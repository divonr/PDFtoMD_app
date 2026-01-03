package com.divonr.pdftomd.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class PdfRepository(private val context: Context) {

    suspend fun loadPdfBytes(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readBytes()
        } ?: throw Exception("Unable to read PDF")
    }

    suspend fun saveTextToFile(content: String, filename: String): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, filename)
        FileOutputStream(file).use { output ->
            output.write(content.toByteArray())
        }
        file
    }
    
    // Copy file to internal storage
    suspend fun copyFileToInternalStorage(uri: Uri, filename: String): File = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, filename)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Unable to copy file")
        file
    }

    suspend fun copyPdfToInternalStorage(uri: Uri, filename: String): File {
        return copyFileToInternalStorage(uri, filename)
    }
    
    fun getInternalPdfFile(filename: String): File {
        return File(context.filesDir, filename)
    }

    fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }
}
