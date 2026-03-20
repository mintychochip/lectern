package org.quill.ast

import org.quill.lang.IrInstr
import org.quill.lang.IrLabel
import org.quill.lang.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlFlowGraphTest {

    // Helper to create unique labels
    private var labelCounter = 0
    private fun label(): IrLabel = IrLabel(labelCounter++)

    /**
     * Test: Simple linear control flow (no branches)
     */
    @Test
    fun testLinearFlow() {
        labelCounter = 0
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),      // Block 0
            IrInstr.LoadImm(1, 1),
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),
            IrInstr.Return(2)
        )

        val cfg = ControlFlowGraph.build(instrs)

        assertEquals(1, cfg.blocks.size, "Should have 1 block for linear flow")
        assertEquals(0, cfg.entryBlock)
        assertEquals(setOf(0), cfg.exitBlocks)

        val block0 = cfg.getBlock(0)!!
        assertEquals(0, block0.successors.size, "Block 0 should have no successors (ends with return)")
        assertEquals(0, block0.predecessors.size, "Entry block should have no predecessors")

        println("Linear flow CFG:")
        println(cfg.dump())
    }

    /**
     * Test: Simple if statement
     */
    @Test
    fun testSimpleIf() {
        labelCounter = 0
        val elseLabel = label()
        val endLabel = label()

        val instrs = listOf(
            // Block 0: condition
            IrInstr.LoadImm(0, 0),          // r0 = true
            IrInstr.JumpIfFalse(0, elseLabel),  // if (!r0) goto else

            // Block 1: then branch
            IrInstr.LoadImm(1, 1),          // r1 = 1
            IrInstr.Jump(endLabel),         // goto end

            // Block 2: else branch
            IrInstr.Label(elseLabel),
            IrInstr.LoadImm(1, 2),          // r1 = 2

            // Block 3: end
            IrInstr.Label(endLabel),
            IrInstr.Return(1)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("Simple if CFG:")
        println(cfg.dump())

        assertEquals(4, cfg.blocks.size, "Should have 4 blocks for if/else")
        assertEquals(0, cfg.entryBlock)
        assertEquals(setOf(3), cfg.exitBlocks)

        // Block 0 (condition) should have successors: Block 1 (then) and Block 2 (else)
        val block0 = cfg.getBlock(0)!!
        assertTrue(1 in block0.successors, "Block 0 should have Block 1 as successor (fall-through)")
        assertTrue(2 in block0.successors, "Block 0 should have Block 2 as successor (jump target)")

        // Block 1 (then) should have successor: Block 3 (end)
        val block1 = cfg.getBlock(1)!!
        assertEquals(setOf(3), block1.successors)

        // Block 2 (else) should have successor: Block 3 (fall-through)
        val block2 = cfg.getBlock(2)!!
        assertEquals(setOf(3), block2.successors)

        // Block 3 (end) should have predecessors from Block 1 and Block 2
        val block3 = cfg.getBlock(3)!!
        assertTrue(1 in block3.predecessors)
        assertTrue(2 in block3.predecessors)
    }

    /**
     * Test: While loop
     */
    @Test
    fun testWhileLoop() {
        labelCounter = 0
        val loopLabel = label()
        val endLabel = label()

        val instrs = listOf(
            // Block 0: init
            IrInstr.LoadImm(0, 0),          // r0 = 0 (counter)
            IrInstr.LoadImm(1, 10),         // r1 = 10 (limit)

            // Block 1: loop condition
            IrInstr.Label(loopLabel),
            IrInstr.BinaryOp(2, TokenType.LT, 0, 1),  // r2 = r0 < r1
            IrInstr.JumpIfFalse(2, endLabel),    // if (!r2) goto end

            // Block 2: loop body
            IrInstr.BinaryOp(0, TokenType.PLUS, 0, 0), // r0 = r0 + 1
            IrInstr.Jump(loopLabel),              // goto loop

            // Block 3: end
            IrInstr.Label(endLabel),
            IrInstr.Return(0)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("While loop CFG:")
        println(cfg.dump())

        assertEquals(4, cfg.blocks.size)
        assertEquals(0, cfg.entryBlock)

        // Check loop detection
        val loops = cfg.naturalLoops()
        assertEquals(1, loops.size, "Should detect 1 loop")

        val loop = loops[0]
        assertEquals(1, loop.header, "Loop header should be Block 1")
        assertTrue(2 in loop.body, "Loop body should contain Block 2")

        // Block 2 should have back edge to Block 1
        val block2 = cfg.getBlock(2)!!
        assertTrue(1 in block2.successors, "Block 2 should have back edge to Block 1")
    }

    /**
     * Test: While loop with Break
     */
    @Test
    fun testWhileLoopWithBreak() {
        labelCounter = 0
        val loopLabel = label()
        val endLabel = label()

        val instrs = listOf(
            // Block 0: init
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 10),

            // Block 1: loop condition
            IrInstr.Label(loopLabel),
            IrInstr.BinaryOp(2, TokenType.LT, 0, 1),
            IrInstr.JumpIfFalse(2, endLabel),

            // Block 2: loop body with break
            IrInstr.LoadImm(3, 1),
            IrInstr.BinaryOp(2, TokenType.EQ_EQ, 0, 3),  // if (r0 == 1)
            IrInstr.JumpIfFalse(2, label()),          // skip break (dummy label)
            IrInstr.Break,                             // break

            // Block 3: continue in loop
            IrInstr.BinaryOp(0, TokenType.PLUS, 0, 0),
            IrInstr.Jump(loopLabel),

            // Block 4: end
            IrInstr.Label(endLabel),
            IrInstr.Return(0)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("While loop with break CFG:")
        println(cfg.dump())

        // Verify loop is detected
        val loops = cfg.naturalLoops()
        assertTrue(loops.isNotEmpty(), "Should detect loop")
    }

    /**
     * Test: While loop with Next (continue)
     */
    @Test
    fun testWhileLoopWithNext() {
        labelCounter = 0
        val loopLabel = label()
        val endLabel = label()

        val instrs = listOf(
            // Block 0: init
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 10),

            // Block 1: loop condition
            IrInstr.Label(loopLabel),
            IrInstr.BinaryOp(2, TokenType.LT, 0, 1),
            IrInstr.JumpIfFalse(2, endLabel),

            // Block 2: loop body with next
            IrInstr.LoadImm(3, 1),
            IrInstr.BinaryOp(2, TokenType.EQ_EQ, 0, 3),
            IrInstr.JumpIfFalse(2, label()),
            IrInstr.Next,  // continue

            // Block 3: continue in loop
            IrInstr.BinaryOp(0, TokenType.PLUS, 0, 0),
            IrInstr.Jump(loopLabel),

            // Block 4: end
            IrInstr.Label(endLabel),
            IrInstr.Return(0)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("While loop with next CFG:")
        println(cfg.dump())

        // Verify loop is detected
        val loops = cfg.naturalLoops()
        assertTrue(loops.isNotEmpty(), "Should detect loop")
    }

    /**
     * Test: Nested if statements
     */
    @Test
    fun testNestedIf() {
        labelCounter = 0
        val innerElse = label()
        val innerEnd = label()
        val outerElse = label()
        val outerEnd = label()

        val instrs = listOf(
            // Block 0: outer condition
            IrInstr.LoadImm(0, 1),
            IrInstr.JumpIfFalse(0, outerElse),

            // Block 1: inner condition
            IrInstr.LoadImm(1, 1),
            IrInstr.JumpIfFalse(1, innerElse),

            // Block 2: inner then
            IrInstr.LoadImm(2, 1),
            IrInstr.Jump(innerEnd),

            // Block 3: inner else
            IrInstr.Label(innerElse),
            IrInstr.LoadImm(2, 2),
            // fall through to innerEnd

            // Block 4: inner end
            IrInstr.Label(innerEnd),
            IrInstr.Jump(outerEnd),

            // Block 5: outer else
            IrInstr.Label(outerElse),
            IrInstr.LoadImm(2, 3),

            // Block 6: outer end
            IrInstr.Label(outerEnd),
            IrInstr.Return(2)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("Nested if CFG:")
        println(cfg.dump())

        assertEquals(7, cfg.blocks.size)

        // Verify reachability
        val reachable = cfg.reachable()
        assertEquals(cfg.blocks.size, reachable.size, "All blocks should be reachable")
    }

    /**
     * Test: Early return
     */
    @Test
    fun testEarlyReturn() {
        labelCounter = 0
        val endLabel = label()

        val instrs = listOf(
            // Block 0: condition
            IrInstr.LoadImm(0, 1),
            IrInstr.JumpIfFalse(0, endLabel),

            // Block 1: early return
            IrInstr.LoadImm(1, 42),
            IrInstr.Return(1),

            // Block 2: normal path
            IrInstr.Label(endLabel),
            IrInstr.LoadImm(1, 0),
            IrInstr.Return(1)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("Early return CFG:")
        println(cfg.dump())

        assertEquals(3, cfg.blocks.size)
        assertEquals(setOf(1, 2), cfg.exitBlocks, "Should have 2 exit blocks")

        // Block 0 should have successors to both Block 1 and Block 2
        val block0 = cfg.getBlock(0)!!
        assertTrue(1 in block0.successors)
        assertTrue(2 in block0.successors)

        // Block 1 should have no successors (return)
        val block1 = cfg.getBlock(1)!!
        assertTrue(block1.successors.isEmpty())
    }

    /**
     * Test: Dominators computation
     */
    @Test
    fun testDominators() {
        labelCounter = 0
        val elseLabel = label()
        val endLabel = label()

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.JumpIfFalse(0, elseLabel),

            IrInstr.LoadImm(1, 1),
            IrInstr.Jump(endLabel),

            IrInstr.Label(elseLabel),
            IrInstr.LoadImm(1, 2),

            IrInstr.Label(endLabel),
            IrInstr.Return(1)
        )

        val cfg = ControlFlowGraph.build(instrs)
        val doms = cfg.dominators()

        println("Dominators:")
        for ((blockId, dominatorSet) in doms) {
            println("  Block$blockId dominated by: ${dominatorSet.map { "Block$it" }}")
        }

        // Entry block is dominated only by itself
        assertEquals(setOf(0), doms[0])

        // Block 0 dominates all blocks
        for (blockId in cfg.blocks.map { it.id }) {
            assertTrue(0 in (doms[blockId] ?: emptySet()), "Block 0 should dominate Block $blockId")
        }
    }

    /**
     * Test: Unreachable code detection
     */
    @Test
    fun testUnreachableCode() {
        labelCounter = 0

        val instrs = listOf(
            // Block 0: always returns
            IrInstr.LoadImm(0, 42),
            IrInstr.Return(0),

            // Block 1: unreachable
            IrInstr.LoadImm(1, 0),
            IrInstr.Return(1)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("Unreachable code CFG:")
        println(cfg.dump())

        val unreachable = cfg.unreachable()
        assertTrue(1 in unreachable, "Block 1 should be unreachable")

        val reachable = cfg.reachable()
        assertFalse(1 in reachable, "Block 1 should not be in reachable set")
    }

    /**
     * Test: Empty instruction list
     */
    @Test
    fun testEmptyInstrs() {
        val cfg = ControlFlowGraph.build(emptyList())

        assertEquals(0, cfg.blocks.size)
        assertEquals(0, cfg.entryBlock)
        assertTrue(cfg.exitBlocks.isEmpty())
    }

    /**
     * Test: Complex control flow with multiple loops
     */
    @Test
    fun testMultipleLoops() {
        labelCounter = 0
        val loop1Label = label()
        val loop1End = label()
        val loop2Label = label()
        val loop2End = label()

        val instrs = listOf(
            // Block 0: first loop init
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 5),

            // Block 1: first loop condition
            IrInstr.Label(loop1Label),
            IrInstr.BinaryOp(2, TokenType.LT, 0, 1),
            IrInstr.JumpIfFalse(2, loop1End),

            // Block 2: first loop body - second loop init
            IrInstr.LoadImm(3, 0),
            IrInstr.LoadImm(4, 3),

            // Block 3: second loop condition
            IrInstr.Label(loop2Label),
            IrInstr.BinaryOp(5, TokenType.LT, 3, 4),
            IrInstr.JumpIfFalse(5, loop2End),

            // Block 4: second loop body
            IrInstr.BinaryOp(3, TokenType.PLUS, 3, 3),
            IrInstr.Jump(loop2Label),

            // Block 5: second loop end
            IrInstr.Label(loop2End),
            IrInstr.BinaryOp(0, TokenType.PLUS, 0, 0),
            IrInstr.Jump(loop1Label),

            // Block 6: first loop end
            IrInstr.Label(loop1End),
            IrInstr.Return(0)
        )

        val cfg = ControlFlowGraph.build(instrs)

        println("Multiple loops CFG:")
        println(cfg.dump())

        val loops = cfg.naturalLoops()
        println("Detected ${loops.size} loops:")
        for ((i, loop) in loops.withIndex()) {
            println("  Loop $i: header=Block${loop.header}, body=${loop.body}")
        }

        // Should detect 2 loops
        assertEquals(2, loops.size, "Should detect 2 loops")
    }
}
