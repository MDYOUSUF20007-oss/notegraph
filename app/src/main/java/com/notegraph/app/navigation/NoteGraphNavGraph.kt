package com.notegraph.app.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.notegraph.app.ui.editor.EditorScreen
import com.notegraph.app.ui.editor.EditorViewModel
import com.notegraph.app.ui.graph.GraphScreen
import com.notegraph.app.ui.home.HomeScreen
import com.notegraph.app.ui.home.HomeViewModel
import com.notegraph.app.ui.search.SearchScreen
import com.notegraph.app.ui.settings.SettingsScreen
import com.notegraph.app.ui.tags.TagsScreen

@Composable
fun NoteGraphNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onNoteClick = { noteId -> navController.navigate("editor/\$noteId") },
                onNavigateSearch = { navController.navigate("search") },
                onNavigateGraph = { navController.navigate("graph") },
                onNavigateTags = { navController.navigate("tags") },
                onNavigateSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "editor/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            val viewModel: EditorViewModel = hiltViewModel()
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable("graph") {
            GraphScreen(onBack = { navController.popBackStack() })
        }
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId -> navController.navigate("editor/\$noteId") }
            )
        }
        composable("tags") {
            TagsScreen(onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}