package com.example.pleosaudioguide

import androidx.room.*

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val kind: String = "drive",
    val dismissed: Boolean = false
)

@Entity(tableName = "listening", foreignKeys = [
    ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"])
], indices = [Index("tripId")])
data class ListeningEntity(
    @PrimaryKey val key: String,
    val tripId: String,
    val payload: String
)

@Entity(tableName = "history_meta")
data class HistoryMeta(@PrimaryKey val key: String, val value: String)

@Dao
interface ListeningDao {
    @Query("SELECT * FROM trips WHERE kind = 'drive' AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun activeTrip(): TripEntity?
    @Query("SELECT * FROM trips WHERE kind = 'drive' AND endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    fun latestEndedTrip(): TripEntity?
    @Query("SELECT * FROM trips ORDER BY startedAt")
    fun trips(): List<TripEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertTrip(trip: TripEntity)
    @Update fun updateTrip(trip: TripEntity)
    @Query("SELECT * FROM listening WHERE tripId = :tripId")
    fun records(tripId: String): List<ListeningEntity>
    @Query("SELECT * FROM listening")
    fun allRecords(): List<ListeningEntity>
    @Query("SELECT * FROM listening WHERE \u0060key\u0060 = :key")
    fun record(key: String): ListeningEntity?
    @Upsert fun save(record: ListeningEntity)
    @Query("SELECT value FROM history_meta WHERE \u0060key\u0060 = :key")
    fun meta(key: String): String?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun saveMeta(meta: HistoryMeta)
    @Query("SELECT * FROM trips WHERE id = :id") fun trip(id: String): TripEntity?
    @Query("SELECT * FROM quiz_attempts WHERE `key` = :key") fun quiz(key: String): QuizAttemptEntity?
    @Upsert fun saveQuiz(attempt: QuizAttemptEntity)
    @Query("SELECT * FROM library_completion ORDER BY completedAt DESC, `key`")
    fun libraryEntries(): List<LibraryCompletionEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun insertCompletion(entry: LibraryCompletionEntity)
    @Query("SELECT * FROM quiz_attempts ORDER BY updatedAt DESC") fun allQuizzes(): List<QuizAttemptEntity>
}

@Entity(tableName = "quiz_attempts", foreignKeys = [
    ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"])
], indices = [Index("tripId")])
data class QuizAttemptEntity(
    @PrimaryKey val key: String, val tripId: String, val questionCount: Int,
    val payload: String, val updatedAt: Long
)

/** 동일 콘텐츠/대본/방식의 최초 완료 시각과 귀속을 보존한다. 재청취로 덮어쓰지 않는다. */
@Entity(tableName = "library_completion", indices = [Index("sessionId")])
data class LibraryCompletionEntity(
    @PrimaryKey val key: String,
    val sessionId: String,
    val tripId: String?,
    val completedAt: Long,
    val recordPayload: String,
    val potionName: String,
    val inferred: Boolean
)

@Database(entities = [TripEntity::class, ListeningEntity::class, HistoryMeta::class, QuizAttemptEntity::class, LibraryCompletionEntity::class],
    version = 3, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)])
abstract class ListeningDatabase : RoomDatabase() {
    abstract fun history(): ListeningDao
}
