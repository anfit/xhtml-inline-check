package dev.xhtmlinlinecheck.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class LocationModelsTest {
    @Test
    fun `normalizes source documents into stable display paths`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", ".", "page.xhtml"),
        )

        assertThat(document.side).isEqualTo(AnalysisSide.OLD)
        assertThat(document.absolutePath).isAbsolute()
        assertThat(document.absolutePath.fileName.toString()).isEqualTo("page.xhtml")
        assertThat(document.rootDirectory).isEqualTo(Path.of("").toAbsolutePath().normalize())
        assertThat(document.displayPath).isEqualTo("legacy/page.xhtml")
    }

    @Test
    fun `anchors display path to provided root directory when resolving relative roots`() {
        val rootDirectory = Path.of("fixtures", "legacy")
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("pages", ".", "order.xhtml"),
            rootDirectory = rootDirectory,
        )

        assertThat(document.rootDirectory).isEqualTo(rootDirectory.toAbsolutePath().normalize())
        assertThat(document.absolutePath)
            .isEqualTo(rootDirectory.toAbsolutePath().normalize().resolve("pages/order.xhtml"))
        assertThat(document.displayPath).isEqualTo("pages/order.xhtml")
    }

    @Test
    fun `resolves include src relative to the current file when the path is not root-anchored`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("pages", "orders", "root.xhtml"),
            rootDirectory = Path.of("fixtures", "legacy"),
        )

        val includedDocument = document.resolveIncludeSource("../fragments/table.xhtml")

        assertThat(includedDocument).isNotNull()
        assertThat(includedDocument!!.rootDirectory).isEqualTo(document.rootDirectory)
        assertThat(includedDocument.absolutePath)
            .isEqualTo(document.rootDirectory.resolve("pages/fragments/table.xhtml").toAbsolutePath().normalize())
        assertThat(includedDocument.displayPath).isEqualTo("pages/fragments/table.xhtml")
    }

    @Test
    fun `resolves root-anchored include src against the base directory instead of the current file`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.NEW,
            path = Path.of("pages", "orders", "root.xhtml"),
            rootDirectory = Path.of("fixtures", "refactored"),
        )

        val includedDocument = document.resolveIncludeSource("/shared/table.xhtml")

        assertThat(includedDocument).isNotNull()
        assertThat(includedDocument!!.absolutePath)
            .isEqualTo(document.rootDirectory.resolve("shared/table.xhtml").toAbsolutePath().normalize())
        assertThat(includedDocument.displayPath).isEqualTo("shared/table.xhtml")
    }

    @Test
    fun `keeps dynamic include src unresolved until later unsupported handling`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.NEW,
            path = Path.of("refactored", "root.xhtml"),
        )

        val includedDocument = document.resolveIncludeSource("#{bean.fragmentPath}")

        assertThat(includedDocument).isNull()
    }

    @Test
    fun `renders source locations with line column and attribute context`() {
        val location = SourceLocation(
            document = SourceDocument.fromPath(
                side = AnalysisSide.NEW,
                path = Path.of("refactored", "page.xhtml"),
            ),
            span = SourceSpan(
                start = SourcePosition(line = 12, column = 7),
            ),
            attributeName = "rendered",
        )

        assertThat(location.render()).isEqualTo("refactored/page.xhtml:12:7 @rendered")
    }

    @Test
    fun `creates root provenance with matching physical and logical locations`() {
        val document = SourceDocument.fromPath(
            side = AnalysisSide.OLD,
            path = Path.of("legacy", "root.xhtml"),
        )

        val provenance = Provenance.forRoot(document)

        assertThat(provenance.physicalLocation.document).isEqualTo(document)
        assertThat(provenance.logicalLocation.document).isEqualTo(document)
        assertThat(provenance.includeStack).isEmpty()
    }
}
