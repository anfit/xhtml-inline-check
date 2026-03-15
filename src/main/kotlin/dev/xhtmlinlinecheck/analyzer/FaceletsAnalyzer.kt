package dev.xhtmlinlinecheck.analyzer

import dev.xhtmlinlinecheck.compare.EquivalenceComparator
import dev.xhtmlinlinecheck.domain.AnalysisReport
import dev.xhtmlinlinecheck.loader.SourceLoader
import dev.xhtmlinlinecheck.rules.TagRuleRegistry
import dev.xhtmlinlinecheck.semantic.SemanticAnalyzer
import dev.xhtmlinlinecheck.syntax.XhtmlSyntaxParser

class FaceletsAnalyzer(
    private val sourceLoader: SourceLoader,
    private val syntaxParser: XhtmlSyntaxParser,
    private val semanticAnalyzer: SemanticAnalyzer,
    private val comparator: EquivalenceComparator,
) {
    fun analyze(request: AnalysisRequest): AnalysisReport {
        return AnalysisProfiler.measure("analyze") {
            val loadedSources = AnalysisProfiler.measure("load") { sourceLoader.load(request) }
            val parsedTrees = AnalysisProfiler.measure("parse") { syntaxParser.parse(loadedSources) }
            val semanticModels = AnalysisProfiler.measure("semantic") { semanticAnalyzer.analyze(parsedTrees) }
            AnalysisProfiler.measure("compare") { comparator.compare(semanticModels) }
        }
    }

    companion object {
        fun scaffold(
            tagRules: TagRuleRegistry = TagRuleRegistry.builtIns(),
        ): FaceletsAnalyzer =
            FaceletsAnalyzer(
                sourceLoader = SourceLoader.scaffold(tagRules),
                syntaxParser = XhtmlSyntaxParser.scaffold(tagRules),
                semanticAnalyzer = SemanticAnalyzer.scaffold(tagRules),
                comparator = EquivalenceComparator.scaffold(),
            )
    }
}
