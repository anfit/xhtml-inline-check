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
            ),
            logicalLocation = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 7, column = 9)),
                attributeName = "value",
            ),
        )
        val edge = SourceGraphEdge(
            includeSite = SourceLocation(
                document = rootDocument,
                span = SourceSpan(SourcePosition(line = 5, column = 5)),
                attributeName = "src",
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
            .isEqualTo("legacy/root.xhtml:7:9 @value")
        assertThat(edge.stackAfter.steps).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.stack.steps).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.provenance.includeStack).containsExactly(edge.asIncludeStep())
        assertThat(includedFile.provenance.logicalLocation.render()).isEqualTo("legacy/fragments/table.xhtml")
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
            ),
            sourcePath = "#{bean.fragmentPath}",
        )

        assertThat(edge.sourcePath).isEqualTo("#{bean.fragmentPath}")
        assertThat(edge.includedDocument).isNull()
        assertThat(edge.includedFile).isNull()
        assertThat(edge.parameters).isEmpty()
        assertThat(edge.isResolved).isFalse()
    }
}
