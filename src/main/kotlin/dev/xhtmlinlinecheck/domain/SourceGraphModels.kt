package dev.xhtmlinlinecheck.domain

data class SourceGraphParameter(
    val name: String,
    val valueExpression: String? = null,
    val provenance: Provenance,
)

data class SourceGraphStack(
    val steps: List<IncludeProvenanceStep> = emptyList(),
) {
    fun push(edge: SourceGraphEdge): SourceGraphStack = SourceGraphStack(steps + edge.asIncludeStep())

    companion object {
        fun root(): SourceGraphStack = SourceGraphStack()
    }
}

data class SourceGraphEdge(
    val includeSite: SourceLocation,
    val includedDocument: SourceDocument,
    val parameters: List<SourceGraphParameter> = emptyList(),
    val stackBefore: SourceGraphStack = SourceGraphStack.root(),
) {
    val stackAfter: SourceGraphStack
        get() = stackBefore.push(this)

    fun asIncludeStep(): IncludeProvenanceStep =
        IncludeProvenanceStep(
            includeSite = includeSite,
            includedDocument = includedDocument,
            parameterNames = parameters.map { it.name },
        )

    fun provenanceForIncludedFile(): Provenance {
        val rootLocation = SourceLocation(document = includedDocument)
        return Provenance(
            physicalLocation = rootLocation,
            logicalLocation = rootLocation,
            includeStack = stackAfter.steps,
        )
    }
}

data class SourceGraphFile(
    val document: SourceDocument,
    val provenance: Provenance,
    val stack: SourceGraphStack,
    val includeEdges: List<SourceGraphEdge> = emptyList(),
) {
    init {
        require(provenance.logicalLocation.document == document) {
            "source graph file provenance must point at its document"
        }
        require(stack.steps == provenance.includeStack) {
            "source graph file stack must match provenance include stack"
        }
    }

    companion object {
        fun root(document: SourceDocument): SourceGraphFile {
            val provenance = Provenance.forRoot(document)
            return SourceGraphFile(
                document = document,
                provenance = provenance,
                stack = SourceGraphStack(provenance.includeStack),
            )
        }

        fun included(
            document: SourceDocument,
            edge: SourceGraphEdge,
            includeEdges: List<SourceGraphEdge> = emptyList(),
        ): SourceGraphFile {
            val provenance = edge.provenanceForIncludedFile()
            return SourceGraphFile(
                document = document,
                provenance = provenance,
                stack = edge.stackAfter,
                includeEdges = includeEdges,
            )
        }
    }
}
