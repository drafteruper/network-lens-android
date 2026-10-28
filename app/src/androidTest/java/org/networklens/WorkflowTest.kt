package org.networklens

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test

class WorkflowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun createEventAddAttendeeConfirmAndReviewHistory() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.deleteDatabase("network-lens.db")
        val vm = LensViewModel(app)
        compose.setContent { MaterialTheme { LensApp(vm) } }
        compose.onNodeWithText("Create event").performClick()
        compose.onNodeWithText("Event name").performTextInput("Fictional Test Event")
        compose.onNodeWithText("Create", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Add attendee").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Add attendee").performClick()
        compose.onNodeWithText("Full name").performTextInput("Alex Example")
        compose.onNodeWithText("Company").performTextInput("Example Ventures")
        compose.onNodeWithText("Role").performTextInput("Partner")
        compose.onNodeWithText("Add", useUnmergedTree = true).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Alex Example").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alex Example").performClick()
        compose.onNodeWithText("Confirm & save").performClick()
        compose.onNodeWithText("History").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("1 saved", substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Alex Example").assertIsDisplayed()
        compose.onNodeWithText("Privacy").performClick()
        compose.onNodeWithText("Private by design").assertIsDisplayed()
    }
}
