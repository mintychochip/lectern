package org.quill.opt

import org.quill.ast.ControlFlowGraph
import org.quill.lang.IrInstr

/**
 * Interface for IR optimization passes.
 * Each pass takes the current IR and CFG, returns optimized IR.
 */
interface OptPass {
    val name: String

    /**
     * Run this optimization pass.
     * @param instrs Current IR instructions
     * @param cfg Control flow graph for the current IR
     * @param constants Current constant pool (may be modified)
     * @return Pair of (optimized instructions, modified constants)
     */
    fun run(
        instrs: List<IrInstr>,
        cfg: ControlFlowGraph,
        constants: List<org.quill.lang.Value>
    ): OptResult

    /**
     * Whether this pass should re-run if it made changes.
     * Most passes should re-run until fixed point.
     */
    fun shouldRerunIfChanged(): Boolean = true
}

/**
 * Result of an optimization pass.
 */
data class OptResult(
    val instrs: List<IrInstr>,
    val constants: List<org.quill.lang.Value>,
    val changed: Boolean
)
