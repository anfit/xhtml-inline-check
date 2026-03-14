package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.compare.EquivalenceComparator
import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser

class FaceletsAnalyzer(
    private val sourceLoader: SourceLoader,
    private val syntaxParser: XhtmlSyntaxParser,
    private val semanticAnalyzer: SemanticAnalyzer,
    private val comparator: EquivalenceComparator,
) {
    fun analyze(request: AnalysisRequest): AnalysisReport {
        val loadedSources = sourceLoader.load(request)
        val parsedTrees = syntaxParser.parse(loadedSources)
        val semanticModels = semanticAnalyzer.analyze(parsedTrees)
        return comparator.compare(semanticModels)
    }

    companion object {
        fun scaffold(): FaceletsAnalyzer =
            FaceletsAnalyzer(
                sourceLoader = SourceLoader.scaffold(),
                syntaxParser = XhtmlSyntaxParser.scaffold(),
                semanticAnalyzer = SemanticAnalyzer.scaffold(),
                comparator = EquivalenceComparator.scaffold(),
            )
    }
}
