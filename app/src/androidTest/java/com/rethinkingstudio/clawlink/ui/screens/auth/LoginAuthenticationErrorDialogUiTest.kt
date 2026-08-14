package com.rethinkingstudio.clawlink.ui.screens.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.rethinkingstudio.clawlink.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LoginAuthenticationErrorDialogUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun authenticationFailureIsShownInDialogAndCanBeDismissed() {
        val message = "Email or password is incorrect. 4 attempts remaining."
        val confirmText = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(R.string.common_action_ok)
        var dismissed = false

        composeRule.setContent {
            MaterialTheme {
                LoginAuthenticationErrorDialog(
                    message = message,
                    isRegisterMode = false,
                    waitingForVerification = false,
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNode(isDialog()).assertIsDisplayed()
        composeRule.onNodeWithText(message).assertIsDisplayed()
        composeRule.onNodeWithText(confirmText).performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }
}
