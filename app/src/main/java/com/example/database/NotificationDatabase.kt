package com.example.database

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notification_logs")
data class NotificationLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val message: String,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pin: String,
    val isSent: Boolean, // true if interceptor sent it, false if receiver received it
    val appLabel: String = ""
)

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notification_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NotificationLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NotificationLog)

    @Query("DELETE FROM notification_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM notification_logs WHERE id = :id")
    suspend fun deleteLogById(id: Int)
}

@Database(entities = [NotificationLog::class], version = 2, exportSchema = false)
abstract class AlarmTossDatabase : RoomDatabase() {
    abstract val notificationDao: NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AlarmTossDatabase? = null

        // v1 -> v2: added appLabel column so the in-app Activity list can show the
        // sender device's actual display name (e.g. "Instagram") instead of a
        // hardcoded mapping. Existing rows get an empty string and fall back to
        // the package-name suffix at display time.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_logs ADD COLUMN appLabel TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AlarmTossDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AlarmTossDatabase::class.java,
                    "alarmtoss_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class NotificationRepository(private val dao: NotificationDao) {
    val allLogs: Flow<List<NotificationLog>> = dao.getAllLogs()

    suspend fun insertLog(log: NotificationLog) {
        dao.insertLog(log)
    }

    suspend fun clearLogs() {
        dao.clearAllLogs()
    }

    suspend fun deleteLog(id: Int) {
        dao.deleteLogById(id)
    }
}
