package org.quill

import org.quill.ast.AstLowerer
import org.quill.ast.ConstantFolder
import org.quill.ast.LivenessAnalyzer
import org.quill.ast.RegisterAllocator
import org.quill.ast.SpillInserter
import org.quill.lang.IrCompiler
import org.quill.lang.Parser
import org.quill.lang.VM
import org.quill.lang.Value
import org.quill.lang.tokenize
import java.io.File

fun main(args: Array<String>) {
    val filename = if (args.isNotEmpty()) args[0] else "test.lec"
    val source = File(filename).readText()
    println("=== Tokenizing ===")
    val tokens = tokenize(source)
    print(tokens)
    println("Tokens: ${tokens.size}")

    println("\n=== Parsing ===")
    val parser = Parser(tokens)
    val statements = parser.parse()
    println("Statements: ${statements.size}")
    for (stmt in statements) {
        println("  $stmt")
    }
    val folder = ConstantFolder()
    val folded = statements.map { folder.foldStmt(it) }
    println("Folded Statements: ${folded.size}")
    for (stmt in folded) {
        println("  $stmt")
    }
    val result = AstLowerer().lower(folded)

    // SSA round-trip with optimizations: IR -> SSA -> IR
    val (ssaDeconstructed, ssaOptConstants) = IrCompiler.optimizedSsaRoundTrip(result.instrs, result.constants)
    val ssaResult = AstLowerer.LoweredResult(ssaDeconstructed, ssaOptConstants)

    val ranges = LivenessAnalyzer().analyze(ssaResult.instrs)
    val allocResult = RegisterAllocator().allocate(ranges)
    val resolved = SpillInserter().insert(ssaResult.instrs, allocResult, ranges)
    val chunk = IrCompiler().compile(AstLowerer.LoweredResult(resolved, ssaResult.constants))
    chunk.spillSlotCount = allocResult.spillSlotCount
    println("\n=== Bytecode ===")
    chunk.disassemble()
    println("\n=== Execution ===")
    val vm = VM()
    vm.execute(chunk)
}
