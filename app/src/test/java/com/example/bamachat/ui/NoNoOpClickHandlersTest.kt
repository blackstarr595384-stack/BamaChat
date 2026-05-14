package com.example.bamachat.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class NoNoOpClickHandlersTest {

    @Test
    fun uiSources_shouldNotContainEmptyOnClickHandlers() {
        val projectRoot = locateProjectRoot()
        val uiSourceRoot = projectRoot.resolve("app/src/main/java/com/example/bamachat/ui")
        assertTrue("UI source root not found: $uiSourceRoot", Files.exists(uiSourceRoot))

        val noOpPattern = Regex("""onClick\s*=\s*\{\s*\}""")
        val offenders = mutableListOf<String>()

        Files.walk(uiSourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { file ->
                    val lines = Files.readAllLines(file, StandardCharsets.UTF_8)
                    lines.forEachIndexed { index, line ->
                        if (noOpPattern.containsMatchIn(line)) {
                            val relativeFile = projectRoot.relativize(file).toString().replace('\\', '/')
                            offenders += "$relativeFile:${index + 1}: ${line.trim()}"
                        }
                    }
                }
        }

        assertTrue(
            "Found empty click handlers:\n${offenders.joinToString("\n")}",
            offenders.isEmpty()
        )
    }

    private fun locateProjectRoot(): Path {
        var current = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        repeat(8) {
            if (Files.exists(current.resolve("app/src/main/java"))) return current
            current = current.parent ?: return@repeat
        }
        error("Could not locate project root from: ${System.getProperty("user.dir")}")
    }
}
