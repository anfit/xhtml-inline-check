package dev.xhtmlinlinecheck.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FaceletsVerifyCliTest {
    @Test
    fun `returns inconclusive exit code for scaffolded pipeline`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(listOf("legacy.xhtml", "refactored.xhtml"), output)

        assertEquals(2, exitCode)
        assertTrue(output.toString().contains("INCONCLUSIVE"))
        assertTrue(output.toString().contains("W00"))
    }

    @Test
    fun `prints usage when roots are missing`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(emptyList(), output)

        assertEquals(64, exitCode)
        assertTrue(output.toString().contains("Usage: facelets-verify"))
    }

    @Test
    fun `renders json when requested`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf("legacy.xhtml", "refactored.xhtml", "--format", "json"),
            output,
        )

        assertEquals(2, exitCode)
        assertTrue(output.toString().contains("\"result\": \"INCONCLUSIVE\""))
    }

    @Test
    fun `accepts format flag before root arguments`() {
        val output = StringBuilder()

        val exitCode = FaceletsVerifyCli().run(
            listOf("--format", "json", "legacy.xhtml", "refactored.xhtml"),
            output,
        )

        assertEquals(2, exitCode)
        assertTrue(output.toString().contains("\"result\": \"INCONCLUSIVE\""))
    }
}
