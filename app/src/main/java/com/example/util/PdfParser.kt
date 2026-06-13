package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object PdfParser {

    /**
     * Extracts all text from a PDF file Uri.
     * This function runs on Dispatchers.IO to avoid blocking the main thread.
     */
    suspend fun extractTextFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val stringBuilder = StringBuilder()
        var inputStream: InputStream? = null
        var reader: PdfReader? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw IllegalArgumentException("Could not open input stream for selected PDF")
            }
            reader = PdfReader(inputStream)
            val totalPages = reader.numberOfPages
            for (i in 1..totalPages) {
                val pageText = PdfTextExtractor.getTextFromPage(reader, i)
                if (!pageText.isNullOrBlank()) {
                    stringBuilder.append(pageText).append("\n")
                }
            }
        } catch (e: Exception) {
            Log.e("PdfParser", "Error extracting text from PDF", e)
            throw e
        } finally {
            try {
                reader?.close()
            } catch (ignored: Exception) {}
            try {
                inputStream?.close()
            } catch (ignored: Exception) {}
        }
        stringBuilder.toString()
    }

    /**
     * Gets the display name of a file from its Uri.
     */
    fun getFileName(context: Context, uri: Uri): String {
        var name = "Syllabus Notes"
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = it.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PdfParser", "Failed to retrieve filename from Uri", e)
        }
        // Remove .pdf extension if present for a cleaner study guide title
        if (name.endsWith(".pdf", ignoreCase = true)) {
            name = name.substring(0, name.length - 4)
        }
        return name
    }
}
