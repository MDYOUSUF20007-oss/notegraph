package com.notegraph.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.notegraph.app.navigation.NoteGraphNavGraph
import com.notegraph.app.ui.theme.NoteGraphTheme
import com.notegraph.app.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var incomingPdfUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        incomingPdfUri = extractPdfUri(intent)
        setContent {
            NoteGraphTheme {
                NoteGraphNavGraph(
                    incomingPdfUri = incomingPdfUri,
                    onIncomingPdfHandled = { incomingPdfUri = null }
                )
            }
        }
    }

    private fun extractPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }
}
