package dev.xhtmlinlinecheck.testing

import java.nio.file.Files
import java.nio.file.Path

data class FixtureScenario(
    val name: String,
    val directory: Path,
) {
    val oldDir: Path = directory.resolve("old")
    val newDir: Path = directory.resolve("new")
    val oldRoot: Path = oldDir.resolve("root.xhtml")
    val newRoot: Path = newDir.resolve("root.xhtml")
    val expectedJson: Path = directory.resolve("expected.json")
    val expectedText: Path = directory.resolve("expected.txt")
    val notes: Path = directory.resolve("notes.md")
}

object FixtureScenarios {
    val repositoryRoot: Path = Path.of("").toAbsolutePath().normalize()
    val fixturesRoot: Path = repositoryRoot.resolve("fixtures")

    fun scenario(relativePath: String): FixtureScenario {
        val directory = fixturesRoot.resolve(relativePath).normalize()
        require(Files.isDirectory(directory)) {
            "Fixture directory does not exist: $directory"
        }
        return FixtureScenario(
            name = relativePath.replace('\\', '/'),
            directory = directory,
        )
    }
}
