package org.quill

import org.quill.ast.AstLowerer
import org.quill.ast.ConstantFolder
import org.quill.ast.LivenessAnalyzer
import org.quill.ast.RegisterAllocator
import org.quill.ast.SpillInserter
import org.quill.lang.IrCompiler
import org.quill.lang.Parser
import org.quill.lang.tokenize

/**
 * Compiler for Quill source code.
 * Provides a clean API for runtime hosts (Paper plugin, tests, CLI).
 */
class QuillCompiler {

    /**
     * Compile Quill source code into a CompiledScript.
     * @param source Quill source code
     * @param name Script name (used for error reporting, defaults to "main")
     * @return CompiledScript ready for execution
     * @throws Exception on syntax or type errors
     */
    fun compile(source: String, name: String = "main"): CompiledScript {
        val tokens = tokenize(source)
        val parser = Parser(tokens)
        val statements = parser.parse()
        val folder = ConstantFolder()
        val folded = statements.map { folder.foldStmt(it) }
        val result = AstLowerer().lower(folded)

        // SSA round-trip with optimizations
        val (ssaDeconstructed, ssaOptConstants) = IrCompiler.optimizedSsaRoundTrip(
            result.instrs, result.constants
        )
        val ssaResult = AstLowerer.LoweredResult(ssaDeconstructed, ssaOptConstants)

        val ranges = LivenessAnalyzer().analyze(ssaResult.instrs)
        val allocResult = RegisterAllocator().allocate(ranges)
        val resolved = SpillInserter().insert(ssaResult.instrs, allocResult, ranges)
        val chunk = IrCompiler().compile(AstLowerer.LoweredResult(resolved, ssaOptConstants))
        chunk.spillSlotCount = allocResult.spillSlotCount

        return CompiledScript(name, chunk)
    }
}
