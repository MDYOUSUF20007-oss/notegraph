package com.notegraph.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.notegraph.app.ui.reader.ReaderScreen
import com.notegraph.app.ui.reader.ReaderViewModel
import com.notegraph.app.ui.search.SearchScreen
import com.notegraph.app.ui.settings.SettingsScreen
import com.notegraph.app.ui.tags.TagsScreen
import com.notegraph.app.ui.tutorial.TutorialScreen

@Composable
fun NoteGraphNavGraph(
    incomingPdfUri: Uri? = null,
    onIncomingPdfHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            val viewModel: HomeViewModel = hiltViewModel()

            LaunchedEffect(incomingPdfUri) {
                val uri = incomingPdfUri
                if (uri != null) {
                    onIncomingPdfHandled()
                    viewModel.importPdfAsNewNote(uri) { createdId ->
                        if (createdId != null) {
                            navController.navigate("editor/$createdId")
                        }
                    }
                }
            }

            HomeScreen(
                viewModel = viewModel,
                onNoteClick = { noteId -> navController.navigate("editor/$noteId") },
                onNavigateSearch = { navController.navigate("search") },
                onNavigateGraph = { navController.navigate("graph") },
                onNavigateTags = { navController.navigate("tags") },
                onNavigateSettings = { navController.navigate("settings") },
                onNavigateReader = { navController.navigate("reader") },
                onNavigateTutorial = { section ->
                    val targetSection = section ?: "ALL"
                    navController.navigate("tutorial?section=$targetSection")
                }
            )
        }
        composable(
            route = "editor/{noteId}",
            arguments = listOf(navArgument("noteId") { type = NavType.StringType })
        ) {
            val viewModel: EditorViewModel = hiltViewModel()
            EditorScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateReader = { id ->
                    if (id != null) navController.navigate("reader?noteId=$id")
                    else navController.navigate("reader")
                },
                onNoteClick = { targetId -> navController.navigate("editor/$targetId") }
            )
        }
        composable("graph") {
            GraphScreen(
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId -> navController.navigate("editor/$noteId") },
                onNavigateTutorial = { navController.navigate("tutorial?section=GRAPH") }
            )
        }
        composable("search") {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId -> navController.navigate("editor/$noteId") }
            )
        }
        composable("tags") {
            TagsScreen(
                onBack = { navController.popBackStack() },
                onNoteClick = { noteId -> navController.navigate("editor/$noteId") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onNavigateTutorial = { navController.navigate("tutorial?section=ALL") }
            )
        }
        composable(
            route = "tutorial?section={section}",
            arguments = listOf(navArgument("section") {
                type = NavType.StringType
                nullable = true
                defaultValue = "ALL"
            })
        ) { backStackEntry ->
            val section = backStackEntry.arguments?.getString("section")
            TutorialScreen(
                initialSection = section,
                onBack = { navController.popBackStack() },
                onOpenGraph = {
                    val popped = navController.popBackStack("graph", inclusive = false)
                    if (!popped) {
                        navController.navigate("graph") { launchSingleTop = true }
                    }
                }
            )
        }
        composable(
            route = "reader?noteId={noteId}",
            arguments = listOf(navArgument("noteId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getString("noteId")
            val viewModel: ReaderViewModel = hiltViewModel()
            ReaderScreen(
                onBack = { navController.popBackStack() },
                onEditNote = { id -> navController.navigate("editor/$id") },
                initialNoteId = noteId,
                viewModel = viewModel
            )
        }
        composable("reader") {
            val viewModel: ReaderViewModel = hiltViewModel()
            ReaderScreen(
                onBack = { navController.popBackStack() },
                onEditNote = { id -> navController.navigate("editor/$id") },
                initialNoteId = null,
                viewModel = viewModel
            )
        }
    }
}
