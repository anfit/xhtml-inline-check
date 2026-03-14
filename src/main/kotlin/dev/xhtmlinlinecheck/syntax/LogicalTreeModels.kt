package dev.xhtmlinlinecheck.syntax

import dev.xhtmlinlinecheck.domain.Provenance
import dev.xhtmlinlinecheck.domain.SourceGraphFile
import dev.xhtmlinlinecheck.domain.SourceGraphIncludeFailure
import dev.xhtmlinlinecheck.domain.SourceGraphParameter
import dev.xhtmlinlinecheck.domain.SourceLocation
import dev.xhtmlinlinecheck.rules.TagRule

data class LogicalName(
    val localName: String,
    val namespaceUri: String? = null,
    val prefix: String? = null,
)

data class LogicalNamespaceBinding(
    val prefix: String? = null,
    val namespaceUri: String,
)

data class LogicalAttribute(
    val name: LogicalName,
    val value: String,
    val location: SourceLocation,
)

sealed interface LogicalNode {
    val location: SourceLocation
    val provenance: Provenance
}

data class XhtmlSyntaxTree(
    val root: LogicalElementNode?,
)

data class LogicalElementNode(
    val name: LogicalName,
    val tagRule: TagRule,
    val attributes: List<LogicalAttribute>,
    val namespaceBindings: List<LogicalNamespaceBinding> = emptyList(),
    val children: List<LogicalNode>,
    override val location: SourceLocation,
    override val provenance: Provenance,
) : LogicalNode

data class LogicalTextNode(
    val text: String,
    override val location: SourceLocation,
    override val provenance: Provenance,
) : LogicalNode

data class LogicalIncludeNode(
    val includeSite: SourceLocation,
    val sourcePath: String?,
    val parameters: List<SourceGraphParameter>,
    val expandedFile: SourceGraphFile? = null,
    val includeFailure: SourceGraphIncludeFailure? = null,
    val children: List<LogicalNode> = emptyList(),
    override val location: SourceLocation = includeSite,
    override val provenance: Provenance,
) : LogicalNode

val LogicalNode.isTransparentStructureWrapper: Boolean
    get() =
        when (this) {
            is LogicalElementNode -> tagRule.isTransparentStructureWrapper
            is LogicalIncludeNode -> true
            is LogicalTextNode -> false
        }

fun LogicalNode.normalizedStructureChildren(): List<LogicalNode> =
    when (this) {
        is LogicalElementNode -> children.flattenTransparentStructureWrappers()
        is LogicalIncludeNode -> children.flattenTransparentStructureWrappers()
        is LogicalTextNode -> emptyList()
    }

fun XhtmlSyntaxTree.normalizedStructureRoots(): List<LogicalNode> =
    root
        ?.let(::listOf)
        ?.flattenTransparentStructureWrappers()
        ?: emptyList()

fun XhtmlSyntaxTree.walkDepthFirst(visitor: (LogicalNode) -> Unit) {
    fun visit(node: LogicalNode) {
        visitor(node)
        when (node) {
            is LogicalElementNode -> node.children.forEach(::visit)
            is LogicalIncludeNode -> node.children.forEach(::visit)
            is LogicalTextNode -> Unit
        }
    }

    root?.let(::visit)
}

fun XhtmlSyntaxTree.walkNormalizedStructureDepthFirst(visitor: (LogicalNode) -> Unit) {
    fun visit(node: LogicalNode) {
        visitor(node)
        node.normalizedStructureChildren().forEach(::visit)
    }

    normalizedStructureRoots().forEach(::visit)
}

private fun List<LogicalNode>.flattenTransparentStructureWrappers(): List<LogicalNode> =
    flatMap { node ->
        if (node.isTransparentStructureWrapper) {
            node.normalizedStructureChildren()
        } else {
            listOf(node)
        }
    }
