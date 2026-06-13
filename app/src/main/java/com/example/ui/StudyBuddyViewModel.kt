package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.AnalyticsHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class PaystackInitUiState {
    object Idle : PaystackInitUiState()
    data class Loading(val message: String = "Initializing Checkout Window...") : PaystackInitUiState()
    data class Success(val authorizationUrl: String, val reference: String) : PaystackInitUiState()
    data class Error(val message: String) : PaystackInitUiState()
}

class StudyBuddyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CourseRepository(database.studyBuddyDao())

    val allCourses: StateFlow<List<StudyCourse>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExamResults: StateFlow<List<ExamResult>> = repository.allExamResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Payment and Unlock States
    private val sharedPrefs = application.getSharedPreferences("study_buddy_prefs", android.content.Context.MODE_PRIVATE)
    
    private val _isUnlocked = MutableStateFlow(sharedPrefs.getBoolean("is_unlocked", false))
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _paystackState = MutableStateFlow<PaystackInitUiState>(PaystackInitUiState.Idle)
    val paystackState: StateFlow<PaystackInitUiState> = _paystackState.asStateFlow()

    // Dark Mode state & toggling
    private val _isDarkMode = MutableStateFlow(sharedPrefs.getBoolean("is_dark_mode", false))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("is_dark_mode", enabled).apply()
        _isDarkMode.value = enabled
    }

    // Referral System
    private val _userReferralCode = MutableStateFlow("")
    val userReferralCode: StateFlow<String> = _userReferralCode.asStateFlow()

    private fun generateRandomReferral(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun registerMyReferralCode() {
        val myCode = _userReferralCode.value
        if (myCode.isNotEmpty() && !sharedPrefs.getBoolean("has_registered_referral_on_server", false)) {
            viewModelScope.launch {
                try {
                    PaystackClient.supabaseDbService.registerReferralCode(
                        ReferralDbRow(code = myCode, email = "student@miva.edu.ng")
                    )
                    sharedPrefs.edit().putBoolean("has_registered_referral_on_server", true).apply()
                } catch (e: Exception) {
                    Log.e("StudyBuddyVM", "Failed to register referral code on Supabase REST", e)
                }
            }
        }
    }

    fun checkReferralCodeOnServer(code: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                _isGenerating.value = true
                val results = PaystackClient.supabaseDbService.checkReferralCode(code.trim().uppercase())
                _isGenerating.value = false
                onResult(results.isNotEmpty())
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to verify referral code on Supabase REST", e)
                _isGenerating.value = false
                val fallbackValid = code.trim().length == 6
                onResult(fallbackValid)
            }
        }
    }

    // Unified error / retry layer
    var lastAction: (() -> Unit)? = null
    private val _networkError = MutableStateFlow<String?>(null)
    val networkError: StateFlow<String?> = _networkError.asStateFlow()

    fun clearNetworkError() {
        _networkError.value = null
    }

    fun retryLastAction() {
        val action = lastAction
        _networkError.value = null
        if (action != null) {
            action()
        }
    }

    // UI States
    private val _selectedCourse = MutableStateFlow<StudyCourse?>(null)
    val selectedCourse: StateFlow<StudyCourse?> = _selectedCourse.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingChatText = MutableStateFlow<String?>(null)
    val streamingChatText: StateFlow<String?> = _streamingChatText.asStateFlow()

    private val _currentMode = MutableStateFlow("STUDY") // "STUDY", "TEACH_ME", "EXAM_SIM", "MAD_TIPS"
    val currentMode: StateFlow<String> = _currentMode.asStateFlow()

    // Chats inside Teach Me or Study Mode
    val activeChats: StateFlow<List<ChatEntry>> = _selectedCourse
        .flatMapLatest { course ->
            if (course != null) {
                repository.getChatsForCourse(course.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // State for interactive exam simulator
    private val _examQuestions = MutableStateFlow<List<Question>>(emptyList())
    val examQuestions: StateFlow<List<Question>> = _examQuestions.asStateFlow()

    private val _currentExamQuestionIndex = MutableStateFlow(0)
    val currentExamQuestionIndex: StateFlow<Int> = _currentExamQuestionIndex.asStateFlow()

    // Maps question index to selected option index
    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers.asStateFlow()

    private val _isExamAnswerConfirmed = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val isExamAnswerConfirmed: StateFlow<Map<Int, Boolean>> = _isExamAnswerConfirmed.asStateFlow()

    private val _examCompleted = MutableStateFlow(false)
    val examCompleted: StateFlow<Boolean> = _examCompleted.asStateFlow()

    private val _weakSpotsFeedback = MutableStateFlow("")
    val weakSpotsFeedback: StateFlow<String> = _weakSpotsFeedback.asStateFlow()

    // Moshi Instance for parsing Gemini JSON responses
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    init {
        // Handle unique referral code generation on first launch
        val storedCode = sharedPrefs.getString("user_referral_code", null)
        if (storedCode == null) {
            val newCode = generateRandomReferral()
            sharedPrefs.edit().putString("user_referral_code", newCode).apply()
            _userReferralCode.value = newCode
        } else {
            _userReferralCode.value = storedCode
        }
        registerMyReferralCode()

        // Pre-populate some standard courses if there are none in the DB so that the user 
        // gets an immediate professional experience without having to paste text
        viewModelScope.launch {
            repository.allCourses.first().let { currentList ->
                if (currentList.isEmpty()) {
                    createPrepopulatedCourses()
                } else {
                    _selectedCourse.value = currentList.firstOrNull()
                }
            }
        }
    }

    private suspend fun createPrepopulatedCourses() {
        val course1 = StudyCourse(
            title = "📚 CSC 201: Py Programming Foundations",
            rawContent = """
                Python variables, control structures (if-else, loops), functions, lambda expressions, list comprehensions, classes, and object-oriented concepts.
                
                Core Syllabus items:
                1. Scoping - Python searches variables in LEGB order: Local, Enclosing, Global, and Builtins.
                2. Mutable default arguments - Using def append_to(element, target=[]): is dangerous. The target list is shared across calls.
                3. OOP structure - Classes support double underscore dunder methods like __init__, __str__, __repr__ to define initial states and string layouts.
            """.trimIndent(),
            keyConceptsJson = """
                ### 💡 Key Concepts
                - **Scope Hierarchy (LEGB)**: The systematic resolution order Python uses to resolve names. Local variable definitions override Enclosing, Global, and Built-in definitions.
                - **Mutable Default Arguments**: A classic memory-leak pitfall where default argument values are constructed once at class declaration time and persist across sequential instances.

                ### ⚖️ Must-Know Formulas & Rules
                - **Rule of List Comprehension**: Syntax structured as `[expression for item in iterable if condition]`. Used to perform quick filtering and mapping operations clean on one statement stack.
                - **The LEGB Resolution Sequence**: Always starts from the innermost local execution context and traverses outward to Built-in.

                ### 🎯 Common Exam Traps
                - **The Shared Mutable Object Trap**: Watch out on MIVA exams for code checking default target arguments. If an array is initialized to a default blank `[]` in the parameter block, any inserts accumulate across unrelated function runs!
            """.trimIndent()
        )

        val course2 = StudyCourse(
            title = "💼 BUS 101: Introduction to Business",
            rawContent = """
                Foundational Principles of Administrative Business:
                1. Planning, Organizing, Directing, Controlling (the classical POSDC sequence).
                2. Strategic planning frameworks focusing on internal factors (Strengths, Weaknesses) and external forces (Opportunities, Threats).
                3. Distinction between organizational capability scopes: Efficiency (doing things right with minimal resource waste) versus Effectiveness (doing the right strategic things to meet end goals).
            """.trimIndent(),
            keyConceptsJson = """
                ### 💡 Key Concepts
                - **Organizational POSDC Framework**: The foundational pillar of administration. Planning (setting targets), Organizing (structuring workflows), Staffing (roles assignment), Directing (guiding operations), and Controlling (performance evaluation).
                - **SWOT Strategic Analysis Matrix**: Categorizes strategic elements into Internal dynamics (Strengths & Weaknesses) and External trends (Opportunities & Threats).

                ### ⚖️ Must-Know Rules
                - **The Core Management Divide**: Distinction between *Efficiency* (minimizing resources used per output) and *Effectiveness* (matching final results successfully with ultimate strategic goals).

                ### 🎯 Common Exam Traps
                - **Environmental Classification Trap**: Watch out! MIVA examiners love trying to trick students by presenting external factors (e.g. dynamic competitor bankruptcies or government regulatory relief) and asking if these are Strengths (Internal). They are strictly Opportunities or Threats (External)!
            """.trimIndent()
        )

        val course3 = StudyCourse(
            title = "📊 ACC 201: Principles of Accounting",
            rawContent = """
                Introduction to accounting concepts, double-entry bookkeeping, the accounting equation, financial statements, and ledger transactions.
                
                Core Syllabus items:
                1. The Accounting Equation: Assets = Liabilities + Owners' Equity.
                2. Debit/Credit Rules: Assets increase with Debits. Liabilities increase with Credits.
                3. Financial Statements: Balance Sheet (position), Income Statement (performance), and Cash Flow Statement.
            """.trimIndent(),
            keyConceptsJson = """
                ### 💡 Key Concepts
                - **Accounting Equation Rule**: Assets = Liabilities + Equity. Every single transaction must keep this basic ledger scale perfectly balanced!
                - **The Double Entry Principle**: For every financial transaction, there is a Debit entry in one account and a corresponding Credit entry in another.

                ### ⚖️ Must-Know Formulas & Rules
                - **Balance Sheet Equality**: Dynamic equation where total Assets MUST match total Liabilities + Owners' Equity on day-end logs.

                ### 🎯 Common Exam Traps
                - **The Debit/Credit Swap Trap**: Watch out! Exams will try to ask if an increase in a liability account is represented as a Debit. Remember: Increases in Liabilities and Equity are ALWAYS recorded as Credits!
            """.trimIndent()
        )

        val course4 = StudyCourse(
            title = "📈 ECO 101: Principles of Economics",
            rawContent = """
                Core economic theories, supply and demand, elasticities, consumer behaviors, market demand, inflation, GDP and monetary policies.
                
                Syllabus Items:
                1. Law of Demand: Quantity demanded falls as price rises, other things being equal.
                2. Market Equilibrium: Point where market supply matches market demand.
                3. GDP calculation: Consumption + Investment + Government purchases + Net Exports.
            """.trimIndent(),
            keyConceptsJson = """
                ### 💡 Key Concepts
                - **The Law of Demand**: Higher prices decrease the quantity demanded relative to steady supply curves.
                - **Market Price Clearing**: Equilibrium price exists where quantity supplied matches consumer demand curves.

                ### ⚖️ Must-Know Formulas & Rules
                - **GDP Aggregate Expenditure**: `GDP = C + I + G + (X - M)`. Adds private consumption, investments, government spend, and net exports.

                ### 🎯 Common Exam Traps
                - **The Elasticity Coefficient Sign Trap**: Note that or price elasticity of demand is technically negative due to the inverse demand law, but economics exams usually quote it in positive absolute terms!
            """.trimIndent()
        )

        val course5 = StudyCourse(
            title = "📐 MTH 101: Elementary Mathematics",
            rawContent = """
                Fundamental algebraic equations, limits, differential calculus, matrices, indices, logarithms, and set theory.
                
                Syllabus items:
                1. Logarithm Rules: log(ab) = log(a) + log(b), log(a/b) = log(a) - log(b).
                2. Set Theory: Union, Intersection, and Complement sets of Venn diagrams.
                3. Power Rule of Derivatives: d/dx(x^n) = n * x^(n-1).
            """.trimIndent(),
            keyConceptsJson = """
                ### 💡 Key Concepts
                - **Calculus Power Rule**: Basic differentiation technique where `d/dx(x^n) = n * x^(n-1)`.
                - **Set Intersection Laws**: Intersection represents all elements shared in BOTH target sets simultaneously (`A ∩ B`).

                ### ⚖️ Must-Know Formulas & Rules
                - **Logarithmic Product Expansion**: `log(AB) = log(A) + log(B)`. Expanding products into clear logarithmic sums.

                ### 🎯 Common Exam Traps
                - **The Constant Derivative Trap**: MIVA math tests will try to trick you by asking for the derivative of a constant term (like `d/dx(120)`). Remember: The derivative of any constant value is ALWAYS zero!
            """.trimIndent()
        )

        val id1 = repository.insertCourse(course1)
        val id2 = repository.insertCourse(course2)
        val id3 = repository.insertCourse(course3)
        val id4 = repository.insertCourse(course4)
        val id5 = repository.insertCourse(course5)
        _selectedCourse.value = repository.getCourseById(id1)
    }

    fun selectCourse(course: StudyCourse) {
        _selectedCourse.value = course
        // Clear active exam configs
        _examQuestions.value = emptyList()
        _currentExamQuestionIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _isExamAnswerConfirmed.value = emptyMap()
        _examCompleted.value = false
        _weakSpotsFeedback.value = ""
    }

    fun setMode(mode: String) {
        _currentMode.value = mode
        if (mode == "EXAM_SIM" && _examQuestions.value.isEmpty()) {
            generateSimulatorExam()
        }
    }

    fun addNewCourse(title: String, content: String) {
        if (title.isBlank() || content.isBlank()) return
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                // Truncate input for speed and RAM safety on low-end devices
                val summarizedContent = if (content.length > 1000) {
                    content.take(1000) + "\n\n... [Syllabus notes truncated for fast response] ..."
                } else {
                    content
                }
                // Instantly generate key concepts summary using study mode instructions (ultra-short format)
                val systemInstruction = "You are StudyBuddy, an elite exam tutor for MIVA. Provide a brutally concise, high-yield exam sheet under 150 words. Do not ramble."
                val prompt = """
                    Review the following syllabus notes.
                    Immediately compile an ultra-concise, high-yield cheat sheet with exactly these sections neatly in simple Markdown notation:
                    
                    ### 💡 Key Concepts
                    (list top 2 structural concepts with analogies)
                    
                    ### ⚖️ Must-Know Formulas & Rules
                    (provide 1-2 crucial formulas or administrative rules)
                    
                    ### 🎯 Common Exam Traps
                    (identify 1 main trap commonly used to confuse students)
                    
                    Here is the raw material:
                    $summarizedContent
                """.trimIndent()

                val response = repository.generateContent(systemInstruction, prompt)
                
                val newCourse = StudyCourse(
                    title = title,
                    rawContent = content,
                    keyConceptsJson = response
                )
                val id = repository.insertCourse(newCourse)
                val saved = repository.getCourseById(id)
                if (saved != null) {
                    _selectedCourse.value = saved
                    _currentMode.value = "STUDY"
                    // reset exam
                    _examQuestions.value = emptyList()
                }
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to add new course: ${e.message}")
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun deleteCurrentCourse() {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            repository.deleteCourse(course.id)
            val left = repository.allCourses.first()
            if (left.isNotEmpty()) {
                _selectedCourse.value = left.first()
            } else {
                _selectedCourse.value = null
            }
        }
    }

    // --- TEACH ME MODE ACTION ---
    fun sendTeachReply(message: String, pdfTitle: String? = null, pdfContent: String? = null) {
        val course = _selectedCourse.value ?: return
        if (message.isBlank() && pdfContent.isNullOrBlank()) return

        viewModelScope.launch {
            _isGenerating.value = true
            _streamingChatText.value = ""
            try {
                // Load existing chat log
                val currentChats = activeChats.value
                
                // Formulate a clean, lightweight UI chat display text
                val dbUserMessage = if (pdfTitle != null) {
                    val optionalNote = if (message.isNotBlank()) "\n\n**Note / Question:** $message" else ""
                    "📎 **Attached Material:** $pdfTitle$optionalNote"
                } else {
                    message
                }
                
                // Save user's question or confirmation to DB
                repository.insertChat(
                    ChatEntry(
                        courseId = course.id,
                        role = "user",
                        message = dbUserMessage,
                        mode = "TEACH_ME"
                    )
                )

                val systemInstruction = """
                    You are StudyBuddy, an elite tutor. 
                    Explain this topic step-by-step using the Feynman Technique. Only 1 concept per turn.
                    Be brutally direct, clear, and practical. Talk in brief sentences. Max 80 words.
                    Always end your explanation by pausing and asking: "Got it? Go deeper or move on?"
                """.trimIndent()

                val summarizedBaseContent = if (course.rawContent.length > 800) {
                    course.rawContent.take(800) + "\n\n... [truncated for speed] ..."
                } else {
                    course.rawContent
                }

                val summarizedAttachedContent = if (pdfContent != null && pdfContent.length > 800) {
                    pdfContent.take(800) + "\n\n... [truncated for speed] ..."
                } else {
                    pdfContent
                }

                val attachedMaterialPrompt = if (summarizedAttachedContent != null) {
                    """
                    Additional course material:
                    $summarizedAttachedContent
                    """.trimIndent()
                } else {
                    ""
                }

                val prompt = """
                    Background syllabus info:
                    $summarizedBaseContent

                    $attachedMaterialPrompt

                    Student in Teach Me mode. 
                    Student response: "$dbUserMessage"
                    Provide the next concise, high-yield explanation chunk under 80 words, then ask if they want to go deeper or move on.
                """.trimIndent()

                val updatedHistory = currentChats + ChatEntry(courseId = course.id, role = "user", message = dbUserMessage, mode = "TEACH_ME")
                
                var accumulatedText = ""
                repository.generateContentStream(systemInstruction, prompt, updatedHistory) { chunk ->
                    accumulatedText += chunk
                    _streamingChatText.value = accumulatedText
                }

                repository.insertChat(
                    ChatEntry(
                        courseId = course.id,
                        role = "assistant",
                        message = accumulatedText,
                        mode = "TEACH_ME"
                    )
                )
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to process Teach Me turn: ${e.message}")
            } finally {
                _streamingChatText.value = null
                _isGenerating.value = false
            }
        }
    }

    fun startTeachMeSession() {
        val course = _selectedCourse.value ?: return
        viewModelScope.launch {
            _isGenerating.value = true
            _streamingChatText.value = ""
            try {
                repository.clearChatsForCourse(course.id)
                val systemInstruction = "You are StudyBuddy, an elite tutor. Break down syllabus content using the Feynman Technique. Be brutally direct, clear, and extremely brief (under 80 words)."
                val summarizedBaseContent = if (course.rawContent.length > 800) {
                    course.rawContent.take(800) + "\n\n... [truncated for speed] ..."
                } else {
                    course.rawContent
                }
                val prompt = """
                    Here is the syllabus material outline:
                    $summarizedBaseContent

                    Initiate 'Teach Me Mode'. 
                    Give a sharp, high-yield explanation of the FIRST core concept in this syllabus. 
                    Use an awesome brief analogy or memory hook under 80 words total. 
                    End strictly by looking for confirmation: "Got it? Want to go deeper or move on?"
                """.trimIndent()

                var accumulatedText = ""
                repository.generateContentStream(systemInstruction, prompt) { chunk ->
                    accumulatedText += chunk
                    _streamingChatText.value = accumulatedText
                }
                
                repository.insertChat(
                    ChatEntry(
                        courseId = course.id,
                        role = "assistant",
                        message = accumulatedText,
                        mode = "TEACH_ME"
                    )
                )
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to start Teach Me session: ${e.message}")
            } finally {
                _streamingChatText.value = null
                _isGenerating.value = false
            }
        }
    }

    // --- EXAM SIMULATOR ACTION ---
    fun generateSimulatorExam() {
        val course = _selectedCourse.value ?: return
        lastAction = { generateSimulatorExam() }
        
        // Log simulator exam started event
        val eventParams = android.os.Bundle().apply {
            putString("course_id", course.id.toString())
            putString("course_title", course.title)
        }
        AnalyticsHelper.logEvent("exam_started", eventParams)

        _isGenerating.value = true
        _examCompleted.value = false
        _currentExamQuestionIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _isExamAnswerConfirmed.value = emptyMap()

        viewModelScope.launch {
            try {
                val systemInstruction = "You are StudyBuddy, an exam crafter. Compile highly realistic multiple choice questions in rigid JSON format. Keep questions and explanations very short (under 20 words each)."
                val summarizedBaseContent = if (course.rawContent.length > 800) {
                    course.rawContent.take(800) + "\n\n... [truncated for speed] ..."
                } else {
                    course.rawContent
                }
                val prompt = """
                    Generate an exam mockup based on the following material.
                    Provide exactly 3 high-yield, realistic multiple choice questions. Keep options and explanations extremely short.
                    You MUST respond with a JSON array ONLY. Do not include any conversational boundaries.
                    Ensure each entry matches this JSON schema with identical keys:
                    {
                      "question": "Realistic brief question...",
                      "options": ["Opt A", "Opt B", "Opt C", "Opt D"],
                      "correctAnswerIndex": 0,
                      "explanation": "Ultra-short MIVA core alignment explaining correction."
                    }

                    Here is the raw study material:
                    $summarizedBaseContent
                """.trimIndent()

                val response = repository.generateContent(
                    systemInstruction = systemInstruction,
                    prompt = prompt,
                    responseMimeType = "application/json"
                )

                val cleanedResponse = cleanJson(response)
                
                try {
                    val listType = Types.newParameterizedType(List::class.java, Question::class.java)
                    val jsonAdapter = moshi.adapter<List<Question>>(listType)
                    val questions = jsonAdapter.fromJson(cleanedResponse)
                    if (!questions.isNullOrEmpty()) {
                        _examQuestions.value = expandToExactly60Questions(questions, course.rawContent, course.title)
                    } else {
                        throw Exception("Moshi returned empty parsed list")
                    }
                } catch (pe: Exception) {
                    Log.e("StudyBuddyVM", "Moshi json parsing failed. Falling back.", pe)
                    _examQuestions.value = expandToExactly60Questions(getLocalFallbackQuestions(), course.rawContent, course.title)
                }
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to compile MIVA Simulator Exam: ${e.message}")
                _networkError.value = "Connection issue — check your internet and tap to retry"
                _examQuestions.value = expandToExactly60Questions(getLocalFallbackQuestions(), course.rawContent, course.title)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun expandToExactly60Questions(
        baseQuestions: List<Question>,
        rawContent: String,
        courseTitle: String
    ): List<Question> {
        val finalQuestions = baseQuestions.toMutableList()
        if (finalQuestions.isEmpty()) {
            finalQuestions.addAll(getLocalFallbackQuestions())
        }

        // 1. Compile some key terms for procedural questions
        val keyTerms = mutableListOf<String>()
        val stops = setOf("the", "and", "for", "with", "from", "that", "this", "these", "those", "their", "there", "about", "notes", "syllabus", "course", "concepts", "must", "know", "common", "exam", "traps", "rules")
        
        courseTitle.split(Regex("[^a-zA-Z0-9]")).forEach { word ->
            val cleanWord = word.trim()
            if (cleanWord.length > 3 && !stops.contains(cleanWord.lowercase())) {
                keyTerms.add(cleanWord)
            }
        }
        rawContent.split(Regex("[^a-zA-Z0-9]")).forEach { word ->
            val cleanWord = word.trim()
            if (cleanWord.length > 4 && cleanWord.any { it.isUpperCase() } && !stops.contains(cleanWord.lowercase())) {
                keyTerms.add(cleanWord)
            }
        }
        val terms = keyTerms.distinct().filter { it.isNotBlank() }.take(15)

        // 2. Mix in high quality Active Recall & Exam Prep strategy questions
        val studyTricksPool = listOf(
            Question(
                question = "What is the primary cognitive philosophy behind the Feynman Technique?",
                options = listOf(
                    "Translating complexity into plain explanation to uncover logical gap areas",
                    "Memorizing complete definitions word-for-word prior to standard exams",
                    "Practicing manual speed-writing exercises under intense physical pressure",
                    "Reviewing class syllabus items in quick 5-second rhythmic flashes"
                ),
                correctAnswerIndex = 0,
                explanation = "The Feynman Technique forces the brain to clarify definitions by teaching them simply, exposing gaps instantly."
            ),
            Question(
                question = "How does 'Active Recall' directly build long-term memory?",
                options = listOf(
                    "It forces the brain to retrieve information from neural pathways, strengthening memory traces",
                    "It is a subset of passive highlighting which eases visual scanning processes",
                    "It acts as a sleep-cycle regulator raising delta waves prior to tests",
                    "It prevents any forgetting by permanently locking memory in physical brain cells"
                ),
                correctAnswerIndex = 0,
                explanation = "By retrieving information actively rather than looking at notes, you reinforce structural recall mechanisms."
            ),
            Question(
                question = "In standard multiple-choice exams, what is the best strategy when you are stuck between two close options?",
                options = listOf(
                    "Examine which option contains absolute qualifiers like 'always' or 'never', which are usually distractors",
                    "Pick the option with the shortest text length, as professors prefer compact solutions",
                    "Select the option that sounds most complex, to impress the automated grading engines",
                    "Choose a random answer instantly without waste, avoiding any cognitive load"
                ),
                correctAnswerIndex = 0,
                explanation = "Absolute qualifiers like 'always', 'never', or 'completely' often render options incorrect due to lack of exceptions."
            ),
            Question(
                question = "According to the Spacing Effect, how should final revision sessions be scheduled?",
                options = listOf(
                    "Distributed in spaced sessions across several days to allow consolidations",
                    "Packed into one continuous 12-hour session the night before the exam",
                    "Repeated over and over in identical patterns on the morning of testing",
                    "Ignored completely until entering the testing center to protect focus"
                ),
                correctAnswerIndex = 0,
                explanation = "The Spacing Effect proves that distributing learning sessions over time yields vastly superior retention."
            ),
            Question(
                question = "What does the first step of the Feynman Technique require you to do?",
                options = listOf(
                    "Write the name of a concept at the top of a blank page and explain it as if teaching a child",
                    "Consult five separate foreign textbooks to compile an index of complex words",
                    "Hire a professional tutor to explain the concept in detail",
                    "Recite the definitions in a loud voice until memorized"
                ),
                correctAnswerIndex = 0,
                explanation = "The Feynman Technique starts by framing the target concept and writing a clear, basic, accessible explanation."
            ),
            Question(
                question = "Why does teaching a concept to someone else help you understand it?",
                options = listOf(
                    "It forces you to simplify language and confront areas where your own explanation breaks down",
                    "It increases social standing and establishes academic dominance on campus",
                    "It automatically guarantees full marks on any subsequent group project files",
                    "It signals the brain to release stress hormones, which aids retention"
                ),
                correctAnswerIndex = 0,
                explanation = "Teaching forces you to synthesize and bridge logical leaps in your own mental models."
            ),
            Question(
                question = "When studying challenging MIVA-aligned materials, which methodology ensures deep understanding?",
                options = listOf(
                    "Explaining the concepts out loud without looking at notes, then filling notes gaps",
                    "Rereading the same page ten times without pausing to evaluate",
                    "Copying the text word-for-word onto several colored flashcards",
                    "Listening to white noise while silently copying diagrams"
                ),
                correctAnswerIndex = 0,
                explanation = "Active retrieval paired with gap-filling (the core of study sessions) is proven to yield direct comprehension."
            )
        )

        for (stQ in studyTricksPool) {
            if (finalQuestions.size < 60) {
                finalQuestions.add(stQ)
            }
        }

        // 3. Programmatically generate procedural subject-oriented questions from keywords
        if (terms.isNotEmpty()) {
            for (i in 0 until 30) {
                if (finalQuestions.size >= 60) break
                val t1 = terms[i % terms.size]
                val t2 = terms[(i + 1) % terms.size]
                val q = when (i % 3) {
                    0 -> Question(
                        question = "In the context of standard university examinations, which statement best defines the direct action or purpose of '$t1'?",
                        options = listOf(
                            "It acts as a critical guiding paradigm that standardizes core operational outputs",
                            "It is a secondary variable that can be safely ignored after initialization",
                            "It represents a common external threat that MIVA managers filter out",
                            "It is a deprecated design pattern that is no longer supported"
                        ),
                        correctAnswerIndex = 0,
                        explanation = "In academic evaluations, '$t1' represents an essential structural concept discussed extensively in this course syllabus."
                    )
                    1 -> Question(
                        question = "How should we evaluate the direct academic relationship between the concept of '$t1' and '$t2'?",
                        options = listOf(
                            "They function as synergistic mechanisms that together raise performance quality and comprehension",
                            "They are completely mutually exclusive and can never be integrated on the same branch",
                            "They are identical concepts that can be utilized interchangeably without any category change",
                            "They are external constraints that only apply to advanced physical engineering systems"
                        ),
                        correctAnswerIndex = 0,
                        explanation = "Both '$t1' and '$t2' represent key strategic facets that work in synergy to resolve objectives."
                    )
                    else -> Question(
                        question = "What is a major exam-day trap or common mistake regarding the misclassification of '$t1'?",
                        options = listOf(
                            "Confusing its internal properties and bounds with external or unrelated variables",
                            "Failing to capitalized its first letter during standard written essays",
                            "Assuming it operates under a static single-threaded execution scope",
                            "Applying its rules only to junior or beginner environments"
                        ),
                        correctAnswerIndex = 0,
                        explanation = "A common test trap is misclassifying '$t1' properties, leading to incorrect strategic decisions."
                    )
                }
                finalQuestions.add(q)
            }
        }

        // 4. Fill remaining slots to make EXACTLY 60 questions using randomized variations
        var varOffset = 0
        val originalBaseList = finalQuestions.toList() // snapshot of current questions
        while (finalQuestions.size < 60 && originalBaseList.isNotEmpty()) {
            val baseQ = originalBaseList[varOffset % originalBaseList.size]
            val correctText = baseQ.options.getOrElse(baseQ.correctAnswerIndex) { baseQ.options.first() }
            val shuffledOptions = baseQ.options.shuffled()
            val newCorrectIndex = shuffledOptions.indexOf(correctText)
            
            val prefixes = listOf(
                "Based on the course syllabus: ",
                "In a clinical or administrative evaluation: ",
                "Which is the most appropriate response for: ",
                "Consider the professional implications: ",
                "Under MIVA's academic framework: "
            )
            val prefix = prefixes.getOrElse(varOffset / originalBaseList.size) { "Recalling target definitions: " }
            val newText = if (baseQ.question.contains("?")) {
                prefix + baseQ.question.lowercase().replaceFirstChar { it.lowercase() }
            } else {
                "$prefix ${baseQ.question}"
            }
            
            finalQuestions.add(
                Question(
                    question = newText,
                    options = shuffledOptions,
                    correctAnswerIndex = if (newCorrectIndex != -1) newCorrectIndex else baseQ.correctAnswerIndex,
                    explanation = baseQ.explanation
                )
            )
            varOffset++
        }

        return finalQuestions.take(60)
    }

    private fun getLocalFallbackQuestions(): List<Question> {
        val course = _selectedCourse.value
        if (course != null && course.title.contains("Py Programming")) {
            return listOf(
                Question(
                    question = "Why is specifying target=[] as a default argument in Python functions considered a critical trap?",
                    options = listOf(
                        "It will cause Python to raise a SyntaxError at compile time",
                        "The default parameter is evaluated once at definition, sharing the mutable list across consecutive calls",
                        "It renders the function completely private, blocking standard caller execution",
                        "It automatically forces the interpreter to use standard multi-threading locks"
                    ),
                    correctAnswerIndex = 1,
                    explanation = "Python evaluates default parameters exactly once. Because of this, mutating standard default lists accumulates insertions across independent invocations!"
                ),
                Question(
                    question = "What is the order in which Python resolves variables under the LEGB rule?",
                    options = listOf(
                        "Loop, External, Global, Build",
                        "Local, Enclosing, Global, Built-in",
                        "Logic, Entry, General, Binary",
                        "Local, Enterprise, Group, Base"
                    ),
                    correctAnswerIndex = 1,
                    explanation = "LEGB represents Local, Enclosing (nested functions), Global (module level), and Built-in scopes. This is the search hierarchy Python follows strictly!"
                )
            )
        }
        
        // General default MIVA mock questions
        return listOf(
            Question(
                question = "Under MIVA Strategic POSDC definitions, what is the precise divide between Efficiency and Effectiveness?",
                options = listOf(
                    "Efficiency is about resource inputs; Effectiveness is about executing the right strategic targets",
                    "Efficiency is for junior staff; Effectiveness is only for executive VP offices",
                    "They are identical terms and can be interchanged on the exam",
                    "Efficiency focuses on external trends; Effectiveness handles internal assets"
                ),
                correctAnswerIndex = 0,
                explanation = "Efficiency is 'doing things right' (minimizing costs/waste). Effectiveness is 'doing the right things' (completing goals)."
            ),
            Question(
                question = "When conducting a SWOT strategic evaluation, how should external market opportunities be classified?",
                options = listOf(
                    "As an internal Strengths factor, since our team handles standard executions",
                    "Strictly as an external Opportunities factor, as we do not own external economic forces",
                    "As an internal Weakness, if we do not currently exploit them fully",
                    "As a static Threats indicator, unless they are already profitable"
                ),
                correctAnswerIndex = 1,
                explanation = "MIVA trap alert! Opportunities are external trends we do not control. Do not misclassify external potentials as internal strengths!"
            )
        )
    }

    fun selectExamAnswer(questionIndex: Int, optionIndex: Int) {
        val current = _selectedAnswers.value.toMutableMap()
        current[questionIndex] = optionIndex
        _selectedAnswers.value = current
    }

    fun confirmExamAnswer(questionIndex: Int) {
        val current = _isExamAnswerConfirmed.value.toMutableMap()
        current[questionIndex] = true
        _isExamAnswerConfirmed.value = current
    }

    fun submitExam() {
        val course = _selectedCourse.value ?: return
        val questions = _examQuestions.value
        if (questions.isEmpty()) return

        var correctCount = 0
        val answers = _selectedAnswers.value
        
        for ((idx, ques) in questions.withIndex()) {
            val reply = answers[idx]
            if (reply == ques.correctAnswerIndex) {
                correctCount++
            }
        }

        _examCompleted.value = true

        viewModelScope.launch {
            _isGenerating.value = true
            try {
                // Generate deep coaching feedback from Gemini based on score
                val systemInstruction = "You are StudyBuddy, an elite exam coach. Give a brutally honest, extreme motivation appraisal under 50 words total."
                val prompt = """
                    Analyze performance:
                    Course: ${course.title}
                    Score: $correctCount / ${questions.size}
                    
                    Give 1 core weak spot and exactly 1 high-yield advice block stating what they should re-read under 50 words total.
                """.trimIndent()

                val feedback = repository.generateContent(systemInstruction, prompt)
                _weakSpotsFeedback.value = feedback

                // Insert into historic DB
                repository.insertExamResult(
                    ExamResult(
                        courseId = course.id,
                        courseTitle = course.title,
                        score = correctCount,
                        totalQuestions = questions.size,
                        feedback = feedback
                    )
                )
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to retrieve feedback analysis: ${e.message}")
                _weakSpotsFeedback.value = "Review the core concepts of ${course.title}. Focus on clarifying definitions and identifying tricky opposite options."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun nextExamQuestion() {
        if (_currentExamQuestionIndex.value < _examQuestions.value.size - 1) {
            _currentExamQuestionIndex.value += 1
        }
    }

    fun prevExamQuestion() {
        if (_currentExamQuestionIndex.value > 0) {
            _currentExamQuestionIndex.value -= 1
        }
    }

    // --- MAD TIPS MODE ACTION ---
    private val _madTipsState = MutableStateFlow<String>("")
    val madTipsState: StateFlow<String> = _madTipsState.asStateFlow()

    fun fetchMadTips() {
        val course = _selectedCourse.value ?: return
        lastAction = { fetchMadTips() }
        _isGenerating.value = true
        viewModelScope.launch {
            try {
                val systemInstruction = "You are StudyBuddy, a brutal and brilliant exam coach. Be extremely direct and compile high-yield tips under 60 words total."
                val prompt = """
                    Provide exactly 3 extremely short, high-impact strategies tailored for: "${course.title}".
                    Keep statements, tips, and bullet points under 15 words each. No greetings.
                """.trimIndent()

                val response = repository.generateContent(systemInstruction, prompt)
                _madTipsState.value = response
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Failed to retrieve Mad Tips: ${e.message}")
                _networkError.value = "Connection issue — check your internet and tap to retry"
                _madTipsState.value = """
                    💡 **MIVA Open University Core Hacks**:
                    - **The Extremes Filter**: Absolute qualifiers ('always', 'only', 'never') inside questions are highly likely to be distractors. Lean on nuanced definitions.
                    - **Application Questions**: Rather than basic rote recital, MIVA exam setters love asking scenario questions where you must diagnose POSDC components.
                    - **Formula Recall**: Scribble down accounting balance equations and key loops the second the timer initiates to relieve active working memory!
                """.trimIndent()
            } finally {
                _isGenerating.value = false
            }
        }
    }

    // Clean JSON block markers
    private fun cleanJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) {
            clean = clean.substring("```json".length)
        } else if (clean.startsWith("```")) {
            clean = clean.substring("```".length)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - "```".length)
        }
        return clean.trim()
    }

    // --- PAYSTACK CHECKOUT ACTIONS ---
    fun unlockStudyBuddy() {
        sharedPrefs.edit().putBoolean("is_unlocked", true).apply()
        _isUnlocked.value = true
    }

    fun lockStudyBuddy() {
        sharedPrefs.edit().putBoolean("is_unlocked", false).apply()
        _isUnlocked.value = false
    }

    fun resetPaystackState() {
        _paystackState.value = PaystackInitUiState.Idle
    }

    fun initializePaystackPayment(userEmail: String, referralApplied: Boolean = false) {
        val emailToUse = if (userEmail.isBlank()) "student@miva.edu.ng" else userEmail
        val targetAmount = if (referralApplied) 80000 else 100000
        _paystackState.value = PaystackInitUiState.Loading("Initializing Checkout Window...")
        viewModelScope.launch {
            try {
                val request = SupabaseInitializeRequest(email = emailToUse, amount = targetAmount)
                val response = PaystackClient.service.initializeTransaction(request = request)
                
                val authUrl = response.finalAuthorizationUrl
                val ref = response.finalReference
                
                if (authUrl != null && ref != null) {
                    _paystackState.value = PaystackInitUiState.Success(
                        authorizationUrl = authUrl,
                        reference = ref
                    )
                } else {
                    _paystackState.value = PaystackInitUiState.Error(response.message ?: "Failed to get payment checkout URL.")
                }
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Paystack initialization failed via Supabase wrapper", e)
                _paystackState.value = PaystackInitUiState.Error(e.localizedMessage ?: "Unknown network error during init")
            }
        }
    }

    fun verifyPaystackPayment(reference: String) {
        _paystackState.value = PaystackInitUiState.Loading("Verifying Payment with Server...")
        viewModelScope.launch {
            try {
                val response = PaystackClient.service.verifyTransaction(
                    request = SupabaseVerifyRequest(reference = reference)
                )
                if (response.isSuccessful) {
                    // Log successful payment event
                    val pParams = android.os.Bundle().apply {
                        putString("payment_reference", reference)
                    }
                    AnalyticsHelper.logEvent("payment_success", pParams)

                    unlockStudyBuddy()
                    _paystackState.value = PaystackInitUiState.Idle
                } else {
                    val fallbackMsg = response.message ?: "Payment verification failed or was cancelled."
                    _paystackState.value = PaystackInitUiState.Error(fallbackMsg)
                }
            } catch (e: Exception) {
                Log.e("StudyBuddyVM", "Paystack verification failed via Supabase wrapper", e)
                _paystackState.value = PaystackInitUiState.Error(e.localizedMessage ?: "Unknown network error during validation")
            }
        }
    }
}
