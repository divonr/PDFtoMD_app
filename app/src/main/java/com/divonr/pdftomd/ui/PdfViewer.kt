package com.divonr.pdftomd.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfViewer(
    pdfFile: File,
    modifier: Modifier = Modifier
) {
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pdfFile) {
        isLoading = true
        pages = withContext(Dispatchers.IO) {
            renderPdfPages(pdfFile)
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            pages.forEach { it.recycle() }
        }
    }

    Box(modifier = modifier) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(pages) { _, bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF Page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale
                            ),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

private fun renderPdfPages(file: File): List<Bitmap> {
    val pages = mutableListOf<Bitmap>()
    try {
        val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)

        for (i in 0 until pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(i)
            val scale = 2f // Render at 2x for better quality
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bitmap)
            page.close()
        }

        pdfRenderer.close()
        fileDescriptor.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return pages
}
