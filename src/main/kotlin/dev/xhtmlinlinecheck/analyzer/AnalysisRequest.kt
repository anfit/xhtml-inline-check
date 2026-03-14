package dev.xhtmlinlinecheck.analyzer

import java.nio.file.Path

data class AnalysisRequest(
    val oldRoot: Path,
    val newRoot: Path,
    val baseOld: Path? = null,
    val baseNew: Path? = null,
)
