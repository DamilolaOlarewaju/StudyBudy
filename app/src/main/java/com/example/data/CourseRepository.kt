package com.example.data

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CourseRepository(private val dao: StudyBuddyDao) {

    val allCourses: Flow<List<StudyCourse>> = dao.getAllCourses()
    val allExamResults: Flow<List<ExamResult>> = dao.getAllExamResults()

    suspend fun getCourseById(courseId: Long): StudyCourse? = withContext(Dispatchers.IO) {
        dao.getCourseById(courseId)
    }

    suspend fun insertCourse(course: StudyCourse): Long = withContext(Dispatchers.IO) {
        dao.insertCourse(course)
    }

    suspend fun deleteCourse(courseId: Long) = withContext(Dispatchers.IO) {
        dao.deleteCourseById(courseId)
        dao.deleteChatsForCourse(courseId)
    }

    fun getChatsForCourse(courseId: Long): Flow<List<ChatEntry>> {
        return dao.getChatsForCourse(courseId)
    }

    suspend fun insertChat(chat: ChatEntry): Long = withContext(Dispatchers.IO) {
        dao.insertChat(chat)
    }

    suspend fun clearChatsForCourse(courseId: Long) = withContext(Dispatchers.IO) {
        dao.deleteChatsForCourse(courseId)
    }

    suspend fun insertExamResult(result: ExamResult): Long = withContext(Dispatchers.IO) {
        dao.insertExamResult(result)
    }

    /**
     * Connects to the Google Gemini API to get a structured reply based on the history,
     * system instructions, and current prompt.
     */
    suspend fun generateContent(
        systemInstruction: String,
        prompt: String,
        chatHistory: List<ChatEntry> = emptyList(),
        responseMimeType: String? = null
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("CourseRepository", "API Key is missing or default placeholder! Running local tutor fallback.")
            return@withContext getLocalFallbackResponse(prompt, chatHistory)
        }

        try {
            // Map the history into GeminiContent format
            val contents = mutableListOf<GeminiContent>()
            
            // Limit history turns to avoid context overflow and extra latency
            val shortHistory = chatHistory.takeLast(10)
            for (turn in shortHistory) {
                contents.add(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = turn.message))
                    )
                )
            }
            
            // Add current prompt
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3f,
                    responseMimeType = responseMimeType
                )
            )

            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text 
                ?: throw Exception("Empty response candidate body from Gemini API.")
        } catch (e: Exception) {
            Log.e("CourseRepository", "Gemini request failed: ${e.message}. Falling back to offline model logic.")
            return@withContext "⚠️ **[Offline Mode - Gemini API not available or key error]** \n\n" + getLocalFallbackResponse(prompt, chatHistory)
        }
    }

    /**
     * Connects to the Google Gemini API to get a structured reply streamed chunk-by-chunk.
     */
    suspend fun generateContentStream(
        systemInstruction: String,
        prompt: String,
        chatHistory: List<ChatEntry> = emptyList(),
        responseMimeType: String? = null,
        onChunkReceived: (String) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("CourseRepository", "API Key is missing or default placeholder! Running local stream tutor fallback.")
            val fullFallback = getLocalFallbackResponse(prompt, chatHistory)
            simulateStreaming(fullFallback, onChunkReceived)
            return@withContext
        }

        try {
            val contents = mutableListOf<GeminiContent>()
            val shortHistory = chatHistory.takeLast(10)
            for (turn in shortHistory) {
                contents.add(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = turn.message))
                    )
                )
            }
            contents.add(
                GeminiContent(
                    parts = listOf(GeminiPart(text = prompt))
                )
            )

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemInstruction))),
                generationConfig = GeminiGenerationConfig(
                    temperature = 0.3f,
                    responseMimeType = responseMimeType
                )
            )

            val responseBody = RetrofitClient.service.generateContentStream(apiKey, request)
            
            // Build a Moshi instance since we need to deserialize individual structures
            val moshi = com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            val adapter = moshi.adapter(GeminiResponse::class.java)

            responseBody.byteStream().bufferedReader().use { reader ->
                var line: String?
                val jsonBuffer = StringBuilder()
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed == "[" || trimmed == "]") continue
                    val cleanLine = if (trimmed.startsWith(",")) trimmed.substring(1).trim() else trimmed
                    if (cleanLine.isEmpty()) continue

                    jsonBuffer.append(cleanLine)
                    var cleanBuffer = jsonBuffer.toString().trim()
                    if (cleanBuffer.startsWith(",")) {
                        cleanBuffer = cleanBuffer.substring(1).trim()
                    }
                    if (cleanBuffer.endsWith(",")) {
                        cleanBuffer = cleanBuffer.substring(0, cleanBuffer.length - 1).trim()
                    }

                    try {
                        val response = adapter.fromJson(cleanBuffer)
                        if (response != null) {
                            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                            if (text != null) {
                                onChunkReceived(text)
                            }
                            jsonBuffer.setLength(0) // clear buffer on success
                        }
                    } catch (e: Exception) {
                        // Keep accumulating JSON lines until a complete JSON block matches
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CourseRepository", "Streaming Gemini request failed: ${e.message}. Falling back to offline model logic.")
            val fullFallback = "⚠️ **[Offline Mode - Gemini API not available or key error]** \n\n" + getLocalFallbackResponse(prompt, chatHistory)
            simulateStreaming(fullFallback, onChunkReceived)
        }
    }

    private suspend fun simulateStreaming(text: String, onChunkReceived: (String) -> Unit) {
        val words = text.split(" ")
        for (i in words.indices) {
            val chunk = words[i] + if (i < words.size - 1) " " else ""
            onChunkReceived(chunk)
            kotlinx.coroutines.delay(15)
        }
    }

    /**
     * Highly specific academic fallback response engine mimicking StudyBuddy's persona and logic.
     */
    private fun getLocalFallbackResponse(prompt: String, chatHistory: List<ChatEntry>): String {
        val lowercase = prompt.lowercase()
        return when {
            lowercase.contains("generate exam") || lowercase.contains("exam sim") || lowercase.contains("simulate") -> {
                """
                [
                  {
                    "question": "In Python, which of the following refers to the correct scoping resolution order (LEGB rule)?",
                    "options": [
                      "Local, External, Global, Benchmark",
                      "Local, Enclosing, Global, Built-in",
                      "Layout, Entity, Generator, Buffer",
                      "Loop, Exception, Guard, Branch"
                    ],
                    "correctAnswerIndex": 1,
                    "explanation": "MIVA exam alert! LEGB stands for Local, Enclosing, Global, and Built-in scopes. This defines the order Python searches block namespaces."
                  },
                  {
                    "question": "Which accounting equation holds true under all business conditions?",
                    "options": [
                      "Assets = Liabilities - Equity",
                      "Assets = Liabilities + Equity",
                      "Equity = Assets + Liabilities",
                      "Liabilities = Assets + Equity"
                    ],
                    "correctAnswerIndex": 1,
                    "explanation": "Double-entry foundation: Assets must balance with Liabilities plus Equity. Memorize this relationship—MIVA loves testing basic double-entry formulations."
                  },
                  {
                    "question": "In SWOT analysis, external parameters are classified into which categories?",
                    "options": [
                      "Strengths and Weaknesses",
                      "Opportunities and Threats",
                      "Strengths and Opportunities",
                      "Weaknesses and Threats"
                    ],
                    "correctAnswerIndex": 1,
                    "explanation": "Correct! SWOT stands for Strengths (Internal), Weaknesses (Internal), Opportunities (External), and Threats (External). Opportunities and Threats are outside your direct control."
                  },
                  {
                    "question": "What is a common trap associated with treating Scrum as a software development framework?",
                    "options": [
                      "Having too much documentation",
                      "Confusing the Daily Standup as a simple status reporting meeting instead of a daily plan alignment",
                      "Skipping coding altogether",
                      "Using Python instead of Java"
                    ],
                    "correctAnswerIndex": 1,
                    "explanation": "Agile Pitfall! Students often think standups are for reporting status to managers, but they are designed to align task directions on team-level items. MIVA exam testers often focus on practical Agile traps!"
                  }
                ]
                """.trimIndent()
            }
            lowercase.contains("teach me") || lowercase.contains("explain") -> {
                val topic = prompt.replace("teach me", "", ignoreCase = true).trim()
                "📚 **Concept Breakdown: $topic**\n\n" +
                "1. **Core Meaning**: Let's keep it brutally simple. This concept defines the fundamental way we organize components to prevent overlapping failures.\n\n" +
                "2. **Simple Analogy**: Think of it like a kitchen with labeled cabinets compared to a giant pile of raw dishes on the counter. Labeled cabinets are structured, categorized, and fast to retrieve.\n\n" +
                "3. **Real-World Hook**: In industrial companies, they isolate chemical processes so a single pipe burst doesn't compromise the entire refinery.\n\n" +
                "🧠 *Quick Check*: Got it? Want me to go deeper or move on to the next step?"
            }
            lowercase.contains("tip") || lowercase.contains("anxiety") || lowercase.contains("mad tips") -> {
                "💡 **StudyBuddy Extreme MIVA Exam Strategy**\n\n" +
                "- **Watch out for Absolute Words**: If you see 'always', 'never', 'only' inside an MCQ option, it is almost certainly a trap. MIVA questions are written to honor complex nuances.\n" +
                "- **Opposite Rule**: If two options look like direct opposites, one is 90% likely to be the correct answer because the professor set them up to test the clear distinction.\n" +
                "- **Definiton Traps**: Read ALL options before tapping. Professors place 'partially correct' statements at Option A to capture hasty students. \n" +
                "- **Strengthening Weak spots**: The night before, don't read new things. Only review core rules and Must-Know Formulas!"
            }
            else -> {
                "📚 **High-Yield Response**\n\n" +
                "Let's focus on what is strictly exam-relevant here. This point relates to the foundation of the syllabus. Remember, MIVA expects you to identify practical applications, not just parrot definitions.\n\n" +
                "Next Step: Ready for a step-by-step breakdown (Teach Me), an Exam Simulation, or some Mad Tips?"
            }
        }
    }
}
