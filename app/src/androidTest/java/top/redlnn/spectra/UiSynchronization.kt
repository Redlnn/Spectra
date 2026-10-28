package top.redlnn.spectra

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag

/** Activity RESUMED can precede the first Compose hierarchy during a window transition. */
internal fun ComposeTestRule.waitForSettingsScreen() {
    waitUntil(5000) {
        onAllNodesWithTag("settings-pager")
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .isNotEmpty()
    }
}
