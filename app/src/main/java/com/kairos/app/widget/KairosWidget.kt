package com.kairos.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity as actionStartIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.AppDatabase
import com.kairos.app.data.Reminder
import com.kairos.app.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val ACTION_NEW = "com.kairos.app.NEW_REMINDER"

private val BG = ColorProvider(day = Color(0xFFF8F6F1), night = Color(0xFF161210))
private val CARD = ColorProvider(day = Color(0xFFECEAE5), night = Color(0xFF211A17))
private val INK = ColorProvider(day = Color(0xFF181C1B), night = Color(0xFFF2EEE8))
private val MUTED = ColorProvider(day = Color(0xFF5F6967), night = Color(0xFFC4BDB5))
private val ACCENT = ColorProvider(day = Color(0xFF0B6B66), night = Color(0xFFE0959D))
private val OVERDUE = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFE59A9A))
private val ON_ACCENT = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF2A0E13))

private val idKey = ActionParameters.Key<Int>("id")
private val dueKey = ActionParameters.Key<Long>("due")

private data class WidgetRow(val r: Reminder, val label: String, val overdue: Boolean)

/** Home-screen widget: upcoming reminders, tap the circle to complete, "+" to add. */
class KairosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fmtTime = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        val fmtDay = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
        val zone = ZoneId.systemDefault()
        val empty = context.getString(R.string.widget_empty)
        val title = context.getString(R.string.app_name)
        val newIntent = Intent(context, MainActivity::class.java).setAction(ACTION_NEW)
        val dao = AppDatabase.get(context).dao()
        provideContent {
            // Collected inside the composition so an update while the session is alive shows fresh data.
            val all by dao.observeAll().collectAsState(initial = emptyList())
            val now = System.currentTimeMillis()
            val rows = all.filter { !it.done }.sortedBy { it.dueAt }.take(30).map { r ->
                val t = Instant.ofEpochMilli(r.dueAt).atZone(zone)
                WidgetRow(r, if (t.toLocalDate() == LocalDate.now(zone)) t.format(fmtTime) else t.format(fmtDay), r.dueAt < now)
            }
            Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(24.dp).padding(12.dp)) {
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>()),
                        style = TextStyle(color = ACCENT, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    )
                    Box(
                        GlanceModifier.size(32.dp).cornerRadius(16.dp).background(ACCENT)
                            .clickable(actionStartIntent(newIntent)),
                        contentAlignment = Alignment.Center
                    ) { Text("+", style = TextStyle(color = ON_ACCENT, fontSize = 20.sp, fontWeight = FontWeight.Bold)) }
                }
                if (rows.isEmpty()) {
                    Box(
                        GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>()),
                        contentAlignment = Alignment.Center
                    ) { Text(empty, style = TextStyle(color = MUTED, fontSize = 14.sp)) }
                } else {
                    LazyColumn(GlanceModifier.fillMaxSize()) {
                        items(rows, itemId = { it.r.id.toLong() }) { row ->
                            Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    GlanceModifier.size(30.dp).cornerRadius(15.dp).background(CARD)
                                        .clickable(actionRunCallback<CompleteCallback>(actionParametersOf(idKey to row.r.id, dueKey to row.r.dueAt))),
                                    contentAlignment = Alignment.Center
                                ) { Text("○", style = TextStyle(color = ACCENT, fontSize = 18.sp)) }
                                Spacer(GlanceModifier.width(10.dp))
                                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity<MainActivity>())) {
                                    Text(
                                        row.r.title, maxLines = 1,
                                        style = TextStyle(color = INK, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        row.label, maxLines = 1,
                                        style = TextStyle(color = if (row.overdue) OVERDUE else MUTED, fontSize = 12.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class KairosWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KairosWidget()
}

class CompleteCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[idKey] ?: return
        Repo(context).complete(id, parameters[dueKey])
    }
}

object Widget {
    /** Refreshes every placed widget after the data changed. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { KairosWidget().updateAll(app) } }
    }
}
