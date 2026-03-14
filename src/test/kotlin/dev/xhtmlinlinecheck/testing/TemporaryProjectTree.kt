package dev.xhtmlinlinecheck.testing

import java.nio.file.Files
import java.nio.file.Path

class TemporaryProjectTree(private val root: Path) {
    fun write(relativePath: String, content: String): Path {
        val path = path(relativePath)
        Files.createDirectories(path.parent)
        Files.writeString(path, content.trimIndent())
        return path
    }

    fun path(relativePath: String): Path = root.resolve(relativePath)
}
