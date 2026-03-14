package dev.xhtmlinlinecheck.testing

import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.domain.AnalysisResult
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

class AnalysisReportAssert(actual: AnalysisReport) :
    AbstractAssert<AnalysisReportAssert, AnalysisReport>(actual, AnalysisReportAssert::class.java) {
    fun hasResult(expected: AnalysisResult): AnalysisReportAssert {
        isNotNull
        assertThat(actual.result).isEqualTo(expected)
        return this
    }

    fun hasSummaryContaining(expected: String): AnalysisReportAssert {
        isNotNull
        assertThat(actual.summary.headline).contains(expected)
        return this
    }

    fun hasProblemCount(expected: Int): AnalysisReportAssert {
        isNotNull
        assertThat(actual.problems).hasSize(expected)
        return this
    }

    fun hasWarningCount(expected: Int): AnalysisReportAssert {
        isNotNull
        assertThat(actual.stats.warnings.total).isEqualTo(expected)
        return this
    }

    fun hasProblemIds(vararg expected: String): AnalysisReportAssert {
        isNotNull
        assertThat(actual.problems.map { it.id }).containsExactly(*expected)
        return this
    }
}

fun assertThatReport(actual: AnalysisReport): AnalysisReportAssert = AnalysisReportAssert(actual)
