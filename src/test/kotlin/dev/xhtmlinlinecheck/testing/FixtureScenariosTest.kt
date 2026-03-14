package dev.xhtmlinlinecheck.testing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

class FixtureScenariosTest {
    @Test
    fun `resolves shared smoke fixture with standard old-new layout`() {
        val scenario = FixtureScenarios.scenario("support/smoke")

        assertThat(scenario.name).isEqualTo("support/smoke")
        assertThat(scenario.oldRoot).isRegularFile()
        assertThat(scenario.newRoot).isRegularFile()
        assertThat(Files.isDirectory(scenario.oldDir)).isTrue()
        assertThat(Files.isDirectory(scenario.newDir)).isTrue()
    }
}
