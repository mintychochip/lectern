package org.quill.ast

import org.quill.lang.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LivenessAnalyzerTest {

    private val analyzer = LivenessAnalyzer()

    // -------------------------------------------------------------------------
    // 1. Simple linear sequence
    // -------------------------------------------------------------------------

    @Test
    fun `simple linear sequence - basic define and use`() {
        // idx 0: LoadImm r0
        // idx 1: LoadImm r1
        // idx 2: BinaryOp r2 = r0 + r1
        // idx 3: Return r2
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.BinaryOp(dst = 2, op = TokenType.PLUS, src1 = 0, src2 = 1),
            IrInstr.Return(src = 2)
        )
        val ranges = analyzer.analyze(instrs)

        // r0: defined at 0, last used at 2 (BinaryOp src1)
        assertEquals(LiveRange(reg = 0, start = 0, end = 2), ranges[0])
        // r1: defined at 1, last used at 2 (BinaryOp src2)
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
        // r2: defined at 2, last used at 3 (Return src)
        assertEquals(LiveRange(reg = 2, start = 2, end = 3), ranges[2])
    }

    // -------------------------------------------------------------------------
    // 2. Unused register (defined but never subsequently used)
    // -------------------------------------------------------------------------

    @Test
    fun `unused register - defined but never used after definition`() {
        // idx 0: LoadImm r0
        // idx 1: LoadImm r1    <- r1 is never referenced again
        // idx 2: Return r0
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.Return(src = 0)
        )
        val ranges = analyzer.analyze(instrs)

        // r0: defined at 0, used at 2
        assertEquals(LiveRange(reg = 0, start = 0, end = 2), ranges[0])
        // r1: defined at 1, never used → end stays at definition index 1
        assertEquals(LiveRange(reg = 1, start = 1, end = 1), ranges[1])
    }

    // -------------------------------------------------------------------------
    // 3. Register reuse / single define + single use
    // -------------------------------------------------------------------------

    @Test
    fun `register reuse pattern - define at 0 used at 2`() {
        // idx 0: LoadImm r0
        // idx 1: LoadImm r1
        // idx 2: BinaryOp r0 = r0 + r1   <- r0 used as src1 at idx 2
        // idx 3: Return r0
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.BinaryOp(dst = 0, op = TokenType.PLUS, src1 = 0, src2 = 1),
            IrInstr.Return(src = 0)
        )
        val ranges = analyzer.analyze(instrs)

        // r0 is used as src1 at idx 2, which updates end to 2; then it is
        // redefined as dst at 2 (getOrPut won't overwrite the existing entry),
        // and finally used as Return.src at 3 → end = 3
        assertEquals(0, ranges[0]!!.start)
        assertEquals(3, ranges[0]!!.end)
        // r1: defined at 1, used at 2
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
    }

    // -------------------------------------------------------------------------
    // 4. Loop extension via backward jump
    // -------------------------------------------------------------------------

    @Test
    fun `loop extension - backward jump extends live ranges inside loop`() {
        // idx 0: LoadImm r0
        // idx 1: Label L0          <- loop header (id=0)
        // idx 2: BinaryOp r1 = r0 LT r2
        // idx 3: JumpIfFalse r1 L1
        // idx 4: BinaryOp r0 = r0 + r3
        // idx 5: Jump L0           <- backward jump → loop [1, 5]
        // idx 6: Label L1
        // idx 7: Return r0
        val l0 = IrLabel(id = 0)
        val l1 = IrLabel(id = 1)
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),         // 0
            IrInstr.Label(label = l0),                    // 1
            IrInstr.BinaryOp(dst = 1, op = TokenType.LT, src1 = 0, src2 = 2), // 2
            IrInstr.JumpIfFalse(src = 1, target = l1),   // 3
            IrInstr.BinaryOp(dst = 0, op = TokenType.PLUS, src1 = 0, src2 = 3), // 4
            IrInstr.Jump(target = l0),                    // 5
            IrInstr.Label(label = l1),                    // 6
            IrInstr.Return(src = 0)                       // 7
        )
        val ranges = analyzer.analyze(instrs)

        // r0 is defined before the loop (idx 0) and used inside the loop body.
        // The backward jump is at idx 5, loop start (l0 label) is at idx 1.
        // r0 satisfies: start(0) < loopStart(1) AND end >= loopStart(1) AND end <= loopEnd(5)
        // so its live range should be extended to loopEnd = 5, and then
        // the Return at idx 7 further extends it to 7.
        val r0 = ranges[0]!!
        assertEquals(0, r0.start)
        // end must be at least the loop-end boundary (5); Return at 7 pushes it to 7
        assertEquals(7, r0.end)

        // r1 is defined at 2 (inside loop), used at 3 (inside loop)
        // r1.start=2 is NOT < loopStart=1, so no extension applies
        assertTrue(ranges[1]!!.start >= 1)
    }

    // -------------------------------------------------------------------------
    // 5. Multiple uses – end extends to last use
    // -------------------------------------------------------------------------

    @Test
    fun `multiple uses - end extends to last use of register`() {
        // idx 0: LoadImm r0
        // idx 1: BinaryOp r1 = r0 + r0
        // idx 2: BinaryOp r2 = r0 + r1
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.BinaryOp(dst = 1, op = TokenType.PLUS, src1 = 0, src2 = 0),
            IrInstr.BinaryOp(dst = 2, op = TokenType.PLUS, src1 = 0, src2 = 1)
        )
        val ranges = analyzer.analyze(instrs)

        // r0 used at idx 1 (src1 & src2) and at idx 2 (src1) → last use = 2
        assertEquals(LiveRange(reg = 0, start = 0, end = 2), ranges[0])
        // r1 defined at 1, used at 2 as src2
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
        // r2 defined at 2, never used
        assertEquals(LiveRange(reg = 2, start = 2, end = 2), ranges[2])
    }

    // -------------------------------------------------------------------------
    // 6. Call instruction – func and all args tracked as uses
    // -------------------------------------------------------------------------

    @Test
    fun `call instruction - func reg and all arg regs are recorded as uses`() {
        // idx 0: LoadImm r0  (func reg)
        // idx 1: LoadImm r1  (arg 0)
        // idx 2: LoadImm r2  (arg 1)
        // idx 3: Call r3 = r0(r1, r2)
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.LoadImm(dst = 2, index = 2),
            IrInstr.Call(dst = 3, func = 0, args = listOf(1, 2))
        )
        val ranges = analyzer.analyze(instrs)

        // func reg r0: defined at 0, used as func at idx 3
        assertEquals(LiveRange(reg = 0, start = 0, end = 3), ranges[0])
        // arg r1: defined at 1, used at idx 3
        assertEquals(LiveRange(reg = 1, start = 1, end = 3), ranges[1])
        // arg r2: defined at 2, used at idx 3
        assertEquals(LiveRange(reg = 2, start = 2, end = 3), ranges[2])
        // dst r3: defined at 3, never subsequently used
        assertEquals(LiveRange(reg = 3, start = 3, end = 3), ranges[3])
    }

    @Test
    fun `call instruction with no args - only func reg tracked`() {
        // idx 0: LoadImm r0  (func reg)
        // idx 1: Call r1 = r0()
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.Call(dst = 1, func = 0, args = emptyList())
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 1), ranges[1])
    }

    // -------------------------------------------------------------------------
    // 7. StoreGlobal – src is tracked as use
    // -------------------------------------------------------------------------

    @Test
    fun `StoreGlobal - src register is recorded as a use`() {
        // idx 0: LoadImm r0
        // idx 1: StoreGlobal "x" r0
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.StoreGlobal(name = "x", src = 0)
        )
        val ranges = analyzer.analyze(instrs)

        // r0 defined at 0, used as StoreGlobal src at idx 1
        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
    }

    @Test
    fun `StoreGlobal after several intervening instructions`() {
        // idx 0: LoadImm r0
        // idx 1: LoadImm r1
        // idx 2: BinaryOp r2 = r0 + r1
        // idx 3: StoreGlobal "result" r2
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.BinaryOp(dst = 2, op = TokenType.PLUS, src1 = 0, src2 = 1),
            IrInstr.StoreGlobal(name = "result", src = 2)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(3, ranges[2]!!.end)
    }

    // -------------------------------------------------------------------------
    // 8. Move – defines dst and uses src
    // -------------------------------------------------------------------------

    @Test
    fun `Move instruction - defines dst and uses src`() {
        // idx 0: LoadImm r0
        // idx 1: Move r1 = r0
        // idx 2: Return r1
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.Move(dst = 1, src = 0),
            IrInstr.Return(src = 1)
        )
        val ranges = analyzer.analyze(instrs)

        // r0: defined at 0, used as Move.src at idx 1
        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
        // r1: defined at 1 (Move.dst), used at 2 (Return.src)
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
    }

    @Test
    fun `Move instruction - src defined later than dst first use`() {
        // idx 0: LoadImm r5
        // idx 1: Move r6 = r5
        // idx 2: LoadImm r7
        // idx 3: BinaryOp r8 = r6 + r7
        val instrs = listOf(
            IrInstr.LoadImm(dst = 5, index = 0),
            IrInstr.Move(dst = 6, src = 5),
            IrInstr.LoadImm(dst = 7, index = 1),
            IrInstr.BinaryOp(dst = 8, op = TokenType.PLUS, src1 = 6, src2 = 7)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 5, start = 0, end = 1), ranges[5])
        assertEquals(LiveRange(reg = 6, start = 1, end = 3), ranges[6])
        assertEquals(LiveRange(reg = 7, start = 2, end = 3), ranges[7])
        assertEquals(LiveRange(reg = 8, start = 3, end = 3), ranges[8])
    }

    // -------------------------------------------------------------------------
    // 9. Empty instruction list
    // -------------------------------------------------------------------------

    @Test
    fun `empty instruction list - returns empty map`() {
        val ranges = analyzer.analyze(emptyList())
        assertTrue(ranges.isEmpty(), "Expected empty map but got: $ranges")
    }

    // -------------------------------------------------------------------------
    // Additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `LoadGlobal defines dst register`() {
        // idx 0: LoadGlobal r0 "counter"
        // idx 1: Return r0
        val instrs = listOf(
            IrInstr.LoadGlobal(dst = 0, name = "counter"),
            IrInstr.Return(src = 0)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
    }

    @Test
    fun `UnaryOp - defines dst and uses src`() {
        // idx 0: LoadImm r0
        // idx 1: UnaryOp r1 = MINUS r0
        // idx 2: Return r1
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.UnaryOp(dst = 1, op = TokenType.MINUS, src = 0),
            IrInstr.Return(src = 1)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
    }

    @Test
    fun `NewArray - defines dst and uses all element registers`() {
        // idx 0: LoadImm r0
        // idx 1: LoadImm r1
        // idx 2: LoadImm r2
        // idx 3: NewArray r3 = [r0, r1, r2]
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.LoadImm(dst = 2, index = 2),
            IrInstr.NewArray(dst = 3, elements = listOf(0, 1, 2))
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 3), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 3), ranges[1])
        assertEquals(LiveRange(reg = 2, start = 2, end = 3), ranges[2])
        assertEquals(LiveRange(reg = 3, start = 3, end = 3), ranges[3])
    }

    @Test
    fun `GetIndex - defines dst and uses obj and index registers`() {
        // idx 0: LoadImm r0  (array)
        // idx 1: LoadImm r1  (index)
        // idx 2: GetIndex r2 = r0[r1]
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.GetIndex(dst = 2, obj = 0, index = 1)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 2), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
        assertEquals(LiveRange(reg = 2, start = 2, end = 2), ranges[2])
    }

    @Test
    fun `SetIndex - uses obj, index, and src registers`() {
        // idx 0: LoadImm r0  (array)
        // idx 1: LoadImm r1  (index)
        // idx 2: LoadImm r2  (value)
        // idx 3: SetIndex r0[r1] = r2
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.LoadImm(dst = 2, index = 2),
            IrInstr.SetIndex(obj = 0, index = 1, src = 2)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(3, ranges[0]!!.end)
        assertEquals(3, ranges[1]!!.end)
        assertEquals(3, ranges[2]!!.end)
    }

    @Test
    fun `GetField - defines dst and uses obj register`() {
        // idx 0: LoadImm r0  (object)
        // idx 1: GetField r1 = r0.name
        // idx 2: Return r1
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.GetField(dst = 1, obj = 0, name = "name"),
            IrInstr.Return(src = 1)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
    }

    @Test
    fun `SetField - uses obj and src registers`() {
        // idx 0: LoadImm r0  (object)
        // idx 1: LoadImm r1  (value)
        // idx 2: SetField r0.score = r1
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.LoadImm(dst = 1, index = 1),
            IrInstr.SetField(obj = 0, name = "score", src = 1)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 2), ranges[0])
        assertEquals(LiveRange(reg = 1, start = 1, end = 2), ranges[1])
    }

    @Test
    fun `JumpIfFalse - uses src register`() {
        // idx 0: LoadImm r0
        // idx 1: JumpIfFalse r0 L0
        val l0 = IrLabel(id = 99)
        val instrs = listOf(
            IrInstr.LoadImm(dst = 0, index = 0),
            IrInstr.JumpIfFalse(src = 0, target = l0)
        )
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 0, start = 0, end = 1), ranges[0])
    }

    @Test
    fun `Label and Jump alone do not create range entries`() {
        val l0 = IrLabel(id = 5)
        val instrs = listOf(
            IrInstr.Label(label = l0),
            IrInstr.Jump(target = l0)
        )
        val ranges = analyzer.analyze(instrs)

        assertTrue(ranges.isEmpty(), "Expected no register ranges from Label/Jump only, got: $ranges")
    }

    @Test
    fun `loop with no outer-scope variables does not extend inner ranges`() {
        // r10 is defined inside the loop, not before it → no extension expected
        // idx 0: Label L0
        // idx 1: LoadImm r10
        // idx 2: Return r10
        // idx 3: Jump L0   <- backward jump; loopStart=0, loopEnd=3
        //
        // r10.start=1 is NOT < loopStart=0, so the extension condition is false
        val l0 = IrLabel(id = 0)
        val instrs = listOf(
            IrInstr.Label(label = l0),             // 0
            IrInstr.LoadImm(dst = 10, index = 0),  // 1
            IrInstr.Return(src = 10),               // 2
            IrInstr.Jump(target = l0)               // 3
        )
        val ranges = analyzer.analyze(instrs)

        // r10: defined at 1, used at 2 → end=2; start(1) >= loopStart(0), no extension
        assertEquals(LiveRange(reg = 10, start = 1, end = 2), ranges[10])
    }

    @Test
    fun `single instruction LoadImm only - one entry in map`() {
        val instrs = listOf(IrInstr.LoadImm(dst = 0, index = 0))
        val ranges = analyzer.analyze(instrs)

        assertEquals(1, ranges.size)
        assertEquals(LiveRange(reg = 0, start = 0, end = 0), ranges[0])
    }

    @Test
    fun `return alone on undefined reg - creates range starting at its index`() {
        // IrInstr.Return uses a register not previously defined via define().
        // The `use` helper creates a new LiveRange(reg, idx, idx) via getOrPut.
        // idx 0: Return r7
        val instrs = listOf(IrInstr.Return(src = 7))
        val ranges = analyzer.analyze(instrs)

        assertEquals(LiveRange(reg = 7, start = 0, end = 0), ranges[7])
    }
}
