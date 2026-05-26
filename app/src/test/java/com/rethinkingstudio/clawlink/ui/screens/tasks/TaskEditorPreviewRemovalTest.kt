package com.rethinkingstudio.clawlink.ui.screens.tasks

import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class TaskEditorPreviewRemovalTest {
    @Test
    fun taskEditorDoesNotRenderSchedulePreview() {
        val source = taskEditorSourcePath().toFile().readText()

        assertFalse(source.contains("FieldLabel(choose(\"Preview\", \"预览\"))"))
        assertFalse(source.contains("previewTask("))
        assertFalse(source.contains("id = \"preview\""))
    }

    private fun taskEditorSourcePath(): Path {
        var cursor: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor != null) {
            val candidate = cursor.resolve(
                "app/src/main/java/com/rethinkingstudio/clawlink/ui/screens/tasks/components/TaskEditorComponents.kt"
            )
            if (Files.exists(candidate)) return candidate
            cursor = cursor.parent
        }
        error("Could not locate TaskEditorComponents.kt from ${System.getProperty("user.dir")}")
    }
}
