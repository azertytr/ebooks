package com.ebooks.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ebooks.reader.ui.screens.LibraryScreen
import com.ebooks.reader.ui.screens.PdfReaderScreen
import com.ebooks.reader.ui.screens.ReaderScreen
import com.ebooks.reader.ui.theme.EbookReaderTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EbookReaderTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = "library"
                    ) {
                        composable("library") {
                            LibraryScreen(
                                onOpenBook = { bookId, fileType ->
                                    if (fileType == "pdf") {
                                        navController.navigate("pdf_reader/$bookId")
                                    } else {
                                        navController.navigate("reader/$bookId")
                                    }
                                }
                            )
                        }

                        composable(
                            route = "reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            ReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "pdf_reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                            PdfReaderScreen(
                                bookId = bookId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
