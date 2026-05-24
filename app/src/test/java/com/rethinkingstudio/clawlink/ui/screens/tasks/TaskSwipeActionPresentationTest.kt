package com.rethinkingstudio.clawlink.ui.screens.tasks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSwipeActionPresentationTest {
    @Test
    fun leftDragRevealsDeleteWhenTaskCanBeManaged() {
        assertTrue(
            TaskSwipeActionPresentation.shouldRevealDelete(
                canManageTasks = true,
                isActionDisabled = false,
                dragAmount = -12f
            )
        )
    }

    @Test
    fun leftDragDoesNotRevealDeleteWhenTaskActionsAreDisabled() {
        assertFalse(
            TaskSwipeActionPresentation.shouldRevealDelete(
                canManageTasks = true,
                isActionDisabled = true,
                dragAmount = -12f
            )
        )
    }

    @Test
    fun rightDragHidesDelete() {
        assertTrue(TaskSwipeActionPresentation.shouldHideDelete(dragAmount = 12f))
        assertFalse(TaskSwipeActionPresentation.shouldHideDelete(dragAmount = -12f))
    }
}
