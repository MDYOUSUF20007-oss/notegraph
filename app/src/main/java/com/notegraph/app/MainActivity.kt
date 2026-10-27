package com.notegraph.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.notegraph.app.navigation.NoteGraphNavGraph
import com.notegraph.app.ui.theme.NoteGraphTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NoteGraphTheme {
                NoteGraphNavGraph()
            }
        }
    }
}