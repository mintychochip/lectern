package org.quill.opt

import org.quill.lang.IrInstr
import org.quill.lang.IrLabel
import org.quill.lang.TokenType
import org.quill.lang.Value
import org.quill.opt.passes.ConstantFoldingPass
import org.quill.opt.passes.CopyPropagationPass
import org.quill.opt.passes.DeadCodeEliminationPass
import org.quill.opt.passes.InductionVariablePass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptimizationPassesTest {

    // Helper to create unique labels
    private var labelCounter = 0
    private fun label(): IrLabel = IrLabel(labelCounter++)

    /**
     * Test: Constant folding for arithmetic
     */
    @Test
    fun testConstantFoldingArithmetic() {
        labelCounter = 0
        val constants = mutableListOf<Value>(
            Value.Int(2),
            Value.Int(3)
        )

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // r0 = 2
            IrInstr.LoadImm(1, 1),      // r1 = 3
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),  // r2 = r0 + r1 (should fold to 5)
            IrInstr.Return(2)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = ConstantFoldingPass()
        val result = pass.run(instrs, cfg, constants)

        assertTrue(result.changed, "Pass should report changes")

        // Should have LoadImm for folded result instead of BinaryOp
        val hasFoldedLoad = result.instrs.any {
            it is IrInstr.LoadImm && it.dst == 2
        }
        assertTrue(hasFoldedLoad, "Should have folded to LoadImm")

        // Check the folded constant is 5
        val foldedLoad = result.instrs.filterIsInstance<IrInstr.LoadImm>()
            .first { it.dst == 2 }
        val foldedValue = result.constants[foldedLoad.index]
        assertEquals(Value.Int(5), foldedValue)

        println("Constant folding result:")
        result.instrs.forEach { println("  $it") }
    }

    /**
     * Test: Constant folding for comparisons
     */
    @Test
    fun testConstantFoldingComparison() {
        labelCounter = 0
        val constants = mutableListOf<Value>(
            Value.Int(5),
            Value.Int(10)
        )

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // r0 = 5
            IrInstr.LoadImm(1, 1),      // r1 = 10
            IrInstr.BinaryOp(2, TokenType.LT, 0, 1),   // r2 = 5 < 10 (should fold to true)
            IrInstr.Return(2)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = ConstantFoldingPass()
        val result = pass.run(instrs, cfg, constants)

        assertTrue(result.changed)

        val foldedLoad = result.instrs.filterIsInstance<IrInstr.LoadImm>()
            .first { it.dst == 2 }
        val foldedValue = result.constants[foldedLoad.index]
        assertEquals(Value.Boolean(true), foldedValue)
    }

    /**
     * Test: Dead code elimination removes unreachable code
     */
    @Test
    fun testDeadCodeEliminationUnreachable() {
        labelCounter = 0

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // Block 0
            IrInstr.Return(0),
            IrInstr.LoadImm(1, 1),      // Block 1 - unreachable
            IrInstr.Return(1)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = DeadCodeEliminationPass()
        val result = pass.run(instrs, cfg, emptyList())

        assertTrue(result.changed)
        assertEquals(2, result.instrs.size, "Should have 2 instructions (reachable only)")
        assertTrue(result.instrs[1] is IrInstr.Return)
    }

    /**
     * Test: Dead code elimination removes unused register definitions
     */
    @Test
    fun testDeadCodeEliminationUnused() {
        labelCounter = 0
        val constants = listOf(Value.Int(5), Value.Int(10), Value.Int(15))

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // r0 = 5 (used)
            IrInstr.LoadImm(1, 1),      // r1 = 10 (UNUSED)
            IrInstr.LoadImm(2, 2),      // r2 = 15 (used)
            IrInstr.BinaryOp(3, TokenType.PLUS, 0, 2),  // r3 = r0 + r2
            IrInstr.Return(3)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = DeadCodeEliminationPass()
        val result = pass.run(instrs, cfg, constants)

        assertTrue(result.changed)

        // r1's LoadImm should be removed
        val hasLoadR1 = result.instrs.any {
            it is IrInstr.LoadImm && it.dst == 1
        }
        assertFalse(hasLoadR1, "Unused LoadImm for r1 should be removed")

        println("DCE result:")
        result.instrs.forEach { println("  $it") }
    }

    /**
     * Test: Copy propagation replaces copied register uses
     */
    @Test
    fun testCopyPropagation() {
        labelCounter = 0
        val constants = listOf(Value.Int(5))

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // r0 = 5
            IrInstr.Move(1, 0),         // r1 = r0 (copy)
            IrInstr.BinaryOp(2, TokenType.PLUS, 1, 0),  // r2 = r1 + r0 -> r2 = r0 + r0
            IrInstr.Return(2)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = CopyPropagationPass()
        val result = pass.run(instrs, cfg, constants)

        assertTrue(result.changed)

        // BinaryOp should now use r0 instead of r1
        val binOp = result.instrs.filterIsInstance<IrInstr.BinaryOp>().first()
        assertEquals(0, binOp.src1, "src1 should be r0 (propagated from r1)")
        assertEquals(0, binOp.src2)

        println("Copy propagation result:")
        result.instrs.forEach { println("  $it") }
    }

    /**
     * Test: Full optimization pipeline
     */
    @Test
    fun testFullPipeline() {
        labelCounter = 0
        val constants = mutableListOf<Value>(
            Value.Int(2),
            Value.Int(3),
            Value.Int(0)
        )

        val instrs = listOf(
            // These constants should be folded
            IrInstr.LoadImm(0, 0),      // r0 = 2
            IrInstr.LoadImm(1, 1),      // r1 = 3
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),  // r2 = 2 + 3 = 5

            // This copy should be propagated
            IrInstr.Move(3, 2),         // r3 = r2

            // Unused computation should be eliminated
            IrInstr.LoadImm(4, 2),      // r4 = 0 (UNUSED)

            IrInstr.Return(3)           // return r3
        )

        val pipeline = OptimizationPipeline(listOf(
            ConstantFoldingPass(),
            CopyPropagationPass(),
            DeadCodeEliminationPass()
        ))

        val (optimized, newConstants) = pipeline.optimize(instrs, constants)

        println("\nFull pipeline result:")
        println("Original: ${instrs.size} instructions")
        println("Optimized: ${optimized.size} instructions")
        optimized.forEach { println("  $it") }

        // Should have fewer instructions
        assertTrue(optimized.size < instrs.size, "Should have fewer instructions after optimization")
    }

    /**
     * Test: Pipeline with logging
     */
    @Test
    fun testPipelineWithLogging() {
        labelCounter = 0
        val constants = mutableListOf<Value>(
            Value.Int(10),
            Value.Int(20)
        )

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 1),
            IrInstr.BinaryOp(2, TokenType.STAR, 0, 1),  // 10 * 20 = 200
            IrInstr.Return(2)
        )

        val pipeline = OptimizationPipeline(listOf(
            ConstantFoldingPass()
        ))

        val (optimized, newConstants) = pipeline.optimizeWithLogging(instrs, constants)

        // Verify the constant was folded
        val hasFoldedValue = newConstants.any {
            it is Value.Int && it.value == 200
        }
        assertTrue(hasFoldedValue, "Should have folded constant 200")
    }

    /**
     * Test: Constant folding doesn't fold division by zero
     */
    @Test
    fun testNoFoldDivisionByZero() {
        labelCounter = 0
        val constants = mutableListOf<Value>(
            Value.Int(10),
            Value.Int(0)
        )

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // r0 = 10
            IrInstr.LoadImm(1, 1),      // r1 = 0
            IrInstr.BinaryOp(2, TokenType.SLASH, 0, 1),  // r2 = 10 / 0 (should NOT fold)
            IrInstr.Return(2)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = ConstantFoldingPass()
        val result = pass.run(instrs, cfg, constants)

        // Division by zero should not be folded
        val hasBinaryOp = result.instrs.any { it is IrInstr.BinaryOp }
        assertTrue(hasBinaryOp, "Division by zero should not be folded")

        println("Division by zero (not folded):")
        result.instrs.forEach { println("  $it") }
    }

    /**
     * Test: Induction variable pass detects Range loop pattern and transforms it.
     *
     * This test creates a simple Range for loop IR:
     *   for i in 0..5 { print(i) }
     *
     * The pass should recognize the iterator pattern and replace it with
     * direct arithmetic (valueReg = start; if valueReg > end goto end; body; valueReg = valueReg + 1).
     */
    @Test
    fun testInductionVariableRangeLoop() {
        labelCounter = 0
        val constants = mutableListOf(
            Value.Int(0),    // index 0: start = 0
            Value.Int(5),    // index 1: end = 5
            Value.String("body")  // index 2: placeholder
        )

        val topLabel = IrLabel(0)
        val endLabel = IrLabel(1)

        // IR for "for i in 0..5 { print(i) }" using Range iterator pattern:
        //   r0 = LoadImm #0        ; r0 = 0 (start)
        //   r1 = LoadImm #1        ; r1 = 5 (end)
        //   r2 = BinaryOp(DOT_DOT) r0, r1  ; r2 = Range(0, 5)
        //   r3 = GetField r2, "iter"       ; r3 = r2.iter
        //   r4 = Call r3, []       ; r4 = r3()
        //   Label loopHeader
        //   r5 = GetField r4, "hasNext"   ; r5 = r4.hasNext
        //   r6 = Call r5, []       ; r6 = r5()
        //   JumpIfFalse r6, endLabel
        //   r7 = GetField r4, "next"       ; r7 = r4.next
        //   r8 = Call r7, []       ; r8 = r7()
        //   ; body would use r8 here
        //   Jump loopHeader
        //   Label endLabel
        //   Return r8

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),                        // r0 = 0
            IrInstr.LoadImm(1, 1),                        // r1 = 5
            IrInstr.BinaryOp(2, TokenType.DOT_DOT, 0, 1), // r2 = Range(0, 5)
            IrInstr.GetField(3, 2, "iter"),               // r3 = r2.iter
            IrInstr.Call(4, 3, listOf()),                // r4 = r3()
            IrInstr.Label(topLabel),                      // loopHeader:
            IrInstr.GetField(5, 4, "hasNext"),           // r5 = r4.hasNext
            IrInstr.Call(6, 5, listOf()),                // r6 = r5()
            IrInstr.JumpIfFalse(6, endLabel),             // if !r6 goto endLabel
            IrInstr.GetField(7, 4, "next"),              // r7 = r4.next
            IrInstr.Call(8, 7, listOf()),                // r8 = r7()
            // Body placeholder (r8 would be used here)
            IrInstr.LoadImm(9, 2),                       // r9 = "body" placeholder
            IrInstr.Jump(topLabel),                      // goto loopHeader
            IrInstr.Label(endLabel),                     // endLabel:
            IrInstr.Return(8)
        )

        val cfg = org.quill.ast.ControlFlowGraph.build(instrs)
        val pass = InductionVariablePass()
        val result = pass.run(instrs, cfg, constants)

        println("Induction variable pass result:")
        result.instrs.forEachIndexed { idx, instr -> println("  $idx: $instr") }

        // After transformation, the following should be true:
        // 1. No GetField("next") or GetField("hasNext") or GetField("iter") instructions
        // 2. The iterator GetField and Call should be removed
        // 3. The JumpIfFalse should be replaced with GT comparison

        val hasIterGet = result.instrs.any { it is IrInstr.GetField && it.name == "iter" }
        val hasHasNextGet = result.instrs.any { it is IrInstr.GetField && it.name == "hasNext" }
        val hasNextGet = result.instrs.any { it is IrInstr.GetField && it.name == "next" }

        println("Has iter GetField: $hasIterGet (should be false)")
        println("Has hasNext GetField: $hasHasNextGet (should be false)")
        println("Has next GetField: $hasNextGet (should be false)")

        assertFalse(hasIterGet, "Iterator GetField should be removed")
        assertFalse(hasHasNextGet, "hasNext GetField should be removed")
        assertFalse(hasNextGet, "next GetField should be removed")

        // The pass should have reported changes
        println("Pass reported changed: ${result.changed}")
    }
}
