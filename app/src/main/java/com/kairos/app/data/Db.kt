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
    val calendarEventId: Long? = null
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

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Int)
}

@Database(entities = [Reminder::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ReminderDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "kairos.db"
            ).build().also { instance = it }
        }
    }
}
