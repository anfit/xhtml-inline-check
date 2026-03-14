package dev.xhtmlinlinecheck.cli

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    exitProcess(FaceletsVerifyCli().run(args.toList()))
}
