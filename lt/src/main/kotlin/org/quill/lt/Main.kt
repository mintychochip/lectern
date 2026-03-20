package org.quill.lt

import org.quill.lt.commands.*
import java.nio.file.Paths

fun main(args: Array<String>) {
    val projectDir = Paths.get(System.getProperty("user.dir"))

    if (args.isEmpty()) {
        println("lt - Quill package manager v0.1.0")
        println("Usage: lt <command> [options]")
        println("")
        println("Commands:")
        println("  lt new <name>     Create a new package")
        println("  lt init          Initialize quill.toml in existing project")
        println("  lt add <pkg>[@v] Install a package")
        println("  lt remove <pkg>  Uninstall a package")
        println("  lt install       Install all dependencies from quill.toml")
        println("  lt ls            List installed packages")
        println("  lt clean         Remove .quill-cache/")
        return
    }

    val command = args[0]
    try {
        when (command) {
            "new" -> {
                if (args.size < 2) {
                    println("Usage: lt new <name>")
                    return
                }
                NewCommand(projectDir).run(args[1])
            }
            "init" -> {
                val name = args.getOrNull(1)
                InitCommand(projectDir).run(name)
            }
            "add" -> {
                if (args.size < 2) {
                    println("Usage: lt add <package>[@version]")
                    return
                }
                AddCommand(projectDir).run(args[1])
            }
            "remove" -> {
                if (args.size < 2) {
                    println("Usage: lt remove <package>")
                    return
                }
                RemoveCommand(projectDir).run(args[1])
            }
            "install" -> {
                InstallCommand(projectDir).run()
            }
            "ls" -> {
                LsCommand(projectDir).run()
            }
            "clean" -> {
                CleanCommand(projectDir).run()
            }
            else -> {
                println("Unknown command: $command")
                println("Run `lt` with no arguments for usage.")
            }
        }
    } catch (e: Exception) {
        System.err.println("Error: ${e.message}")
        e.printStackTrace()
    }
}
