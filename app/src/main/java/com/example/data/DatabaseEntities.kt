package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "study_courses")
data class StudyCourse(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val rawContent: String,
    val keyConceptsJson: String? = null, // key concepts, definitions, formulas, traps
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_entries")
data class ChatEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val role: String, // "user" or "assistant"
    val message: String,
    val mode: String, // "STUDY", "TEACH_ME", "EXAM_SIM", "MAD_TIPS"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "exam_results")
data class ExamResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val courseTitle: String,
    val score: Int,
    val totalQuestions: Int,
    val feedback: String, // general gaps identified by coach
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface StudyBuddyDao {
    @Query("SELECT * FROM study_courses ORDER BY addedTime DESC")
    fun getAllCourses(): Flow<List<StudyCourse>>

    @Query("SELECT * FROM study_courses WHERE id = :courseId LIMIT 1")
    suspend fun getCourseById(courseId: Long): StudyCourse?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: StudyCourse): Long

    @Query("DELETE FROM study_courses WHERE id = :courseId")
    suspend fun deleteCourseById(courseId: Long)

    @Query("SELECT * FROM chat_entries WHERE courseId = :courseId ORDER BY timestamp ASC")
    fun getChatsForCourse(courseId: Long): Flow<List<ChatEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntry): Long

    @Query("DELETE FROM chat_entries WHERE courseId = :courseId")
    suspend fun deleteChatsForCourse(courseId: Long)

    @Query("SELECT * FROM exam_results ORDER BY timestamp DESC")
    fun getAllExamResults(): Flow<List<ExamResult>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamResult(result: ExamResult): Long
}
