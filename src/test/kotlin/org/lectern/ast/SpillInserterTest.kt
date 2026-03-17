package org.lectern.ast

import org.lectern.lang.IrInstr
import org.lectern.lang.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpillInserterTest {

    /**
     * Non-spill path: two virtual regs, simple BinaryOp, no spilling needed.
     * SpillInserter should just map virtuals → physicals via allocation.
     */
    @Test
    fun testNoSpill() {
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 1),
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),
            IrInstr.Return(2)
        )
        val ranges = LivenessAnalyzer().analyze(instrs)
        val allocResult = RegisterAllocator().allocate(ranges)
        assertEquals(emptyMap(), allocResult.spills)

        val resolved = SpillInserter().insert(instrs, allocResult, ranges)

        assertEquals(instrs.size, resolved.size)
        assert(resolved.none { it is IrInstr.Spill || it is IrInstr.Unspill })
    }

    /**
     * Spill path: v17 is defined early (long range) so the allocator spills it.
     * v0-v14 are all used together at the NewArray instruction — v17 is NOT used there.
     * At v17's use point (BinaryOp), v0-v14 are all dead → many free temp registers exist.
     * SpillInserter must succeed and inject Unspill/Spill around the BinaryOp.
     *
     * Instruction layout:
     *   idx 0:    LoadImm(v17, 0)             — v17 defined early, long range
     *   idx 1-15: LoadImm(v0..v14, 0)         — v0-v14 defined (15 virtuals)
     *   idx 16:   NewArray(v15, [v0..v14])     — uses v0-v14 (they all die); v17 NOT used here
     *   idx 17:   BinaryOp(v16, +, v15, v17)  — v17 finally used; only v15 also live → many free temps
     *   idx 18:   Return(v16)
     *
     * Peak simultaneous live virtuals = v17 + v0..v14 = 16 → exactly one spill (v17, longest range).
     * v0..v14 all expire with strict end < range.start before v16 is processed at idx 17,
     * so no second spill occurs.
     */
    @Test
    fun testSpillInjected() {
        val instrs = buildList {
            add(IrInstr.LoadImm(17, 0))                        // v17 at idx 0 (long range)
            for (i in 0..14) add(IrInstr.LoadImm(i, 0))       // v0-v14 at idx 1-15
            add(IrInstr.NewArray(15, (0..14).toList()))        // idx 16: uses v0-v14
            add(IrInstr.BinaryOp(16, TokenType.PLUS, 15, 17)) // idx 17: uses v15 + spilled v17
            add(IrInstr.Return(16))
        }
        val ranges = LivenessAnalyzer().analyze(instrs)
        val allocResult = RegisterAllocator().allocate(ranges)

        // v17 must be spilled (longest range when all 16 regs are occupied at NewArray)
        assert(allocResult.spills.isNotEmpty()) { "Expected at least one spill" }
        assert(17 in allocResult.spills) { "Expected v17 to be spilled" }

        // SpillInserter must succeed — v17's use point (BinaryOp) has free temp regs
        val resolved = SpillInserter().insert(instrs, allocResult, ranges)

        // Must contain Unspill (before BinaryOp) and Spill (after BinaryOp) for v17
        assert(resolved.any { it is IrInstr.Unspill }) { "Expected Unspill for spilled v17" }
        assert(resolved.any { it is IrInstr.Spill })   { "Expected Spill for spilled v17" }

        // All register operands in the resolved list must be physical (0-15)
        resolved.forEach { instr ->
            when (instr) {
                is IrInstr.LoadImm    -> assert(instr.dst in 0..15)
                is IrInstr.NewArray   -> {
                    assert(instr.dst in 0..15)
                    instr.elements.forEach { e -> assert(e in 0..15) }
                }
                is IrInstr.BinaryOp   -> {
                    assert(instr.dst in 0..15)
                    assert(instr.src1 in 0..15)
                    assert(instr.src2 in 0..15)
                }
                is IrInstr.Return     -> assert(instr.src in 0..15)
                is IrInstr.Unspill    -> assert(instr.dst in 0..15)
                is IrInstr.Spill      -> assert(instr.src in 0..15)
                else -> {}
            }
        }
    }

    /**
     * Pressure error: a spilled virtual is used at an instruction where all 16
     * physical registers are simultaneously occupied → SpillInserter must throw.
     *
     * Instruction layout:
     *   idx 0-15:  LoadImm(v0..v15, 0)         — 16 virtuals defined
     *   idx 16:    NewArray(v16, [v0..v15])     — uses v0-v15 (all 16 physicals live);
     *                                              allocator spills one of v0-v15 to free a
     *                                              register for v16. At idx 16, livePhysAt = {0..15}.
     *   idx 17:    Return(v16)
     *
     * The spilled virtual (one of v0-v15) is used as a NewArray element at idx 16.
     * At that point all 16 physical registers are occupied → no free temp → error.
     */
    @Test
    fun testRegisterPressureError() {
        val instrs = buildList {
            for (i in 0..15) add(IrInstr.LoadImm(i, 0))
            add(IrInstr.NewArray(16, (0..15).toList()))
            add(IrInstr.Return(16))
        }
        val ranges = LivenessAnalyzer().analyze(instrs)
        val allocResult = RegisterAllocator().allocate(ranges)
        assert(allocResult.spills.isNotEmpty()) { "Expected spilling for 17 simultaneously live virtuals" }

        assertFailsWith<IllegalStateException> {
            SpillInserter().insert(instrs, allocResult, ranges)
        }
    }
}
