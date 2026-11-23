package com.kairos.app.widget

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.kairos.app.MainActivity
import com.kairos.app.R
import com.kairos.app.data.AppDatabase
import com.kairos.app.data.AppLanguage
import com.kairos.app.data.Prefs
import com.kairos.app.data.Reminder
import com.kairos.app.data.Repo
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.SnoozeMachine
import com.kairos.app.system.Locales
import com.kairos.app.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val ACTION_NEW = "com.kairos.app.NEW_REMINDER"

// Keep in sync with the widget_* colours in values/colors.xml and values-night/colors.xml.
private val BG = ColorProvider(day = Color(0xFFF8F6F1), night = Color(0xFF161210))
private val CARD = ColorProvider(day = Color(0xFFECEAE5), night = Color(0xFF2A221E))
private val LINE = ColorProvider(day = Color(0xFFE2DFD9), night = Color(0xFF2A221E))
private val INK = ColorProvider(day = Color(0xFF181C1B), night = Color(0xFFF2EEE8))
private val MUTED = ColorProvider(day = Color(0xFF5F6967), night = Color(0xFFC4BDB5))
private val ACCENT = ColorProvider(day = Color(0xFF0B6B66), night = Color(0xFFE0959D))
private val ALERT = ColorProvider(day = Color(0xFFB3261E), night = Color(0xFFE59A9A))
private val ON_ACCENT = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF2A0E13))

private val idKey = ActionParameters.Key<Int>("id")
private val dueKey = ActionParameters.Key<Long>("due")
private val countKey = ActionParameters.Key<Int>("count")
private val actionKey = ActionParameters.Key<String>("action")

private const val DAY_MS = 24 * 60 * 60_000L

/** When the reminder rings next: the snooze replaces the original time while it is set. */
private val Reminder.effectiveAt: Long get() = snoozeUntil ?: dueAt

/**
 * Home-screen widget. The top shows the most pressing reminder: the one ringing now (with its snooze
 * buttons), else the snoozed or next one with a live countdown. Below it the following reminders,
 * each with a ring to mark it done; "+" adds one.
 */
class KairosWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dao = AppDatabase.get(context).dao()
        val prefs = Prefs(context)
        provideContent {
            // Collected inside the composition so an update while the session is alive shows fresh data.
            val all by dao.observeAll().collectAsState(initial = emptyList())
            val settings by prefs.settings.collectAsState(initial = null)
            val ctx = widgetContext(context, settings?.language ?: AppLanguage.SYSTEM)
            val now = System.currentTimeMillis()
            val open = all.filter { !it.done }.sortedBy { it.effectiveAt }
            Body(ctx, open.firstOrNull(), open.drop(1).take(30), now)
        }
    }
}

/**
 * The widget's process may be started by an alarm without the per-app language applied to its
 * resources (seen on API 35: an alarm-started refresh came out in the device language), so the
 * language is set explicitly: the system per-app setting first, else the one stored in the app.
 */
private fun widgetContext(context: Context, stored: AppLanguage): Context {
    val system = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales?.toLanguageTags().orEmpty()
    } else ""
    val lang = AppLanguage.fromTags(system).takeIf { it != AppLanguage.SYSTEM } ?: stored
    return Locales.localized(context, lang)
}

@Composable
private fun Body(ctx: Context, hero: Reminder?, rest: List<Reminder>, now: Long) {
    val openApp = actionStartActivity<MainActivity>()
    Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(28.dp).padding(16.dp)) {
        if (hero == null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Image(ImageProvider(R.drawable.widget_logo), null, GlanceModifier.size(40.dp))
                    Spacer(GlanceModifier.width(12.dp))
                    Text(
                        ctx.getString(R.string.widget_empty),
                        GlanceModifier.defaultWeight().clickable(openApp),
                        style = TextStyle(color = MUTED, fontSize = 16.sp)
                    )
                    AddButton(ctx)
                }
            }
            return@Column
        }
        val ringing = hero.effectiveAt <= now
        Hero(ctx, hero, ringing, now, openApp)
        if (ringing) {
            Spacer(GlanceModifier.height(12.dp))
            SnoozeButtons(ctx, hero)
            Spacer(GlanceModifier.height(4.dp))
        } else {
            Spacer(GlanceModifier.height(12.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(LINE)) {}
        }
        if (rest.isNotEmpty()) {
            LazyColumn(GlanceModifier.fillMaxSize()) {
                items(rest, itemId = { it.id.toLong() }) { r -> ListRow(ctx, r, now, openApp) }
            }
        }
    }
}

@Composable
private fun Hero(ctx: Context, r: Reminder, ringing: Boolean, now: Long, openApp: Action) {
    val snoozed = r.snoozeUntil != null && !ringing
    val label = when {
        ringing -> ctx.getString(R.string.widget_now)
        snoozed -> ctx.getString(R.string.widget_snoozed, r.snoozeCount)
        else -> ctx.getString(R.string.widget_next)
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                ImageProvider(if (ringing) R.drawable.widget_logo_alert else R.drawable.widget_logo),
                null, GlanceModifier.size(40.dp)
            )
            Spacer(GlanceModifier.width(10.dp))
            Column {
                Text(label, style = TextStyle(color = if (ringing) ALERT else MUTED, fontSize = 13.sp))
                BigTime(ctx, r.effectiveAt)
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    r.title, maxLines = 1,
                    style = TextStyle(color = INK, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                )
                HeroDetail(ctx, r, ringing, snoozed, now)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        AddButton(ctx)
    }
}

/** The hero time; on a 12-hour clock the AM/PM marker is set small so the title keeps its room. */
@Composable
private fun BigTime(ctx: Context, at: Long) {
    val big = TextStyle(color = INK, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    val dt = Fmt.local(at)
    if (DateFormat.is24HourFormat(ctx)) {
        Text(Fmt.time(ctx, dt), maxLines = 1, style = big)
        return
    }
    val l = Fmt.locale(ctx)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(DateTimeFormatter.ofPattern("h:mm", l).format(dt), maxLines = 1, style = big)
        Text(
            DateTimeFormatter.ofPattern("a", l).format(dt), maxLines = 1,
            modifier = GlanceModifier.padding(start = 3.dp, bottom = 6.dp),
            style = TextStyle(color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        )
    }
}

/** Second line under the title. Countdowns are system-driven Chronometers, so they stay live between updates. */
@Composable
private fun HeroDetail(ctx: Context, r: Reminder, ringing: Boolean, snoozed: Boolean, now: Long) {
    val at = r.effectiveAt
    when {
        ringing -> Chrono(ctx, at, ctx.getString(R.string.widget_ringing_for), alert = true, now = now)
        snoozed || at - now < DAY_MS -> Chrono(ctx, at, ctx.getString(R.string.widget_left), alert = false, now = now)
        else -> Text(dayLabel(ctx, at) ?: "", maxLines = 1, style = TextStyle(color = MUTED, fontSize = 15.sp))
    }
}

@Composable
private fun Chrono(ctx: Context, at: Long, format: String, alert: Boolean, now: Long) {
    val views = RemoteViews(ctx.packageName, if (alert) R.layout.widget_chrono_alert else R.layout.widget_chrono_muted)
    // The base is on the elapsed-realtime clock: the target time moved there.
    views.setChronometer(R.id.widget_chrono, SystemClock.elapsedRealtime() + (at - now), format, true)
    views.setChronometerCountDown(R.id.widget_chrono, at > now)
    AndroidRemoteViews(views)
}

@Composable
private fun AddButton(ctx: Context) {
    val newIntent = Intent(ctx, MainActivity::class.java).setAction(ACTION_NEW)
    Box(
        GlanceModifier.size(44.dp).cornerRadius(22.dp).background(CARD).clickable(actionStartIntent(newIntent))
            .semantics { contentDescription = ctx.getString(R.string.widget_add) },
        contentAlignment = Alignment.Center
    ) { Text("+", style = TextStyle(color = ACCENT, fontSize = 26.sp)) }
}

@Composable
private fun SnoozeButtons(ctx: Context, r: Reminder) {
    val actions = SnoozeMachine.actions(r.snoozeCount)
    Row(GlanceModifier.fillMaxWidth()) {
        actions.forEachIndexed { i, action ->
            if (i > 0) Spacer(GlanceModifier.width(10.dp))
            val primary = i == 0
            val text = ctx.getString(
                when (action) {
                    SnoozeMachine.Action.SNOOZE_5 -> R.string.action_snooze_5
                    SnoozeMachine.Action.SNOOZE_10 ->
                        if (r.snoozeCount >= SnoozeMachine.MAX_SNOOZES) R.string.action_snooze_10_more else R.string.action_snooze_10
                    SnoozeMachine.Action.DONE -> R.string.widget_done
                }
            )
            val params = actionParametersOf(
                idKey to r.id, dueKey to r.dueAt, countKey to r.snoozeCount, actionKey to action.name
            )
            Box(
                GlanceModifier.defaultWeight().height(48.dp).cornerRadius(24.dp)
                    .background(if (primary) ACCENT else CARD)
                    .clickable(actionRunCallback<SnoozeCallback>(params)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text, maxLines = 1,
                    style = TextStyle(
                        color = if (primary) ON_ACCENT else INK, fontSize = 15.sp, fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

@Composable
private fun ListRow(ctx: Context, r: Reminder, now: Long, openApp: Action) {
    val at = r.effectiveAt
    val overdue = at <= now
    val type = RepeatType.parse(r.repeatType)
    val side = dayLabel(ctx, at) ?: if (type != RepeatType.NONE) Fmt.repeatLabel(ctx, type, r.weekdays) else ""
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(GlanceModifier.defaultWeight().clickable(openApp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                Fmt.time(ctx, Fmt.local(at)), maxLines = 1,
                modifier = GlanceModifier.padding(start = 16.dp, end = 14.dp),
                style = TextStyle(color = if (overdue) ALERT else MUTED, fontSize = 15.sp)
            )
            Text(
                r.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(color = INK, fontSize = 16.sp)
            )
            if (side.isNotEmpty()) {
                Text(side, maxLines = 1, modifier = GlanceModifier.padding(start = 8.dp), style = TextStyle(color = MUTED, fontSize = 13.sp))
            }
        }
        Box(
            GlanceModifier.size(44.dp)
                .clickable(actionRunCallback<CompleteCallback>(actionParametersOf(idKey to r.id, dueKey to r.dueAt))),
            contentAlignment = Alignment.Center
        ) { Image(ImageProvider(R.drawable.widget_check), ctx.getString(R.string.widget_mark_done), GlanceModifier.size(26.dp)) }
    }
}

/** "Tomorrow" or "Mon 24 Nov" for another day; null for today. */
private fun dayLabel(ctx: Context, at: Long): String? {
    val d = Fmt.local(at).toLocalDate()
    val today = LocalDate.now()
    return when (d) {
        today -> null
        today.plusDays(1) -> ctx.getString(R.string.day_tomorrow)
        else -> DateTimeFormatter.ofPattern("EEE d MMM", Fmt.locale(ctx)).format(d)
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

/** Snooze / done from the widget; the same guarded path as the notification buttons. */
class SnoozeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[idKey] ?: return
        val action = runCatching { SnoozeMachine.Action.valueOf(parameters[actionKey] ?: return) }.getOrNull() ?: return
        Repo(context).handleAction(id, action, parameters[dueKey], parameters[countKey])
    }
}

object Widget {
    /** Refreshes every placed widget after the data changed. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        CoroutineScope(Dispatchers.IO).launch { runCatching { KairosWidget().updateAll(app) } }
    }
}
