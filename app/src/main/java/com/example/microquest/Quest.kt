package com.example.microquest.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.time.Instant

// ─────────────────────────────────────────
//  Enums
// ─────────────────────────────────────────

enum class QuestType { TEXT, PHOTO, ACTION, VOICE }

// ─────────────────────────────────────────
//  Domain model (in-memory, not stored)
// ─────────────────────────────────────────

data class Quest(
    val id: Int,
    val text: String,
    val type: QuestType,
    val durationSeconds: Int = 30
)

// ─────────────────────────────────────────
//  Room Entity — completed quests history
// ─────────────────────────────────────────

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

// ─────────────────────────────────────────
//  Type converters (not needed here, but
//  kept as extension point)
// ─────────────────────────────────────────

class Converters {
    @TypeConverter fun fromQuestType(value: QuestType): String = value.name
    @TypeConverter fun toQuestType(value: String): QuestType = QuestType.valueOf(value)
}

// ─────────────────────────────────────────
//  DAO
// ─────────────────────────────────────────

@Dao
interface CompletedQuestDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quest: CompletedQuest): Long

    /** All completed quest IDs — used to exclude them from random selection. */
    @Query("SELECT questId FROM completed_quests")
    suspend fun completedIds(): List<Int>

    /** Full history, newest first. */
    @Query("SELECT * FROM completed_quests ORDER BY completedAt DESC")
    fun historyFlow(): Flow<List<CompletedQuest>>

    /** Total count — emits on every change. */
    @Query("SELECT COUNT(*) FROM completed_quests")
    fun countFlow(): Flow<Int>

    @Query("DELETE FROM completed_quests")
    suspend fun clearAll()
}

// ─────────────────────────────────────────
//  Database
// ─────────────────────────────────────────

@Database(
    entities = [CompletedQuest::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun completedQuestDao(): CompletedQuestDao

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
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "micro_quest.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build().also { INSTANCE = it }
            }
    }
}
