package org.quill.lt

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("lt - Quill package manager v0.1.0")
        println("Usage: lt <command> [options]")
        println("")
        println("Commands:")
        println("  lt new <name>     Create a new package")
        println("  lt add <pkg>      Install a package")
        println("  lt install        Install all dependencies")
        println("  lt build          Build the package")
        println("  lt run [file]     Run the package")
        println("  lt clean          Remove build artifacts")
        return
    }

    val command = args[0]
    when (command) {
        "new" -> println("lt new: not yet implemented")
        "add" -> println("lt add: not yet implemented")
        "install" -> println("lt install: not yet implemented")
        "build" -> println("lt build: not yet implemented")
        "run" -> println("lt run: not yet implemented")
        "clean" -> println("lt clean: not yet implemented")
        else -> println("Unknown command: $command")
    }
}
