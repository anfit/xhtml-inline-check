package dev.xhtmlinlinecheck.analyzer

object AnalysisProfiler {
    private val enabled: Boolean =
        profileFlag(System.getProperty("dev.xhtmlinlinecheck.profile")) ||
                profileFlag(System.getenv("XHTML_INLINE_CHECK_PROFILE"))

    private val depth = ThreadLocal.withInitial { 0 }

    fun <T> measure(stage: String, block: () -> T): T {
        if (!enabled) {
            return block()
        }

        val currentDepth = depth.get()
        val start = System.nanoTime()
        depth.set(currentDepth + 1)

        return try {
            block()
        } finally {
            depth.set(currentDepth)
            val elapsedMillis = (System.nanoTime() - start) / 1_000_000.0
            val indent = "  ".repeat(currentDepth)
            System.err.println("[profile] ${indent}${stage}: ${"%.2f".format(elapsedMillis)} ms")
        }
    }

    private fun profileFlag(value: String?): Boolean =
        when (value?.trim()?.lowercase()) {
            null, "", "0", "false", "off", "no" -> false
            else -> true
        }
}
