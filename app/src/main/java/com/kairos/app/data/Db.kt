package com.kairos.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kairos.app.logic.RepeatType
import com.kairos.app.logic.WallClock
import com.kairos.app.system.Sounds
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

/**
 * One reminder. [dueAt] is always the CURRENT (next unacknowledged) occurrence;
 * for repeating reminders it advances when the user confirms "done".
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val notes: String = "",
    val dueAt: Long,
    val repeatType: String = RepeatType.NONE.name,
    val weekdays: Int = 0,
    val anchorDay: Int = 1,
    val sound: String = Sounds.DEFAULT_KEY,
    val done: Boolean = false,
    val doneAt: Long? = null,
    /** Persisted snooze counter of the current occurrence. */
    val snoozeCount: Int = 0,
    val calendarEventId: Long? = null,
    /** When the snoozed alarm rings. While set, it replaces the rest of the chain. */
    val snoozeUntil: Long? = null,
    /**
     * The current occurrence as the user's clock shows it (ISO local date-time, see [WallClock]).
     * [dueAt] follows it when the time zone changes; null only for rows written before version 3.
     */
    val dueLocal: String? = null
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueAt ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Int): Reminder?

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Update
    suspend fun update(reminder: Reminder)

    /** Touches only this column, so a slow calendar sync cannot overwrite a snooze made meanwhile. */
    @Query("UPDATE reminders SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: Int, eventId: Long?)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Int)
}

@Database(entities = [Reminder::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ReminderDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozeUntil INTEGER")
            }
        }

        /** Fills dueLocal with the clock time each reminder shows now, which is what the user picked. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN dueLocal TEXT")
                val zone = ZoneId.systemDefault()
                val rows = ArrayList<Pair<Int, Long>>()
                db.query("SELECT id, dueAt FROM reminders").use { c ->
                    while (c.moveToNext()) rows += c.getInt(0) to c.getLong(1)
                }
                for ((id, dueAt) in rows) {
                    db.execSQL("UPDATE reminders SET dueLocal = ? WHERE id = ?", arrayOf(WallClock.of(dueAt, zone), id))
                }
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kairos.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
