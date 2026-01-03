package com.divonr.pdftomd.data

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository {

    suspend fun generateMarkdownFromPdf(apiKey: String, pdfBytes: ByteArray, mimeType: String = "application/pdf"): String = withContext(Dispatchers.IO) {
        val generativeModel = GenerativeModel(
            modelName = "gemini-2.0-flash-exp", // Updated to the latest flash model as requested or similar powerful model
            apiKey = apiKey
        )

        // Ideally we pass the PDF BLOB directly.
        // Gemini API supports PDF input directly via the `parts` if it's supported by the client lib.
        // The current Android client might support it as InlineData.
        
        val inputContent = content {
            blob(mimeType, pdfBytes)
            text("Analyze this PDF and convert it to Markdown format. Be as precise as possible. Do not include any introductory or concluding text, just the Markdown content.")
        }

        val response = generativeModel.generateContent(inputContent)
        response.text ?: ""
    }
}
