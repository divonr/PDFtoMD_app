package com.divonr.pdftomd

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.divonr.pdftomd.data.GeminiRepository
import com.divonr.pdftomd.data.PdfRepository
import com.divonr.pdftomd.data.PreferenceManager
import com.divonr.pdftomd.ui.AppScreen
import com.divonr.pdftomd.ui.MainViewModel
import com.divonr.pdftomd.ui.MainViewModelFactory
import com.divonr.pdftomd.ui.theme.PDFtoMDTheme

class MainActivity : ComponentActivity() {
    
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefManager = PreferenceManager(this)
        val geminiRepo = GeminiRepository()
        val pdfRepo = PdfRepository(this)
        val factory = MainViewModelFactory(prefManager, geminiRepo, pdfRepo)
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        handleIntent(intent)

        setContent {
            PDFtoMDTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AppScreen(viewModel)
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { viewModel.loadPdf(it) }
        }
    }
}