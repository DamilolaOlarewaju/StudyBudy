package com.example.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.PdfParser
import com.example.AnalyticsHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun PdfDropZone(
    onPdfParsed: (title: String, extractedText: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isDragging by remember { mutableStateOf(false) }
    var isParsing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Setup file picker launcher for selecting custom course notes PDFs
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            processPdfUri(context, uri, coroutineScope, 
                onStart = { 
                    isParsing = true
                    errorMessage = null
                },
                onSuccess = { title, text ->
                    isParsing = false
                    onPdfParsed(title, text)
                },
                onFailure = { err ->
                    isParsing = false
                    errorMessage = err
                }
            )
        }
    }

    // Setup Drag-and-Drop target
    val dndTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                // No-op
            }
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }
            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }
            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val androidEvent = event.toAndroidDragEvent()
                val clipData = androidEvent.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    val uri = clipData.getItemAt(0).uri
                    if (uri != null) {
                        processPdfUri(context, uri, coroutineScope,
                            onStart = {
                                isParsing = true
                                errorMessage = null
                            },
                            onSuccess = { title, text ->
                                isParsing = false
                                onPdfParsed(title, text)
                            },
                            onFailure = { err ->
                                isParsing = false
                                errorMessage = err
                            }
                        )
                        return true
                    }
                }
                return false
            }
        }
    }

    // Border and container colors depending on action state
    val strokeColor = when {
        isDragging -> ForestGreen
        isParsing -> AccentGold
        errorMessage != null -> Color.Red
        else -> ForestGreen.copy(alpha = 0.4f) // Premium Indigo accent dashed border
    }
    
    val containerBg = when {
        isDragging -> ForestGreen.copy(alpha = 0.05f)
        errorMessage != null -> Color.Red.copy(alpha = 0.02f)
        else -> SoftSageBg
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .dashedBorder(
                color = strokeColor,
                strokeWidth = 2.dp,
                dashWidth = 8.dp,
                gapWidth = 6.dp,
                cornerRadius = 16.dp
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { true },
                target = dndTarget
            )
            .clickable(enabled = !isParsing) {
                filePickerLauncher.launch("application/pdf")
            }
            .testTag("pdf_drop_zone"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            if (isParsing) {
                CircularProgressIndicator(
                    color = ForestGreen,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Parsing Course PDF & Syllabus...",
                    style = MaterialTheme.typography.titleSmall,
                    color = CharcoalDark,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Running local CPU text extraction, please wait",
                    style = MaterialTheme.typography.bodySmall,
                    color = SageTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Icon(
                    imageVector = if (errorMessage != null) Icons.Default.Warning else Icons.Default.Add,
                    contentDescription = "Upload PDF",
                    tint = if (errorMessage != null) Color.Red.copy(alpha = 0.8f) else ForestGreen,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (errorMessage != null) "Failed to parse PDF!" else "Drop Syllabus or Notes PDF here",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (errorMessage != null) Color.Red else CharcoalDark,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (errorMessage != null) errorMessage!! else "or click / tap to select from files",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (errorMessage != null) Color.Red.copy(alpha = 0.7f) else SageTextMuted,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Standard PDF documents under 20MB are fully supported",
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateTextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

private fun processPdfUri(
    context: Context,
    uri: Uri,
    scope: kotlinx.coroutines.CoroutineScope,
    onStart: () -> Unit,
    onSuccess: (String, String) -> Unit,
    onFailure: (String) -> Unit
) {
    onStart()
    scope.launch {
        try {
            val title = PdfParser.getFileName(context, uri)
            val extractedText = PdfParser.extractTextFromUri(context, uri)
            if (extractedText.isBlank()) {
                onFailure("Could not extract any text from this PDF. Is it scanned or empty?")
            } else {
                val params = android.os.Bundle().apply {
                    putString("pdf_title", title)
                    putInt("text_character_count", extractedText.length)
                }
                AnalyticsHelper.logEvent("pdf_uploaded", params)
                onSuccess("📚 " + title.trim(), extractedText)
            }
        } catch (e: Exception) {
            Log.e("PdfDropZone", "Parsing failed", e)
            onFailure("Failed to extract PDF text: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}

// Custom drawer extension for dashed borders
fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    dashWidth: Dp,
    gapWidth: Dp,
    cornerRadius: Dp
) = drawBehind {
    val stroke = Stroke(
        width = strokeWidth.toPx(),
        pathEffect = PathEffect.dashPathEffect(
            floatArrayOf(dashWidth.toPx(), gapWidth.toPx()),
            0f
        )
    )
    val radius = cornerRadius.toPx()
    drawRoundRect(
        color = color,
        style = stroke,
        cornerRadius = CornerRadius(radius, radius)
    )
}
