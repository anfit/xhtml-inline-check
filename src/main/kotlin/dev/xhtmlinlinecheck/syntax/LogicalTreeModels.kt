package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.SourceLocation

data class LogicalName(
    val localName: String,
    val namespaceUri: String? = null,
    val prefix: String? = null,
)

data class LogicalAttribute(
    val name: LogicalName,
    val value: String,
    val location: SourceLocation,
)

sealed interface LogicalNode {
    val provenance: Provenance
}

data class LogicalElementNode(
    val name: LogicalName,
    val attributes: List<LogicalAttribute>,
    val children: List<LogicalNode>,
    override val provenance: Provenance,
) : LogicalNode

data class LogicalTextNode(
    val text: String,
    override val provenance: Provenance,
) : LogicalNode

data class LogicalIncludeNode(
    val includeSite: SourceLocation,
    val sourcePath: String?,
    val parameters: List<SourceGraphParameter>,
    val expandedFile: SourceGraphFile? = null,
    val includeFailure: SourceGraphIncludeFailure? = null,
    val children: List<LogicalNode> = emptyList(),
    override val provenance: Provenance,
) : LogicalNode
