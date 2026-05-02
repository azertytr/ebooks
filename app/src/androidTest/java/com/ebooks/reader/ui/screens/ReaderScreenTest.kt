package com.ebooks.reader.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for ReaderScreen.
 * Verifies core reading functionality: navigation, search, and settings.
 */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun readerScreenDisplaysBackButton() {
        var backPressed = false
        composeTestRule.setContent {
            ReaderScreen(
                bookId = "test-book-id",
                onBack = { backPressed = true }
            )
        }

        composeTestRule
            .onNodeWithContentDescription("Back")
            .performClick()

        assert(backPressed) { "Back button should trigger onBack callback" }
    }

    @Test
    fun searchToggleShowsSearchBar() {
        composeTestRule.setContent {
            ReaderScreen(
                bookId = "test-book-id",
                onBack = {}
            )
        }

        // Tap search button (should be in top bar)
        composeTestRule
            .onNodeWithContentDescription("Search")
            .performClick()

        // Verify search field appears
        composeTestRule
            .onNodeWithTag("SearchTextField", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun settingsButtonShowsBottomSheet() {
        composeTestRule.setContent {
            ReaderScreen(
                bookId = "test-book-id",
                onBack = {}
            )
        }

        // Tap settings button
        composeTestRule
            .onNodeWithContentDescription("Settings")
            .performClick()

        // Verify settings sheet appears
        composeTestRule
            .onNodeWithText("Theme", substring = true)
            .assertExists()
    }
}
