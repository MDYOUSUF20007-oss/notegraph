package com.notegraph.app.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Knowledge Graph") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(color = Color.Gray, radius = 24f, center = center)
            drawCircle(color = Color.LightGray, radius = 16f, center = center + Offset(150f, 150f))
            drawLine(color = Color.DarkGray, start = center, end = center + Offset(150f, 150f), strokeWidth = 4f)
        }
    }
}