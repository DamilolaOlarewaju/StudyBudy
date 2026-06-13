package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import com.example.data.*
import com.example.ui.StudyBuddyViewModel
import com.example.ui.PaystackInitUiState
import com.example.ui.PdfDropZone
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: StudyBuddyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Firebase Analytics helper
        AnalyticsHelper.init(applicationContext)
        
        // Set basic user & device properties for diagnostics and tracking
        AnalyticsHelper.setUserProperty("device_model", android.os.Build.MODEL)
        AnalyticsHelper.setUserProperty("device_brand", android.os.Build.BRAND)
        AnalyticsHelper.setUserProperty("device_product", android.os.Build.PRODUCT)
        AnalyticsHelper.setUserProperty("device_locale", java.util.Locale.getDefault().toString())
        
        // Log when application is opened
        AnalyticsHelper.logEvent("app_opened")

        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val sharedPrefs = remember { context.getSharedPreferences("study_buddy_prefs", android.content.Context.MODE_PRIVATE) }
            var showOnboarding by remember { mutableStateOf(!sharedPrefs.getBoolean("has_seen_onboarding", false)) }
            val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = isDark, dynamicColor = false) {
                if (showOnboarding) {
                    OnboardingScreen(
                        onDismiss = {
                            sharedPrefs.edit().putBoolean("has_seen_onboarding", true).apply()
                            showOnboarding = false
                        }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        StudyBuddyWorkspace(
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyBuddyWorkspace(
    viewModel: StudyBuddyViewModel,
    modifier: Modifier = Modifier
) {
    val courses by viewModel.allCourses.collectAsStateWithLifecycle()
    val selectedCourse by viewModel.selectedCourse.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val currentMode by viewModel.currentMode.collectAsStateWithLifecycle()

    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    var showPaywallDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showPaywallDialog) {
        if (showPaywallDialog) {
            AnalyticsHelper.logEvent("payment_initiated")
        }
    }

    var showCourseDialog by remember { mutableStateOf(false) }
    var showDropdownMenu by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    
    // Theme and reactive parameters
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val onboardingCode by viewModel.userReferralCode.collectAsStateWithLifecycle()
    val networkError by viewModel.networkError.collectAsStateWithLifecycle()

    val dynamicBg = if (isDark) Color(0xFF0F0F0F) else IvoryBackground
    val dynamicTopBarBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val dynamicCardBg = if (isDark) Color(0xFF1E1E1E) else Color.White
    val dynamicSyllabusCardBg = if (isDark) Color(0xFF1A1A1A) else SoftSageBg
    val dynamicTextPrimary = if (isDark) Color.White else CharcoalDark
    val dynamicTextSecondary = if (isDark) Color(0xFFD1D5DB) else SlateTextMuted
    val dynamicTextMuted = if (isDark) Color(0xFF9CA3AF) else SageTextMuted
    val dynamicBorder = if (isDark) Color(0xFF2E2E2E) else OutlineMedium
    val dynamicAccentTint = if (isDark) Color(0xFF818CF8) else ForestGreen

    Box(
        modifier = modifier
            .background(dynamicBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Clean professional white Top Bar with brand name and pill Pro badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dynamicTopBarBg)
                    .border(width = (0.5).dp, color = if (isDark) Color(0xFF2A2A2A) else OutlineLight)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "StudyBuddy 🎓",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold),
                    color = dynamicAccentTint, 
                )

                Spacer(modifier = Modifier.weight(1f))

                // Settings icon
                IconButton(
                    onClick = { showSettingsDialog = true },
                    modifier = Modifier.testTag("settings_button").size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = dynamicAccentTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Pill shaped Pro badge (Indigo locked, Green unlocked)
                if (!isUnlocked) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(dynamicAccentTint) 
                            .clickable { showPaywallDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "GET PRO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF10B981)) // Secondary Emerald Accent
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Dropdown Menu for selecting courses (anchored below)
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                DropdownMenu(
                    expanded = showDropdownMenu,
                    onDismissRequest = { showDropdownMenu = false },
                    modifier = Modifier
                        .background(dynamicCardBg)
                        .border(1.dp, dynamicBorder, RoundedCornerShape(16.dp))
                        .width(IntrinsicSize.Max)
                ) {
                    courses.forEach { course ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = course.title,
                                    color = if (course.id == selectedCourse?.id) dynamicAccentTint else dynamicTextPrimary,
                                    fontWeight = if (course.id == selectedCourse?.id) FontWeight.Bold else FontWeight.Normal,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                viewModel.selectCourse(course)
                                showDropdownMenu = false
                            },
                            modifier = Modifier.testTag("course_item_${course.id}")
                        )
                    }
                }
            }

            // Clean, gorgeous Course Material Selector Card below top bar
            if (selectedCourse != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = dynamicSyllabusCardBg), 
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = dynamicAccentTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CURRENT SYLLABUS NOTES",
                                style = MaterialTheme.typography.labelSmall,
                                color = dynamicTextMuted
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showDropdownMenu = true }
                                    .testTag("course_selector_dropdown")
                            ) {
                                Text(
                                    text = selectedCourse?.title ?: "Select Study Material",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = dynamicTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Select Course",
                                    tint = dynamicAccentTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        
                        // Add Course Action (Pasted Notes / Import PDF)
                        IconButton(
                            onClick = {
                                if (!isUnlocked) {
                                    showPaywallDialog = true
                                } else {
                                    showCourseDialog = true
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("add_course_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Notes / Outline",
                                tint = dynamicAccentTint,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Delete current Course
                        IconButton(
                            onClick = { viewModel.deleteCurrentCourse() },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_course_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Material",
                                tint = Color.Red.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Connection Issue Warning Card
            if (networkError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF2C1616) else Color(0xFFFEE2E2)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(
                            1.dp,
                            if (isDark) Color(0xFFEF4444) else Color(0xFFFCA5A5),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.retryLastAction() }
                        .testTag("network_error_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = if (isDark) Color(0xFFEF4444) else Color(0xFFB91C1C),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = networkError ?: "Connection issue — check your internet and tap to retry",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = if (isDark) Color.White else Color(0xFF991B1B)
                            )
                        }

                        Button(
                            onClick = { viewModel.retryLastAction() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDark) Color(0xFFEF4444) else Color(0xFFB91C1C)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("network_retry_button")
                        ) {
                            Text("Retry", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }

            // Loading state indicator
            AnimatedVisibility(
                visible = isGenerating,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LinearProgressIndicator(
                    color = dynamicAccentTint,
                    trackColor = ProgressTrack,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (selectedCourse == null) {
                // Premium Empty State Dashboard layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .background(IvoryBackground)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, OutlineLight, RoundedCornerShape(24.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Double-layered overlapping graduation icon badge for academic visual depth
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(SoftSageBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "🎓",
                                        fontSize = 38.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "MIVA Academic Hub",
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = ForestGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    textAlign = TextAlign.Center
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Unlock elite academic performance. Upload your course syllabus outline, lecture PDF papers, or raw notes to let StudyBuddy auto-compile 60 Unit Mock Exams, personalized Feynman tutorials, and high-yield study cards.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SageTextMuted,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 22.sp
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // PDF drag + drop parsing zone with elevated borders
                                PdfDropZone(
                                    onPdfParsed = { title, content ->
                                        if (!isUnlocked) {
                                            showPaywallDialog = true
                                        } else {
                                            viewModel.addNewCourse(title, content)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Box(modifier = Modifier.weight(1f).height(1.dp).background(OutlineLight))
                                    Text(
                                        text = " OR PASTE MANUALLY ",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SlateTextMuted,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )
                                    Box(modifier = Modifier.weight(1f).height(1.dp).background(OutlineLight))
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        if (!isUnlocked) {
                                            showPaywallDialog = true
                                        } else {
                                            showCourseDialog = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Paste Syllabus Notes Outline",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Elegantly styled Selected Mode Panel Box framed with premium layout rules
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBackground)
                        .border(1.dp, OutlineMedium.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                ) {
                    when (currentMode) {
                        "STUDY" -> StudyPanel(
                            course = selectedCourse!!,
                            viewModel = viewModel,
                            onShowPaywall = { showPaywallDialog = true }
                        )
                        "TEACH_ME" -> TeachMePanel(course = selectedCourse!!, viewModel = viewModel)
                        "EXAM_SIM" -> ExamSimulatorPanel(course = selectedCourse!!, viewModel = viewModel)
                        "MAD_TIPS" -> MadTipsPanel(viewModel = viewModel)
                    }
                }

                // Premium bottom tab bar navigation (like Duolingo and Khan Academy) with icons + labels + rounded indicators
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(BorderStroke(0.5.dp, OutlineLight), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modes = listOf(
                            Triple("STUDY", "Study Hub", Icons.Default.Home),
                            Triple("TEACH_ME", "Feynman Coach", Icons.Default.AccountCircle),
                            Triple("EXAM_SIM", "Exam Sim", Icons.Default.PlayArrow),
                            Triple("MAD_TIPS", "Exam Hacks", Icons.Default.Star)
                        )

                        modes.forEach { (modeCode, label, icon) ->
                            val isSelected = currentMode == modeCode
                            val activeColor = ForestGreen
                            val inactiveColor = SageTextMuted

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        if (!isUnlocked && (modeCode == "EXAM_SIM" || modeCode == "TEACH_ME")) {
                                            showPaywallDialog = true
                                        } else {
                                            viewModel.setMode(modeCode)
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .widthIn(min = 68.dp)
                            ) {
                                // Rounded selection pill indicator background behind active icon
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isSelected) SagePillBg else Color.Transparent)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        tint = if (isSelected) activeColor else inactiveColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) activeColor else inactiveColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add Custom Course Dialog
        if (showCourseDialog) {
            AddCourseDialog(
                onDismiss = { showCourseDialog = false },
                onAdd = { title, content ->
                    viewModel.addNewCourse(title, content)
                    showCourseDialog = false
                }
            )
        }

        // Settings Dialog
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Text(
                        text = "Settings & Referrals ⚙️",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isDark) Color.White else CharcoalDark
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Dark mode toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Theme",
                                    tint = dynamicAccentTint,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Dark Mode 🌙",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isDark) Color.White else CharcoalDark
                                )
                            }
                            Switch(
                                checked = isDark,
                                onCheckedChange = { viewModel.toggleDarkMode(it) },
                                modifier = Modifier.testTag("dark_mode_switch")
                            )
                        }

                        Divider(color = if (isDark) Color(0xFF2E2E2E) else OutlineLight)

                        // Referral system
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "REFER & GET DISCOUNT 🎟️",
                                style = MaterialTheme.typography.labelSmall,
                                color = dynamicAccentTint,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Your Unique Referral Code:",
                                style = MaterialTheme.typography.bodySmall,
                                color = dynamicTextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFF3F4F6))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = onboardingCode.ifEmpty { "SB2026" },
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (isDark) Color.White else CharcoalDark
                                )

                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                val utilsContext = LocalContext.current
                                TextButton(
                                    onClick = {
                                        val codeText = onboardingCode.ifEmpty { "SB2026" }
                                        val promoMessage = "Use my code $codeText on StudyBuddy to get ₦200 discount! Download: [APK LINK]"
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(promoMessage))
                                        android.widget.Toast.makeText(utilsContext, "Referral message copied to clipboard! 📋", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("refer_copy_button")
                                ) {
                                    Text("Copy Invite", color = dynamicAccentTint)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Invite a friend! When they pay with your code, they get ₦200 off and you unlock premium features.",
                                style = MaterialTheme.typography.bodySmall,
                                color = dynamicTextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = dynamicAccentTint)
                    ) {
                        Text("Done", color = Color.White)
                    }
                },
                containerColor = dynamicCardBg
            )
        }

        // Paystack Payment Dialog Flow (Cabinet design)
        if (showPaywallDialog) {
            PaystackPaymentDialog(
                viewModel = viewModel,
                onDismiss = { showPaywallDialog = false }
            )
        }
    }
}

// --- COMPILED PANELS CONTENT ---

@Composable
fun StudyPanel(
    course: StudyCourse,
    viewModel: StudyBuddyViewModel,
    onShowPaywall: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Analysis Info",
                    tint = ForestGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HIGH-YIELD STUDY COGNITIVE CARD",
                    style = MaterialTheme.typography.titleSmall,
                    color = ForestGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

            if (isGenerating && (course.keyConceptsJson == null || course.keyConceptsJson.isBlank())) {
                Box(modifier = Modifier.weight(1f)) {
                    SkeletonLoadingScreen(linesCount = 8)
                }
            } else {
                val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
                val contentToDisplay = course.keyConceptsJson ?: "Scanning course syllabus... Parsing Key Concepts..."
                MarkdownViewer(
                    text = contentToDisplay,
                    modifier = Modifier.weight(1f),
                    isLight = !isDark,
                    compactMode = true,
                    expandableSections = true
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Space-optimizing side-by-side premium control buttons (42dp height, 10dp rounded corners) to maximize the reading area
        Row(
            modifier = Modifier.fillMaxWidth().height(42.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Teach Me Button — outlined indigo color
            OutlinedButton(
                onClick = { 
                    if (!viewModel.isUnlocked.value) {
                        onShowPaywall()
                    } else {
                        viewModel.setMode("TEACH_ME")
                    }
                },
                border = BorderStroke(1.dp, ForestGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = ForestGreen,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Teach Me",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Exam Mode Button — bold amber with a 🔥 icon
            Button(
                onClick = { 
                    if (!viewModel.isUnlocked.value) {
                        onShowPaywall()
                    } else {
                        viewModel.setMode("EXAM_SIM")
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🔥 Simulate Exam",
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingTypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Text("Thinking...", style = MaterialTheme.typography.bodyMedium, color = SlateTextMuted)
    }
}

@Composable
fun SkeletonItem(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    widthFraction: Float = 1f,
    shape: androidx.compose.foundation.shape.CornerBasedShape = RoundedCornerShape(4.dp)
) {
    Box(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .clip(shape)
            .background(Color(0xFFE5E7EB)) // Static slate-gray block — 0% CPU consumption
    )
}

@Composable
fun SkeletonLoadingScreen(
    linesCount: Int = 5
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        // Shimmering header block
        SkeletonItem(height = 24.dp, widthFraction = 0.45f, shape = RoundedCornerShape(8.dp))
        Spacer(modifier = Modifier.height(14.dp))
        // Multiple body lines of varying widths
        repeat(linesCount) { index ->
            val fraction = when (index % 3) {
                0 -> 0.95f
                1 -> 0.85f
                else -> 0.65f
            }
            SkeletonItem(height = 14.dp, widthFraction = fraction)
        }
    }
}

@Composable
fun TeachMePanel(
    course: StudyCourse,
    viewModel: StudyBuddyViewModel
) {
    val chats by viewModel.activeChats.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val streamingChatText by viewModel.streamingChatText.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputStr by remember { mutableStateOf("") }

    // PDF attachment states
    var attachedPdfTitle by remember { mutableStateOf<String?>(null) }
    var attachedPdfText by remember { mutableStateOf<String?>(null) }
    var isAttachingPdf by remember { mutableStateOf(false) }
    var attachmentError by remember { mutableStateOf<String?>(null) }

    // Setup file picker launcher for selecting custom course notes PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAttachingPdf = true
            attachmentError = null
            coroutineScope.launch {
                try {
                    val title = com.example.util.PdfParser.getFileName(context, uri)
                    val extractedText = com.example.util.PdfParser.extractTextFromUri(context, uri)
                    if (extractedText.isBlank()) {
                        attachmentError = "Could not extract any text from this PDF. Is it scanned or empty?"
                    } else {
                        attachedPdfTitle = title
                        attachedPdfText = extractedText
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TeachMePanel", "Parsing failed", e)
                    attachmentError = "Failed to extract PDF text: ${e.localizedMessage ?: "Unknown error"}"
                } finally {
                    isAttachingPdf = false
                }
            }
        }
    }

    LaunchedEffect(key1 = course.id) {
        if (chats.isEmpty()) {
            viewModel.startTeachMeSession()
        }
    }

    LaunchedEffect(key1 = chats.size) {
        if (chats.isNotEmpty()) {
            listState.animateScrollToItem(chats.size - 1)
        }
    }

    LaunchedEffect(key1 = streamingChatText) {
        if (isGenerating && streamingChatText != null) {
            val totalCount = chats.size + 1
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Chat Log Panel with Natural Tones frame
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, OutlineMedium, RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (chats.isEmpty() && !isGenerating) {
                item {
                    Text(
                        text = "StudyBuddy is preparing the first lesson block...",
                        color = SlateTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(chats) { chat ->
                    val isAssistant = chat.role == "assistant"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 290.dp)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 16.dp,
                                        topEnd = 16.dp,
                                        bottomStart = if (isAssistant) 4.dp else 16.dp,
                                        bottomEnd = if (isAssistant) 16.dp else 4.dp
                                    )
                                )
                                .background(if (isAssistant) Color(0xFFF3F4F6) else ForestGreen)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (isAssistant) "🎓 STUDY BUDDY" else "STUDENT (YOU)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAssistant) ForestGreen else Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            MarkdownViewer(
                                text = chat.message,
                                isScrollable = false,
                                isLight = isAssistant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (isGenerating && streamingChatText != null) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 290.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = 4.dp,
                                            bottomEnd = 16.dp
                                        )
                                    )
                                    .background(Color(0xFFF3F4F6))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "🎓 STUDY BUDDY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (streamingChatText.isNullOrEmpty()) {
                                    ThinkingTypingIndicator()
                                } else {
                                    MarkdownViewer(
                                        text = streamingChatText!!,
                                        isScrollable = false,
                                        isLight = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick confirmation prompts with Natural Tones outlined capsules
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val preTxt = "Got it! Let's move on to the next concept."
                    viewModel.sendTeachReply(preTxt)
                },
                border = BorderStroke(1.dp, ForestGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("✅ Got it, move on", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = {
                    val preTxt = "Can you go deeper into this section with another real-world analogy?"
                    viewModel.sendTeachReply(preTxt)
                },
                border = BorderStroke(1.dp, ForestGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text("🔍 Explaining deeper", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Show active PDF attachment error card if any
        if (attachmentError != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.05f)),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Error",
                        tint = Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = attachmentError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { attachmentError = null }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Show active PDF attachment badge if any
        if (attachedPdfTitle != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SoftSageBg),
                border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📄",
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = attachedPdfTitle ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = CharcoalDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Course material is attached. Send to explore with Study Buddy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SageTextMuted
                        )
                    }
                    IconButton(
                        onClick = {
                            attachedPdfTitle = null
                            attachedPdfText = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove attachment",
                            tint = Color.Red.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Text input field designed for Natural Tones
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputStr,
                onValueChange = { inputStr = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = CharcoalDark),
                placeholder = { Text("Ask a question, request mini-quiz...", color = SageTextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = OutlineMedium,
                    focusedBorderColor = ForestGreen,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                ),
                leadingIcon = {
                    if (isAttachingPdf) {
                        CircularProgressIndicator(
                            color = ForestGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(
                            onClick = { filePickerLauncher.launch("application/pdf") },
                            modifier = Modifier.testTag("attach_material_button")
                        ) {
                            Text(
                                text = "📎",
                                fontSize = 18.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputStr.isNotBlank() || attachedPdfText != null) {
                        viewModel.sendTeachReply(
                            message = inputStr,
                            pdfTitle = attachedPdfTitle,
                            pdfContent = attachedPdfText
                        )
                        inputStr = ""
                        attachedPdfTitle = null
                        attachedPdfText = null
                        keyboardController?.hide()
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("teach_input_text")
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ForestGreen)
                    .clickable {
                        if (inputStr.isNotBlank() || attachedPdfText != null) {
                            viewModel.sendTeachReply(
                                message = inputStr,
                                pdfTitle = attachedPdfTitle,
                                pdfContent = attachedPdfText
                            )
                            inputStr = ""
                            attachedPdfTitle = null
                            attachedPdfText = null
                            keyboardController?.hide()
                        }
                    }
                    .testTag("teach_send_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send Reply",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ExamSimulatorPanel(
    course: StudyCourse,
    viewModel: StudyBuddyViewModel
) {
    val questions by viewModel.examQuestions.collectAsStateWithLifecycle()
    val currIdx by viewModel.currentExamQuestionIndex.collectAsStateWithLifecycle()
    val selectedAns by viewModel.selectedAnswers.collectAsStateWithLifecycle()
    val isConfirmed by viewModel.isExamAnswerConfirmed.collectAsStateWithLifecycle()
    val isCompleted by viewModel.examCompleted.collectAsStateWithLifecycle()
    val feedback by viewModel.weakSpotsFeedback.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

        if (questions.isEmpty()) {
            if (isGenerating) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Analyzing notes & compiling MIVA exam items...",
                        style = MaterialTheme.typography.titleMedium,
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    SkeletonLoadingScreen(linesCount = 8)
                }
            } else {
                // Unloaded Exam Sim State (Natural Tones Design Theme)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "🔥 MIVA Mock Exam Generator",
                            style = MaterialTheme.typography.titleMedium,
                            color = CharcoalDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Compiles 60 highly probable examination multiple choice questions directly based on \"${course.title}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SageTextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.generateSimulatorExam() },
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text("Simulate MIVA Exam", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (isCompleted) {
            // Exam Finished Score Summary (Natural Tones)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(2.dp, ForestGreen, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🏆 MOCK EXAM RATING REPORT",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val points = questions.mapIndexed { idx, q ->
                        selectedAns[idx] == q.correctAnswerIndex
                    }.count { it }

                    Text(
                        text = "$points / ${questions.size}",
                        style = MaterialTheme.typography.displayMedium,
                        color = ForestGreen,
                        fontWeight = FontWeight.ExtraBold
                    )
                    
                    val rating = if (points == questions.size) "Elite (MIVA Top Tier)" else "High-Yield Gap Identified"
                    Text(
                        text = rating,
                        style = MaterialTheme.typography.titleMedium,
                        color = AccentGold,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = OutlineLight)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "🎓 StudyBuddy Gaps Analysis:",
                        style = MaterialTheme.typography.titleSmall,
                        color = ForestGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isGenerating && feedback.isBlank()) {
                        SkeletonLoadingScreen(linesCount = 4)
                    } else {
                        MarkdownViewer(
                            text = feedback,
                            isScrollable = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    val scoreContext = LocalContext.current
                    val courseTitle = course.title
                    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
                    val dynamicBorder = if (isDark) Color(0xFF2E2E2E) else OutlineMedium
                    val dynamicTextPrimary = if (isDark) Color.White else CharcoalDark

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.generateSimulatorExam() },
                            colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF1F2937) else Color(0xFFE5E7EB)),
                            border = BorderStroke(1.dp, dynamicBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp).testTag("simulate_again_button")
                        ) {
                            Text("Simulate Again", color = dynamicTextPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val textToShare = "I just scored $points/${questions.size} on my $courseTitle exam simulation on StudyBuddy! 🔥 Get the app here: [APK LINK]"
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
                                }
                                try {
                                    sendIntent.setPackage("com.whatsapp")
                                    scoreContext.startActivity(sendIntent)
                                } catch (e: Exception) {
                                    val chooser = android.content.Intent.createChooser(sendIntent, "Share Score via")
                                    scoreContext.startActivity(chooser)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)), // WhatsApp elegant green
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(46.dp).testTag("share_whatsapp_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Active Exam Quest State
            val currentConfirmed = isConfirmed[currIdx] ?: false
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(0.5.dp, OutlineLight, RoundedCornerShape(20.dp))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "QUESTION ${currIdx + 1} OF ${questions.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = SageTextMuted
                        )
                        Text(
                            text = "Target: 90% High-Yield",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = ForestGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Progress bar showing question 1/10
                    LinearProgressIndicator(
                        progress = { (currIdx + 1).toFloat() / questions.size.toFloat() },
                        color = ForestGreen,
                        trackColor = ProgressTrack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val currentQuestion = questions[currIdx]
                    
                    // Question text
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                        color = CharcoalDark,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Multiple choice options list as outlined pill buttons
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        currentQuestion.options.forEachIndexed { optIndex, optionText ->
                            val isSelected = selectedAns[currIdx] == optIndex
                            val confirmed = isConfirmed[currIdx] ?: false
                            val isCorrect = currentQuestion.correctAnswerIndex == optIndex

                            val borderBrush = when {
                                confirmed && isCorrect -> ForestGreen
                                confirmed && isSelected -> Color.Red
                                isSelected -> ForestGreen
                                else -> OutlineMedium
                            }

                            val borderWidth = if (isSelected || (confirmed && isCorrect)) 2.dp else 1.dp

                            val colorBg = when {
                                confirmed && isCorrect -> CorrectOptionBg
                                confirmed && isSelected -> Color.Red.copy(alpha = 0.05f)
                                isSelected -> SagePillBg.copy(alpha = 0.3f)
                                else -> Color.White
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp)) // Outlined pill style (circular rounder)
                                    .background(colorBg)
                                    .clickable(enabled = !confirmed) {
                                        viewModel.selectExamAnswer(currIdx, optIndex)
                                    }
                                    .border(borderWidth, borderBrush, RoundedCornerShape(24.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(15.dp)) // Pill circle badge
                                        .background(
                                            when {
                                                confirmed && isCorrect -> ForestGreen
                                                confirmed && isSelected -> Color.Red
                                                isSelected -> ForestGreen
                                                else -> OutlineMedium.copy(alpha = 0.6f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ('A' + optIndex).toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = CharcoalDark,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Explanation card once answer is confirmed (Natural Tones)
                    val currentConfirmed = isConfirmed[currIdx] ?: false
                    if (currentConfirmed) {
                        val answeredIdx = selectedAns[currIdx]
                        val correctIdx = currentQuestion.correctAnswerIndex
                        val isAnswerCorrect = answeredIdx == correctIdx

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAnswerCorrect) CorrectOptionBg else Color.Red.copy(alpha = 0.03f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isAnswerCorrect) ForestGreen else Color.Red,
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isAnswerCorrect) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = if (isAnswerCorrect) "Correct" else "Wrong",
                                        tint = if (isAnswerCorrect) ForestGreen else Color.Red,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isAnswerCorrect) "CORRECT ✅" else "INCORRECT ❌",
                                        color = if (isAnswerCorrect) ForestGreen else Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = currentQuestion.explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CharcoalDark,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Exam flow actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev button
                IconButton(
                    onClick = { viewModel.prevExamQuestion() },
                    enabled = currIdx > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous",
                        tint = if (currIdx > 0) ForestGreen else OutlineMedium
                    )
                }

                if (!currentConfirmed) {
                    Button(
                        onClick = { viewModel.confirmExamAnswer(currIdx) },
                        enabled = selectedAns.containsKey(currIdx),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("submit_answer_button")
                    ) {
                        Text("Confirm Answer", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else if (currIdx == questions.size - 1) {
                    Button(
                        onClick = { viewModel.submitExam() },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("submit_exam_button")
                    ) {
                        Text("Complete Simulator Exam", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { viewModel.nextExamQuestion() },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("next_question_button")
                    ) {
                        Text("Next Question", color = Color.White)
                    }
                }

                // Next button
                IconButton(
                    onClick = { viewModel.nextExamQuestion() },
                    enabled = currIdx < questions.size - 1
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        tint = if (currIdx < questions.size - 1) ForestGreen else OutlineMedium
                    )
                }
            }
        }
    }
}

@Composable
fun MadTipsPanel(
    viewModel: StudyBuddyViewModel
) {
    val tips by viewModel.madTipsState.collectAsStateWithLifecycle()

    LaunchedEffect(key1 = true) {
        AnalyticsHelper.logEvent("tips_viewed")
        viewModel.fetchMadTips()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Tips cap",
                    tint = ForestGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "💡 MIVA INTEL & EXAM STRATEGIES",
                    style = MaterialTheme.typography.titleSmall,
                    color = ForestGreen,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

            if (isGenerating && tips.isBlank()) {
                Box(modifier = Modifier.weight(1f)) {
                    SkeletonLoadingScreen(linesCount = 6)
                }
            } else {
                val content = tips.ifBlank { "Compiling specific strategies for your syllabus outline..." }
                MarkdownViewer(
                    text = content,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = { viewModel.fetchMadTips() },
            border = BorderStroke(1.dp, ForestGreen),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ForestGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🔄 Refresh Mad Tips", fontWeight = FontWeight.SemiBold)
        }
    }
}

// --- STANDARD DIRECT VIEW DYNAMICS ---

@Composable
fun AddCourseDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
                .border(1.dp, OutlineMedium, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Import Course / Notes Outline",
                    style = MaterialTheme.typography.titleMedium,
                    color = CharcoalDark,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Easily upload a PDF of your class slides, book chapter, or syllabus notes to auto-populate the fields below, or type/paste manually:",
                    style = MaterialTheme.typography.bodySmall,
                    color = SageTextMuted,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Inline PDF parser that populates the field state instantly
                PdfDropZone(
                    onPdfParsed = { parsedTitle, parsedText ->
                        title = parsedTitle
                        content = parsedText
                    },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Course Title (e.g. MIVA ACC 201)", color = SageTextMuted) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CharcoalDark),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = OutlineMedium,
                        focusedBorderColor = ForestGreen,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_course_title_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Notes, Syllabus, or Book Chapters...", color = SageTextMuted) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CharcoalDark),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = OutlineMedium,
                        focusedBorderColor = ForestGreen,
                        unfocusedContainerColor = Color.White,
                        focusedContainerColor = Color.White
                    ),
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_course_content_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ForestGreen, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank() && content.isNotBlank()) {
                                onAdd(title, content)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("dialog_confirm_add_button")
                    ) {
                        Text("Analyze & Coach", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun escapeHtml(text: String): String {
    val clean = text.replace("\r\n", "\n").replace("\r", "\n")
    val placeholder = "___LINE_BREAK_PLACEHOLDER___"
    val escText = clean.replace("\n", placeholder)
    
    val out = StringBuilder()
    for (i in 0 until escText.length) {
        val c = escText[i]
        when (c) {
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '&' -> out.append("&amp;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
    }
    return out.toString().replace(placeholder, "<br/>")
}

fun containsLatex(text: String): Boolean {
    if (text.contains("$")) return true
    if (text.contains("\\(") || text.contains("\\[") || text.contains("\\)")) return true
    val commonLatexSymbols = listOf(
        "\\frac", "\\sum", "\\int", "\\alpha", "\\beta", "\\gamma", "\\theta", "\\lambda",
        "\\sqrt", "\\partial", "\\infty", "\\begin", "\\end", "\\pm", "\\times", "\\div",
        "\\le", "\\ge", "\\pi", "\\mu", "\\sigma", "\\Delta", "\\omega", "\\phi"
    )
    for (sym in commonLatexSymbols) {
        if (text.contains(sym)) return true
    }
    return false
}

@Composable
fun KaTeXWebView(
    text: String,
    isLight: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var webViewHeight by remember { mutableStateOf(40) }
    val density = LocalDensity.current

    val textColor = if (isLight) "#1A1D1E" else "#FFFFFF"
    val escapedText = remember(text) { escapeHtml(text) }
    
    val htmlContent = remember(escapedText, textColor) {
        """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.css">
                <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/katex.min.js"></script>
                <script src="https://cdn.jsdelivr.net/npm/katex@0.16.8/dist/contrib/auto-render.min.js"></script>
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        margin: 0;
                        padding: 0;
                        font-size: 14.5px;
                        line-height: 1.5;
                        color: ${'$'}textColor;
                        background-color: transparent !important;
                        word-wrap: break-word;
                    }
                    .katex-display {
                        margin: 6px 0 !important;
                        overflow-x: auto;
                        overflow-y: hidden;
                    }
                    p {
                        margin: 0 0 6px 0;
                    }
                    p:last-child {
                        margin-bottom: 0;
                    }
                </style>
            </head>
            <body>
                <div id="content">${'$'}escapedText</div>
                <script>
                    function sendHeight() {
                        var elem = document.getElementById("content");
                        if (elem && window.AndroidInterface) {
                            var height = elem.scrollHeight || document.body.scrollHeight;
                            window.AndroidInterface.resize(height);
                        }
                    }
                    
                    document.addEventListener("DOMContentLoaded", function() {
                        try {
                            renderMathInElement(document.body, {
                                delimiters: [
                                    {left: "$$", right: "$$", display: true},
                                    {left: "$", right: "$", display: false},
                                    {left: "\\(", right: "\\)", display: false},
                                    {left: "\\[", right: "\\]", display: true}
                                ],
                                throwOnError : false
                            });
                        } catch(e) {
                            console.error(e);
                        }
                        
                        setTimeout(sendHeight, 10);
                        setTimeout(sendHeight, 100);
                        setTimeout(sendHeight, 300);
                    });
                    
                    window.addEventListener("load", sendHeight);
                    window.addEventListener("resize", sendHeight);
                    
                    var observer = new MutationObserver(sendHeight);
                    observer.observe(document.body, { attributes: true, childList: true, subtree: true });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                
                addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun resize(height: Float) {
                        post {
                            if (height > 0) {
                                webViewHeight = height.toInt()
                            }
                        }
                    }
                }, "AndroidInterface")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("javascript:sendHeight();", null)
                    }
                }
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
        },
        modifier = modifier
            .fillMaxWidth()
            .height((webViewHeight / context.resources.displayMetrics.density).dp + 2.dp)
    )
}

sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class CodeBlock(val language: String?, val code: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class EquationBlock(val formula: String) : MarkdownBlock()
}

fun parseMarkdown(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.lines()
    var inCodeBlock = false
    val codeBuilder = StringBuilder()
    var codeLang: String? = null

    var inEquationBlock = false
    val equationBuilder = StringBuilder()

    for (line in lines) {
        val trimmed = line.trim()

        // Handle Code Blocks first
        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                codeLang = null
                inCodeBlock = false
            } else {
                inCodeBlock = true
                val lang = trimmed.substring(3).trim()
                codeLang = if (lang.isNotEmpty()) lang else null
            }
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(line).append("\n")
            continue
        }

        // Handle Equation Blocks
        if (trimmed.startsWith("$$") && trimmed.endsWith("$$") && trimmed.length > 2) {
            val formula = trimmed.removePrefix("$$").removeSuffix("$$").trim()
            blocks.add(MarkdownBlock.EquationBlock(formula))
            continue
        }

        if (trimmed == "$$") {
            if (inEquationBlock) {
                blocks.add(MarkdownBlock.EquationBlock(equationBuilder.toString().trimEnd()))
                equationBuilder.clear()
                inEquationBlock = false
            } else {
                inEquationBlock = true
            }
            continue
        }

        if (inEquationBlock) {
            equationBuilder.append(line).append("\n")
            continue
        }

        when {
            trimmed.startsWith("###") -> {
                blocks.add(MarkdownBlock.Header(3, trimmed.removePrefix("###").trim()))
            }
            trimmed.startsWith("##") -> {
                blocks.add(MarkdownBlock.Header(2, trimmed.removePrefix("##").trim()))
            }
            trimmed.startsWith("#") -> {
                blocks.add(MarkdownBlock.Header(1, trimmed.removePrefix("#").trim()))
            }
            trimmed.startsWith("- ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.removePrefix("- ").trim()))
            }
            trimmed.startsWith("* ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.removePrefix("* ").trim()))
            }
            trimmed.startsWith("✦ ") -> {
                blocks.add(MarkdownBlock.BulletItem(trimmed.removePrefix("✦ ").trim()))
            }
            trimmed.isNotEmpty() -> {
                blocks.add(MarkdownBlock.Paragraph(line))
            }
            else -> {
                // Ignore empty lines between blocks or add simple spacers if preferred
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
    }
    if (inEquationBlock && equationBuilder.isNotEmpty()) {
        blocks.add(MarkdownBlock.EquationBlock(equationBuilder.toString().trimEnd()))
    }

    return blocks
}

fun stripEscapedCharacters(text: String): String {
    if (text.isEmpty()) return ""
    var cleaned = text
    
    // Unescape common characters that are unnecessarily escaped by markdown/LaTex generators
    cleaned = cleaned.replace("\\$", "$")
    cleaned = cleaned.replace("\\*", "*")
    cleaned = cleaned.replace("\\_", "_")
    cleaned = cleaned.replace("\\#", "#")
    cleaned = cleaned.replace("\\-", "-")
    cleaned = cleaned.replace("\\+", "+")
    cleaned = cleaned.replace("\\`", "`")
    cleaned = cleaned.replace("\\~", "~")
    
    // Clean raw JSON-escaped newlines and tabs if they didn't get serialized properly
    cleaned = cleaned.replace("\\n", "\n")
    cleaned = cleaned.replace("\\r", "\n")
    cleaned = cleaned.replace("\\t", "\t")
    cleaned = cleaned.replace("\\\"", "\"")
    cleaned = cleaned.replace("\\'", "'")
    
    // Convert common HTML entity escapes
    cleaned = cleaned.replace("&amp;", "&")
    cleaned = cleaned.replace("&lt;", "<")
    cleaned = cleaned.replace("&gt;", ">")
    cleaned = cleaned.replace("&quot;", "\"")
    cleaned = cleaned.replace("&#39;", "'")
    
    return cleaned
}

private data class IndexedToken(val index: Int, val type: String)

fun parseInlineMarkdown(text: String, isLight: Boolean): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val textLen = text.length
        while (cursor < textLen) {
            val nextBold = text.indexOf("**", cursor)
            val nextBacktick = text.indexOf("`", cursor)
            val nextDollar = text.indexOf("$", cursor)
            
            // Find next '*' that is NOT double '**'
            var nextItalicAsterisk = -1
            var searchIdx = cursor
            while (searchIdx < textLen) {
                val found = text.indexOf("*", searchIdx)
                if (found == -1) break
                val isDoubleStart = found < textLen - 1 && text[found + 1] == '*'
                val isDoubleEnd = found > 0 && text[found - 1] == '*'
                if (!isDoubleStart && !isDoubleEnd) {
                    nextItalicAsterisk = found
                    break
                }
                searchIdx = if (isDoubleStart) found + 2 else found + 1
            }
            
            val nextItalicUnderscore = text.indexOf("_", cursor)

            // Find the earliest matching markdown token
            val tokens = listOf(
                IndexedToken(nextBold, "bold"),
                IndexedToken(nextBacktick, "backtick"),
                IndexedToken(nextDollar, "dollar"),
                IndexedToken(nextItalicAsterisk, "italic_ast"),
                IndexedToken(nextItalicUnderscore, "italic_und")
            )
            val earliest = tokens.filter { it.index != -1 }.minByOrNull { it.index }

            if (earliest == null) {
                append(text.substring(cursor))
                break
            }

            val tokenIndex = earliest.index
            val tokenType = earliest.type

            if (tokenIndex > cursor) {
                append(text.substring(cursor, tokenIndex))
            }

            when (tokenType) {
                "bold" -> {
                    val boldEnd = text.indexOf("**", tokenIndex + 2)
                    if (boldEnd != -1) {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(tokenIndex + 2, boldEnd))
                        }
                        cursor = boldEnd + 2
                    } else {
                        append("**")
                        cursor = tokenIndex + 2
                    }
                }
                "italic_ast" -> {
                    val italicEnd = text.indexOf("*", tokenIndex + 1)
                    if (italicEnd != -1 && (italicEnd < textLen - 1 && text[italicEnd + 1] != '*' || italicEnd == textLen - 1)) {
                        withStyle(style = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(tokenIndex + 1, italicEnd))
                        }
                        cursor = italicEnd + 1
                    } else {
                        append("*")
                        cursor = tokenIndex + 1
                    }
                }
                "italic_und" -> {
                    val italicEnd = text.indexOf("_", tokenIndex + 1)
                    if (italicEnd != -1) {
                        withStyle(style = SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(tokenIndex + 1, italicEnd))
                        }
                        cursor = italicEnd + 1
                    } else {
                        append("_")
                        cursor = tokenIndex + 1
                    }
                }
                "backtick" -> {
                    val tickEnd = text.indexOf("`", tokenIndex + 1)
                    if (tickEnd != -1) {
                        withStyle(style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = if (isLight) Color(0xFFEEEEEE) else Color(0xFF333333),
                            color = if (isLight) Color(0xFFC7254E) else Color(0xFFF9F2F4)
                        )) {
                            append(text.substring(tokenIndex + 1, tickEnd))
                        }
                        cursor = tickEnd + 1
                    } else {
                        append("`")
                        cursor = tokenIndex + 1
                    }
                }
                "dollar" -> {
                    val dollarEnd = text.indexOf("$", tokenIndex + 1)
                    if (dollarEnd != -1) {
                        withStyle(style = SpanStyle(
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isLight) ForestGreen else SoftSageBg,
                            background = if (isLight) Color(0xFFF0F7F4) else Color(0xFF2C3230)
                        )) {
                            append(text.substring(tokenIndex + 1, dollarEnd))
                        }
                        cursor = dollarEnd + 1
                    } else {
                        append("$")
                        cursor = tokenIndex + 1
                    }
                }
            }
        }
    }
}

data class MarkdownSection(
    val header: MarkdownBlock.Header?,
    val contentBlocks: List<MarkdownBlock>
)

fun groupBlocksIntoSections(blocks: List<MarkdownBlock>): List<MarkdownSection> {
    val sections = mutableListOf<MarkdownSection>()
    var currentHeader: MarkdownBlock.Header? = null
    val currentBlocks = mutableListOf<MarkdownBlock>()

    for (block in blocks) {
        if (block is MarkdownBlock.Header) {
            if (currentHeader != null || currentBlocks.isNotEmpty()) {
                sections.add(MarkdownSection(currentHeader, ArrayList(currentBlocks)))
                currentBlocks.clear()
            }
            currentHeader = block
        } else {
            currentBlocks.add(block)
        }
    }
    if (currentHeader != null || currentBlocks.isNotEmpty()) {
        sections.add(MarkdownSection(currentHeader, ArrayList(currentBlocks)))
    }
    return sections
}

@Composable
fun RenderMarkdownBlock(
    block: MarkdownBlock,
    isLight: Boolean,
    compactMode: Boolean
) {
    when (block) {
        is MarkdownBlock.Header -> {
            val fontSize = when (block.level) {
                1 -> if (compactMode) 15.sp else 18.sp
                2 -> if (compactMode) 14.sp else 16.sp
                else -> if (compactMode) 12.sp else 14.sp
            }
            val leftBorderWidth = when (block.level) {
                1 -> 4.dp
                2 -> 3.dp
                else -> 2.dp
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(fontSize.value.dp * 1.3f)
                        .width(leftBorderWidth)
                        .clip(RoundedCornerShape(leftBorderWidth / 2))
                        .background(if (isLight) ForestGreen else SoftSageBg)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = block.text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    color = if (isLight) ForestGreen else SoftSageBg
                )
            }
        }
        is MarkdownBlock.Paragraph -> {
            if (containsLatex(block.text)) {
                KaTeXWebView(
                    text = block.text,
                    isLight = isLight,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    text = parseInlineMarkdown(block.text, isLight),
                    fontSize = if (compactMode) 12.5.sp else 14.sp,
                    color = if (isLight) CharcoalDark else Color.White,
                    lineHeight = if (compactMode) 17.sp else 20.sp
                )
            }
        }
        is MarkdownBlock.BulletItem -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "◆ ",
                    fontWeight = FontWeight.Bold,
                    color = if (isLight) ForestGreen else SagePillBg,
                    fontSize = if (compactMode) 12.5.sp else 14.sp,
                    modifier = Modifier.padding(top = 1.dp)
                )
                if (containsLatex(block.text)) {
                    KaTeXWebView(
                        text = block.text,
                        isLight = isLight,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = parseInlineMarkdown(block.text, isLight),
                        fontSize = if (compactMode) 12.5.sp else 14.sp,
                        color = if (isLight) CharcoalDark else Color.White,
                        lineHeight = if (compactMode) 17.sp else 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        is MarkdownBlock.CodeBlock -> {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLight) Color(0xFF262A2B) else Color(0xFF1E2224)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, if (isLight) Color(0xFFD3DCD3) else Color(0xFF3C443C))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    block.language?.let { lang ->
                        Text(
                            text = lang.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (compactMode) 11.sp else 13.sp,
                            color = Color(0xFFF3F3E3),
                            lineHeight = if (compactMode) 15.sp else 18.sp
                        )
                    }
                }
            }
        }
        is MarkdownBlock.EquationBlock -> {
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLight) Color(0xFFF5F9F7) else Color(0xFF252B28)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, if (isLight) ForestGreen.copy(alpha = 0.2f) else SoftSageBg.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    KaTeXWebView(
                        text = "$$" + block.formula + "$$",
                        isLight = isLight,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownSectionCard(
    section: MarkdownSection,
    isLight: Boolean,
    compactMode: Boolean
) {
    if (section.header == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            section.contentBlocks.forEach { block ->
                RenderMarkdownBlock(block = block, isLight = isLight, compactMode = compactMode)
            }
        }
    } else {
        var isExpanded by rememberSaveable { mutableStateOf(true) }
        val rotateAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isLight) Color(0xFFF9FBF9) else Color(0xFF1E2421)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .border(
                    width = 1.dp,
                    color = if (isLight) Color(0xFFE2EDE2) else Color(0xFF2E3833),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val fontSize = when (section.header.level) {
                        1 -> if (compactMode) 15.sp else 18.sp
                        2 -> if (compactMode) 14.sp else 16.sp
                        else -> if (compactMode) 13.sp else 14.sp
                    }
                    val leftBorderWidth = when (section.header.level) {
                        1 -> 4.dp
                        2 -> 3.dp
                        else -> 2.dp
                    }

                    Box(
                        modifier = Modifier
                            .height(18.dp)
                            .width(leftBorderWidth)
                            .clip(RoundedCornerShape(leftBorderWidth / 2))
                            .background(if (isLight) ForestGreen else SoftSageBg)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = section.header.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = if (isLight) ForestGreen else SoftSageBg,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = if (isLight) ForestGreen else SoftSageBg,
                        modifier = Modifier
                            .size(24.dp)
                            .rotate(rotateAngle)
                    )
                }

                if (isExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isLight) Color(0xFFE2EDE2) else Color(0xFF2E3833))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        section.contentBlocks.forEach { block ->
                            RenderMarkdownBlock(block = block, isLight = isLight, compactMode = compactMode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarkdownViewer(
    text: String,
    modifier: Modifier = Modifier,
    isScrollable: Boolean = true,
    isLight: Boolean = true,
    compactMode: Boolean = true,
    expandableSections: Boolean = true
) {
    val cleanedText = remember(text) { stripEscapedCharacters(text) }
    val blocks = remember(cleanedText) { parseMarkdown(cleanedText) }
    val columnModifier = if (isScrollable) {
        modifier.verticalScroll(rememberScrollState())
    } else {
        modifier
    }

    if (expandableSections) {
        val sections = remember(blocks) { groupBlocksIntoSections(blocks) }
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { section ->
                MarkdownSectionCard(section = section, isLight = isLight, compactMode = compactMode)
            }
        }
    } else {
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            blocks.forEach { block ->
                RenderMarkdownBlock(block = block, isLight = isLight, compactMode = compactMode)
            }
        }
    }
}

@Composable
fun PaystackWebView(
    url: String,
    expectedReference: String,
    onSuccess: (reference: String) -> Unit,
    onCancel: () -> Unit
) {
    var successTriggered by remember { mutableStateOf(false) }
    val currentOnSuccess by rememberUpdatedState(onSuccess)

    val handleSuccess: (String) -> Unit = { reference ->
        if (!successTriggered) {
            successTriggered = true
            currentOnSuccess(reference)
        }
    }

    fun isSuccessUrl(rawUrl: String): Boolean {
        // Guard: Never count the initial checkout authorization URL itself as a success redirect!
        if (rawUrl.equals(url, ignoreCase = true) || rawUrl.lowercase().trimEnd('/') == url.lowercase().trimEnd('/')) {
            return false
        }
        val cleanUrl = rawUrl.lowercase()
        
        // Also ensure core checkout launch domains themselves are ignored unless they contain success callbacks
        if (cleanUrl.startsWith("https://checkout.paystack.com") && !cleanUrl.contains("callback") && !cleanUrl.contains("success") && !cleanUrl.contains("complete")) {
            return false
        }
        
        return cleanUrl.contains("studybuddy://payment/complete") ||
               cleanUrl.contains("https://standard.paystack.co/close") ||
               cleanUrl.contains("paystack.co/close") ||
               cleanUrl.contains("paystack.com/close") ||
               cleanUrl.startsWith("studybuddy://") ||
               cleanUrl.contains("payment/complete") ||
               cleanUrl.contains("payment-complete") ||
               cleanUrl.contains("payment_complete") ||
               cleanUrl.contains("payment/success") ||
               (cleanUrl.contains("paystack.com") && cleanUrl.contains("success")) ||
               (cleanUrl.contains("paystack.co") && cleanUrl.contains("success")) ||
               (cleanUrl.contains("paystack") && cleanUrl.contains("callback")) ||
               cleanUrl.contains("trxref=") ||
               cleanUrl.contains("reference=")
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val currentUrl = request?.url?.toString() ?: ""
                        android.util.Log.d("PaystackWebView", "shouldOverrideUrlLoading: $currentUrl")
                        
                        if (isSuccessUrl(currentUrl)) {
                            val uri = Uri.parse(currentUrl)
                            val reference = uri.getQueryParameter("reference") ?: uri.getQueryParameter("trxref") ?: ""
                            val finalRef = if (reference.isNotEmpty()) reference else expectedReference
                            handleSuccess(finalRef)
                            return true
                        }
                        return false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        val currentUrl = url ?: ""
                        android.util.Log.d("PaystackWebView", "shouldOverrideUrlLoading (deprecated): $currentUrl")
                        
                        if (isSuccessUrl(currentUrl)) {
                            val uri = Uri.parse(currentUrl)
                            val reference = uri.getQueryParameter("reference") ?: uri.getQueryParameter("trxref") ?: ""
                            val finalRef = if (reference.isNotEmpty()) reference else expectedReference
                            handleSuccess(finalRef)
                            return true
                        }
                        return false
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        val currentUrl = url ?: ""
                        android.util.Log.d("PaystackWebView", "onPageStarted: $currentUrl")
                        
                        if (isSuccessUrl(currentUrl)) {
                            val uri = Uri.parse(currentUrl)
                            val reference = uri.getQueryParameter("reference") ?: uri.getQueryParameter("trxref") ?: ""
                            val finalRef = if (reference.isNotEmpty()) reference else expectedReference
                            handleSuccess(finalRef)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val currentUrl = url ?: ""
                        android.util.Log.d("PaystackWebView", "onPageFinished: $currentUrl")
                        
                        if (isSuccessUrl(currentUrl)) {
                            val uri = Uri.parse(currentUrl)
                            val reference = uri.getQueryParameter("reference") ?: uri.getQueryParameter("trxref") ?: ""
                            val finalRef = if (reference.isNotEmpty()) reference else expectedReference
                            handleSuccess(finalRef)
                        }
                    }
                }
            }
        },
        update = { webView ->
            webView.loadUrl(url)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun PaystackPaymentDialog(
    viewModel: StudyBuddyViewModel,
    onDismiss: () -> Unit
) {
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val paystackState by viewModel.paystackState.collectAsStateWithLifecycle()

    var userEmail by remember { mutableStateOf("student@miva.edu.ng") }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            showSuccessAnimation = true
            delay(1800)
            showSuccessAnimation = false
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (paystackState !is PaystackInitUiState.Loading && !showSuccessAnimation) {
                viewModel.resetPaystackState()
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = paystackState !is PaystackInitUiState.Loading && !showSuccessAnimation,
            dismissOnClickOutside = paystackState !is PaystackInitUiState.Loading && !showSuccessAnimation
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
                .border(0.5.dp, OutlineLight, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp)
        ) {
            if (showSuccessAnimation) {
                // Subtle green checkmark animation success screen
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        var animStart by remember { mutableStateOf(false) }
                        val scale by animateFloatAsState(
                            targetValue = if (animStart) 1.1f else 0.8f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 150f)
                        )

                        LaunchedEffect(true) {
                            animStart = true
                        }

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .scale(scale),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color(0xFF10B981).copy(alpha = 0.15f),
                                    radius = size.minDimension / 2f
                                )
                                drawCircle(
                                    color = Color(0xFF10B981),
                                    radius = size.minDimension / 2.2f,
                                    style = Stroke(width = 4.dp.toPx())
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Success",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(52.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Premium Unlocked Successfully!",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CharcoalDark,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Welcome to StudyBuddy Pro",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SageTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (paystackState is PaystackInitUiState.Success) "💳 Secure Checkout" else "🔒 Unlock Premium",
                        style = MaterialTheme.typography.titleMedium,
                        color = CharcoalDark,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            viewModel.resetPaystackState()
                            onDismiss()
                        },
                        enabled = paystackState !is PaystackInitUiState.Loading
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = SageTextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = OutlineLight)
                Spacer(modifier = Modifier.height(16.dp))

                when (val state = paystackState) {
                    is PaystackInitUiState.Success -> {
                        // Crucial fix: No verticalScroll parent container. Let the webview capture scroll/touches fully!
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Text(
                                text = "Complete payment securely. Tap inputs to enter checkout details:",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageTextMuted,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.5.dp, OutlineMedium, RoundedCornerShape(16.dp))
                                    .background(Color.White)
                            ) {
                                PaystackWebView(
                                    url = state.authorizationUrl,
                                    expectedReference = state.reference,
                                    onSuccess = { reference ->
                                        viewModel.verifyPaystackPayment(reference)
                                    },
                                    onCancel = {
                                        viewModel.resetPaystackState()
                                    }
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = {
                                    viewModel.verifyPaystackPayment(state.reference)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "I Have Paid (Confirm Payment)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ref: ${state.reference}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        color = SageTextMuted
                                    )
                                )
                                TextButton(
                                    onClick = { viewModel.resetPaystackState() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red.copy(alpha = 0.8f))
                                ) {
                                    Text("Cancel / Go Back", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                    else -> {
                        // Idle, Error, or Loading: Info can scroll safely
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (state is PaystackInitUiState.Error) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFFEE2E2))
                                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "❌ Error: ${state.message}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            if (state is PaystackInitUiState.Loading) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = ForestGreen, strokeWidth = 4.dp)
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CharcoalDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Contacting database secure server...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SageTextMuted
                                    )
                                }
                            } else {
                                Text(
                                    text = "Unlock academic superiority with tools designed to crack complex university examinations:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SageTextMuted,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val perks = listOf(
                                    "⚡" to "Unlimited PDF Syllabus / Notes Uploads",
                                    "🔥" to "60-Question Realistic Exam Simulators",
                                    "🧠" to "Analogy-Driven Interactive Feynman Tutor",
                                    "💡" to "Mad Study Tips and Trick Formula Alerts"
                                )

                                perks.forEach { (emoji, desc) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(text = emoji, fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CharcoalDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                var referralCodeInput by remember { mutableStateOf("") }
                                var referralApplied by remember { mutableStateOf(false) }
                                var referralStatusMsg by remember { mutableStateOf("") }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3FBF7)),
                                    border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "ACCESS UNLOCKED FEE",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ForestGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (referralApplied) "₦800" else "₦1,000",
                                            style = MaterialTheme.typography.headlineLarge,
                                            color = ForestGreen,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (referralApplied) "🎉 Referral applied! ₦200 Discount Saved" else "Lifetime updates • Offline fallback integration",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (referralApplied) FontWeight.Bold else FontWeight.Normal),
                                            color = if (referralApplied) Color(0xFF10B981) else SageTextMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Referral Code Input Area
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Have a MIVA scholar's Referral Code?",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SageTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = referralCodeInput,
                                            onValueChange = { referralCodeInput = it },
                                            placeholder = { Text("E.g. XXXXXX", fontSize = 12.sp, color = SageTextMuted) },
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = CharcoalDark),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                unfocusedBorderColor = OutlineMedium,
                                                focusedBorderColor = ForestGreen,
                                                unfocusedContainerColor = Color.White,
                                                focusedContainerColor = Color.White
                                            ),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f).testTag("referral_input_field")
                                        )

                                        Button(
                                            onClick = {
                                                if (referralCodeInput.trim().isNotEmpty()) {
                                                    viewModel.checkReferralCodeOnServer(referralCodeInput) { isValid ->
                                                        if (isValid) {
                                                            referralApplied = true
                                                            referralStatusMsg = "✅ Code applied: ₦200 discount!"
                                                        } else {
                                                            referralApplied = false
                                                            referralStatusMsg = "❌ Code invalid. Ensure it is exactly 6 characters."
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("apply_referral_button")
                                        ) {
                                            Text("Apply", color = Color.White)
                                        }
                                    }

                                    if (referralStatusMsg.isNotEmpty()) {
                                        Text(
                                            text = referralStatusMsg,
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                            color = if (referralApplied) Color(0xFF10B981) else Color.Red,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                OutlinedTextField(
                                    value = userEmail,
                                    onValueChange = { userEmail = it },
                                    label = { Text("Billing Email Address", color = SageTextMuted) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = CharcoalDark),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedBorderColor = OutlineMedium,
                                        focusedBorderColor = ForestGreen,
                                        unfocusedContainerColor = Color.White,
                                        focusedContainerColor = Color.White
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                Button(
                                    onClick = {
                                        viewModel.initializePaystackPayment(userEmail, referralApplied = referralApplied)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(52.dp)
                                ) {
                                    Text("💳 Secure Pay with Paystack", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun OnboardingScreen(
    onDismiss: () -> Unit
) {
    var currentSlide by remember { mutableStateOf(0) }
    val slides = listOf(
        Triple(
            "Upload your course PDF 📎",
            "Quickly upload syllabus papers, lecture notes, or PDF study guides to generate personalized tools.",
            "📁"
        ),
        Triple(
            "Learn what will come out in your exam 🧠",
            "Find and explore high-probability exam target rules and clear conceptual gaps.",
            "💡"
        ),
        Triple(
            "Simulate your MIVA exam & pass. 🏆",
            "Answer simulated MCQ questions styled directly from real MIVA academic curricula.",
            "🎯"
        )
    )

    Surface(
        modifier = Modifier.fillMaxSize().testTag("onboarding_screen"),
        color = Color(0xFF0F0F0F) // Premium Cosmic Dark theme for onboarding
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row (Skip button)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("onboarding_skip_button")
                ) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Central Area (Active slide info)
            val slide = slides[currentSlide]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = slide.third,
                    fontSize = 80.sp,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Text(
                    text = slide.first,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = slide.second,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    lineHeight = 24.sp
                )
            }

            // Bottom Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dots indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(if (index == currentSlide) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == currentSlide) Color(0xFF818CF8) else Color.White.copy(alpha = 0.3f)
                                )
                        )
                    }
                }

                // Next/Get Started button
                Button(
                    onClick = {
                        if (currentSlide < 2) {
                            currentSlide += 1
                        } else {
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF818CF8)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("onboarding_next_button")
                ) {
                    Text(
                        text = if (currentSlide == 2) "Get Started" else "Next",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
