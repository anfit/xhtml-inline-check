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
    val sourcePath: String? = null,
    val includedDocument: SourceDocument? = null,
    val includedFile: SourceGraphFile? = null,
    val parameters: List<SourceGraphParameter> = emptyList(),
    val stackBefore: SourceGraphStack = SourceGraphStack.root(),
) {
    init {
        require(includedFile == null || includedDocument != null) {
            "included source graph files require a resolved included document"
        }
        require(includedFile == null || includedFile.document == includedDocument) {
            "included source graph file must match the resolved included document"
        }
    }

    val isResolved: Boolean
        get() = includedDocument != null

    val stackAfter: SourceGraphStack
        get() = stackBefore.push(this)

    fun asIncludeStep(): IncludeProvenanceStep =
        IncludeProvenanceStep(
            includeSite = includeSite,
            includedDocument = requireNotNull(includedDocument) {
                "include provenance requires a resolved included document"
            },
            parameterNames = parameters.map { it.name },
        )

    fun provenanceForIncludedFile(): Provenance {
        val resolvedDocument = requireNotNull(includedDocument) {
            "included file provenance requires a resolved included document"
        }
        val rootLocation = SourceLocation(document = resolvedDocument)
        return Provenance(
            physicalLocation = rootLocation,
            logicalLocation = rootLocation,
            includeStack = stackAfter.steps,
        )
    }

    companion object {
        fun discovered(
            includeSite: SourceLocation,
            sourcePath: String?,
            includedFile: SourceGraphFile? = null,
            parameters: List<SourceGraphParameter> = emptyList(),
            stackBefore: SourceGraphStack = SourceGraphStack.root(),
        ): SourceGraphEdge =
            SourceGraphEdge(
                includeSite = includeSite,
                sourcePath = sourcePath,
                includedFile = includedFile,
                parameters = parameters,
                stackBefore = stackBefore,
            )

        fun resolved(
            includeSite: SourceLocation,
            includedDocument: SourceDocument,
            sourcePath: String? = null,
            includedFile: SourceGraphFile? = null,
            parameters: List<SourceGraphParameter> = emptyList(),
            stackBefore: SourceGraphStack = SourceGraphStack.root(),
        ): SourceGraphEdge =
            SourceGraphEdge(
                includeSite = includeSite,
                sourcePath = sourcePath,
                includedDocument = includedDocument,
                includedFile = includedFile,
                parameters = parameters,
                stackBefore = stackBefore,
            )
    }
}

data class SourceGraphFile(
    val document: SourceDocument,
    val contents: String,
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
        require(includeEdges.all { it.includeSite.document == document }) {
            "source graph include edges must originate from the same document"
        }
    }

    companion object {
        fun root(document: SourceDocument): SourceGraphFile {
            val provenance = Provenance.forRoot(document)
            return SourceGraphFile(
                document = document,
                contents = "",
                provenance = provenance,
                stack = SourceGraphStack(provenance.includeStack),
            )
        }

        fun included(
            document: SourceDocument,
            edge: SourceGraphEdge,
            contents: String = "",
            includeEdges: List<SourceGraphEdge> = emptyList(),
        ): SourceGraphFile {
            val provenance = edge.provenanceForIncludedFile()
            return SourceGraphFile(
                document = document,
                contents = contents,
                provenance = provenance,
                stack = edge.stackAfter,
                includeEdges = includeEdges,
            )
        }
    }
}
