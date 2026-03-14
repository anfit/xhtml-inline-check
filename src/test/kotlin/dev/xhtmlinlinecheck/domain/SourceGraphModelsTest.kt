package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class SourceGraphModelsTest {
    @Test
    fun `root source graph file reuses root provenance`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )

        val file = SourceGraphFile.root(document)

        assertThat(file.document).isEqualTo(document)
        assertThat(file.provenance).isEqualTo(Provenance.forRoot(document))
        assertThat(file.contents).isEmpty()
        assertThat(file.stack.steps).isEmpty()
        assertThat(file.includeEdges).isEmpty()
    }

    @Test
    fun `include edges preserve parameters and grow source graph stacks`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )
        val includedDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "fragments", "table.xhtml"),
        )
        val parameterProvenance = Provenance.forRoot(rootDocument).copy(
            physicalLocation = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 7, column = 9)),
                attributeName = "value",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            logicalLocation = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 7, column = 9)),
                attributeName = "value",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
        )
        val edge = SourceGraphEdge(
            includeSite = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 5, column = 5)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            sourcePath = "/fragments/table.xhtml",
            includedDocument = includedDocument,
            parameters = listOf(
                SourceGraphParameter(
                    name = "row",
                    valueExpression = "#{bean.row}",
                    provenance = parameterProvenance,
                ),
            ),
        )

        val includedFile = SourceGraphFile.included(includedDocument, edge, contents = "<ui:fragment />")

        assertThat(edge.asIncludeStep().parameterNames).containsExactly("row")
        assertThat(edge.sourcePath).isEqualTo("/fragments/table.xhtml")
        assertThat(edge.isResolved).isTrue()
        assertThat(edge.parameters.single().valueExpression).isEqualTo("#{bean.row}")
        assertThat(edge.parameters.single().provenance.physicalLocation.render())
            .isEqualTo("legacy/root.xhtml:7:9 @value (element fallback)")
        assertThat(edge.stackAfter.steps).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.stack.steps).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.provenance.includeStack).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.provenance.physicalLocation.render()).isEqualTo("legacy/fragments/table.xhtml")
        assertThat(includedFile.provenance.logicalLocation.render())
            .isEqualTo("legacy/root.xhtml:5:5 @src (element fallback)")
        assertThat(includedFile.contents).isEqualTo("<ui:fragment />")
    }

    @Test
    fun `discovered include edges can exist before include resolution`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.NEW,
            path = Path.of("refactored", "root.xhtml"),
        )

        val edge = SourceGraphEdge.discovered(
            includeSite = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 9, column = 13)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            sourcePath = "#{bean.fragmentPath}",
        )

        assertThat(edge.sourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(edge.includedDocument).isNull()
        assertThat(edge.includedFile).isNull()
        assertThat(edge.parameters).isEmpty()
        assertThat(edge.isResolved).isFalse()
    }

    @Test
    fun `dynamic include failures keep the original unresolved src value`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )

        val edge = SourceGraphEdge.discovered(
            includeSite = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 9, column = 13)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            sourcePath = "#{bean.fragmentPath}",
            includeFailure = SourceGraphIncludeFailure.dynamicPath("#{bean.fragmentPath}"),
        )

        assertThat(edge.includeFailure).isNotNull()
        assertThat(edge.includeFailure!!.kind).isEqualTo(SourceGraphIncludeFailureKind.DYNAMIC_PATH)
        assertThat(edge.includeFailure!!.dynamicSourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(edge.includedDocument).isNull()
        assertThat(edge.includedFile).isNull()
    }

    @Test
    fun `include cycle failures keep the ordered recursive document chain`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )
        val outerDocument = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "fragments", "outer.xhtml"),
        )

        val edge = SourceGraphEdge.resolved(
            includeSite = SourceLocation(
                document = outerDocument,
                span = SourceSpan(SourcePosition(line = 3, column = 7)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            includedDocument = rootDocument,
            sourcePath = "/legacy/root.xhtml",
            includeFailure = SourceGraphIncludeFailure.includeCycle(
                listOf(rootDocument, outerDocument, rootDocument),
            ),
        )

        assertThat(edge.includeFailure).isNotNull()
        assertThat(edge.includeFailure!!.kind).isEqualTo(SourceGraphIncludeFailureKind.INCLUDE_CYCLE)
        assertThat(edge.includeFailure!!.cycleDocuments.map { it.displayPath })
            .containsExactly("legacy/root.xhtml", "legacy/fragments/outer.xhtml", "legacy/root.xhtml")
    }

    @Test
    fun `missing include failures keep the resolved target document`() {
        val rootDocument = SourceDocument.fromPath(
            side = AnalysisSide.NEW,
            path = Path.of("refactored", "root.xhtml"),
        )
        val missingDocument = SourceDocument.fromPath(
            side = AnalysisSide.NEW,
            path = Path.of("refactored", "fragments", "missing.xhtml"),
        )

        val edge = SourceGraphEdge.resolved(
            includeSite = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 4, column = 11)),
                attributeName = "src",
                attributeLocationPrecision = AttributeLocationPrecision.ELEMENT_FALLBACK,
            ),
            includedDocument = missingDocument,
            sourcePath = "/fragments/missing.xhtml",
            includeFailure = SourceGraphIncludeFailure.missingFile(missingDocument),
        )

        assertThat(edge.includeFailure).isNotNull()
        assertThat(edge.includeFailure!!.kind).isEqualTo(SourceGraphIncludeFailureKind.MISSING_FILE)
        assertThat(edge.includeFailure!!.missingDocument).isEqualTo(missingDocument)
        assertThat(edge.includedFile).isNull()
    }
}
