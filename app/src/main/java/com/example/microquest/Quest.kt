package com.example.microquest.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Instant


enum class QuestType { TEXT, PHOTO, ACTION, VOICE }


data class Quest(
    val id: Int,
    val text: String,
    val type: QuestType,
    val durationSeconds: Int = 30
)


@Entity(tableName = "completed_quests")
data class CompletedQuest(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val questId: Int,
    val questText: String,
    val questType: String,            // QuestType.name()
    val completedAt: Long = Instant.now().epochSecond,
    val photoUri: String? = null, // only for PHOTO quests
    val userAnswer: String? = null,
    val voiceUri: String? = null,
    val videoUri: String? = null
)


class Converters {
    @TypeConverter fun fromQuestType(value: QuestType): String = value.name
    @TypeConverter fun toQuestType(value: String): QuestType = QuestType.valueOf(value)
}


@Dao
interface CompletedQuestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quest: CompletedQuest): Long

    @Query("SELECT questId FROM completed_quests")
    suspend fun completedIds(): List<Int>

    @Query("SELECT * FROM completed_quests ORDER BY completedAt DESC")
    fun historyFlow(): Flow<List<CompletedQuest>>

    @Query("SELECT COUNT(*) FROM completed_quests")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM completed_quests")
    suspend fun clearAll()
}


@Entity(tableName = "pending_sync")
data class PendingSync(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questId: Int,
    val questText: String,
    val questType: String,
    val completedAt: Long,
    val proofText: String? = null,
    val mediaUrl: String? = null,
    val retryCount: Int = 0
)

@Dao
interface PendingSyncDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingSync): Long

    @Query("SELECT * FROM pending_sync ORDER BY completedAt ASC")
    suspend fun getAll(): List<PendingSync>

    @Query("DELETE FROM pending_sync WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM pending_sync")
    fun countFlow(): Flow<Int>
}


@Database(
    entities = [CompletedQuest::class, PendingSync::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun completedQuestDao(): CompletedQuestDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completed_quests ADD COLUMN userAnswer TEXT")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completed_quests ADD COLUMN voiceUri TEXT")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE completed_quests ADD COLUMN videoUri TEXT")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS pending_sync (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        questId INTEGER NOT NULL,
                        questText TEXT NOT NULL,
                        questType TEXT NOT NULL,
                        completedAt INTEGER NOT NULL,
                        proofText TEXT,
                        mediaUrl TEXT,
                        retryCount INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "micro_quest.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { INSTANCE = it }
            }
    }
}
