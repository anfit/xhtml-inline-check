package dev.xhtmlinlinecheck.testing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files

data class FixtureExpectation(
    val result: String,
    val problemIds: List<String> = emptyList(),
    val warningIds: List<String> = emptyList(),
    val notes: String? = null,
)

object FixtureExpectations {
    private val objectMapper = jacksonObjectMapper()

    fun read(scenario: FixtureScenario): FixtureExpectation {
        require(Files.isRegularFile(scenario.expectedJson)) {
            "Fixture expectation file does not exist: ${scenario.expectedJson}"
        }
        return objectMapper.readValue(Files.readString(scenario.expectedJson))
    }
}
