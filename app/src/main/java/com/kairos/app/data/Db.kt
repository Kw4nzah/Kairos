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
import com.kairos.app.system.Sounds
import kotlinx.coroutines.flow.Flow

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
    val snoozeUntil: Long? = null
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

@Database(entities = [Reminder::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ReminderDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozeUntil INTEGER")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kairos.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
