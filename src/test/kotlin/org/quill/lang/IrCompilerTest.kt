package org.quill.lang

import org.quill.ast.AstLowerer
import kotlin.test.Test
import kotlin.test.assertEquals

class IrCompilerTest {

    // ---------------------------------------------------------------------------
    // Helpers to decode packed instruction words
    // ---------------------------------------------------------------------------

    private fun opcode(word: Int) = word and 0xFF
    private fun dst(word: Int)    = (word shr 8)  and 0x0F
    private fun src1(word: Int)   = (word shr 12) and 0x0F
    private fun src2(word: Int)   = (word shr 16) and 0x0F
    private fun imm(word: Int)    = (word shr 20) and 0xFFF

    private fun compile(
        instrs: List<IrInstr>,
        constants: List<Value> = emptyList()
    ): Chunk = IrCompiler().compile(AstLowerer.LoweredResult(instrs, constants))

    // ---------------------------------------------------------------------------
    // 1. LoadImm + Return
    // ---------------------------------------------------------------------------

    @Test
    fun testLoadImm_andReturn() {
        val constants = listOf(Value.Int(42))
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.Return(0)
        )
        val chunk = compile(instrs, constants)

        assertEquals(2, chunk.code.size, "Expected exactly 2 code words")

        val loadWord = chunk.code[0]
        assertEquals(OpCode.LOAD_IMM.code.toInt() and 0xFF, opcode(loadWord), "First word opcode should be LOAD_IMM")
        assertEquals(0, dst(loadWord), "LoadImm dst should be 0")
        assertEquals(0, imm(loadWord), "LoadImm imm (constant index) should be 0")

        val retWord = chunk.code[1]
        assertEquals(OpCode.RETURN.code.toInt() and 0xFF, opcode(retWord), "Second word opcode should be RETURN")
        assertEquals(0, src1(retWord), "Return src1 should be 0")

        assertEquals(1, chunk.constants.size, "Chunk should carry the one constant")
        assertEquals(Value.Int(42), chunk.constants[0], "Constant should be Int(42)")
    }

    // ---------------------------------------------------------------------------
    // 2. BinaryOp (ADD)
    // ---------------------------------------------------------------------------

    @Test
    fun testBinaryOp_add() {
        val constants = listOf(Value.Int(1), Value.Int(2))
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 1),
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),
            IrInstr.Return(2)
        )
        val chunk = compile(instrs, constants)

        assertEquals(4, chunk.code.size, "Expected 4 code words")

        val addWord = chunk.code[2]
        assertEquals(OpCode.ADD.code.toInt() and 0xFF, opcode(addWord), "Third word opcode should be ADD")
        assertEquals(2, dst(addWord),  "ADD dst should be 2")
        assertEquals(0, src1(addWord), "ADD src1 should be 0")
        assertEquals(1, src2(addWord), "ADD src2 should be 1")
    }

    // ---------------------------------------------------------------------------
    // 3. Move instruction
    // ---------------------------------------------------------------------------

    @Test
    fun testMove() {
        val constants = listOf(Value.Int(7))
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.Move(1, 0),
            IrInstr.Return(1)
        )
        val chunk = compile(instrs, constants)

        assertEquals(3, chunk.code.size, "Expected 3 code words")

        val moveWord = chunk.code[1]
        assertEquals(OpCode.MOVE.code.toInt() and 0xFF, opcode(moveWord), "Second word opcode should be MOVE")
        assertEquals(1, dst(moveWord),  "MOVE dst should be 1")
        assertEquals(0, src1(moveWord), "MOVE src1 should be 0")
    }

    // ---------------------------------------------------------------------------
    // 4. Jump + Label  (unconditional)
    // ---------------------------------------------------------------------------

    @Test
    fun testJump_andLabel() {
        val label = IrLabel(0)
        // Jump resolves to the Return (which is at code offset 1, since Label emits nothing)
        val instrs = listOf(
            IrInstr.Jump(label),
            IrInstr.Label(label),
            IrInstr.Return(0)
        )
        val chunk = compile(instrs)

        // Label emits no code, so we expect 2 words: JUMP + RETURN
        assertEquals(2, chunk.code.size, "Label emits no code word; expected 2 instructions")

        val jumpWord = chunk.code[0]
        assertEquals(OpCode.JUMP.code.toInt() and 0xFF, opcode(jumpWord), "First word opcode should be JUMP")
        assertEquals(1, imm(jumpWord), "JUMP imm should point to Return at index 1")
    }

    // ---------------------------------------------------------------------------
    // 5. JumpIfFalse + Label  (conditional)
    // ---------------------------------------------------------------------------

    @Test
    fun testJumpIfFalse_andLabel() {
        val label = IrLabel(0)
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.JumpIfFalse(0, label),
            IrInstr.Label(label),
            IrInstr.Return(0)
        )
        val chunk = compile(instrs, listOf(Value.Boolean(false)))

        // Label emits no code: LOAD_IMM + JUMP_IF_FALSE + RETURN = 3 words
        assertEquals(3, chunk.code.size, "Expected 3 code words (Label skipped)")

        val jifWord = chunk.code[1]
        assertEquals(OpCode.JUMP_IF_FALSE.code.toInt() and 0xFF, opcode(jifWord), "Second word opcode should be JUMP_IF_FALSE")
        assertEquals(0, src1(jifWord), "JUMP_IF_FALSE src1 should be 0")
        // Label is at code offset 2 (after LOAD_IMM + JUMP_IF_FALSE)
        assertEquals(2, imm(jifWord), "JUMP_IF_FALSE imm should point to Return at index 2")
    }

    // ---------------------------------------------------------------------------
    // 6. StoreGlobal / LoadGlobal — strings table
    // ---------------------------------------------------------------------------

    @Test
    fun testStoreGlobal_populatesStrings() {
        val constants = listOf(Value.Int(99))
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.StoreGlobal("myVar", 0),
            IrInstr.Return(0)
        )
        val chunk = compile(instrs, constants)

        assertEquals(3, chunk.code.size)

        val storeWord = chunk.code[1]
        assertEquals(OpCode.STORE_GLOBAL.code.toInt() and 0xFF, opcode(storeWord), "Second word opcode should be STORE_GLOBAL")
        assertEquals(0, src1(storeWord), "STORE_GLOBAL src1 should be register 0")
        val strIdx = imm(storeWord)
        assertEquals("myVar", chunk.strings[strIdx], "String at imm index should be 'myVar'")
    }

    @Test
    fun testLoadGlobal_populatesStrings() {
        val instrs = listOf(
            IrInstr.LoadGlobal(0, "counter"),
            IrInstr.Return(0)
        )
        val chunk = compile(instrs)

        val loadWord = chunk.code[0]
        assertEquals(OpCode.LOAD_GLOBAL.code.toInt() and 0xFF, opcode(loadWord), "First word opcode should be LOAD_GLOBAL")
        val strIdx = imm(loadWord)
        assertEquals("counter", chunk.strings[strIdx], "String at imm index should be 'counter'")
    }

    // ---------------------------------------------------------------------------
    // 7. Call with args — PUSH_ARG emitted before CALL
    // ---------------------------------------------------------------------------

    @Test
    fun testCall_withArgs_emitsPushArgBeforeCall() {
        val constants = listOf(Value.Int(10), Value.Int(20))
        // func in reg 0, args in regs 1 and 2, result in reg 3
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 1),
            IrInstr.Call(3, 0, listOf(1, 2)),
            IrInstr.Return(3)
        )
        val chunk = compile(instrs, constants)

        // LOAD_IMM, LOAD_IMM, PUSH_ARG(r1), PUSH_ARG(r2), CALL, RETURN  => 6 words
        assertEquals(6, chunk.code.size, "Expected 6 words: 2 loads + 2 PUSH_ARGs + CALL + RETURN")

        val pushArg0 = chunk.code[2]
        assertEquals(OpCode.PUSH_ARG.code.toInt() and 0xFF, opcode(pushArg0), "Third word should be PUSH_ARG")
        assertEquals(1, src1(pushArg0), "First PUSH_ARG src1 should be register 1")

        val pushArg1 = chunk.code[3]
        assertEquals(OpCode.PUSH_ARG.code.toInt() and 0xFF, opcode(pushArg1), "Fourth word should be PUSH_ARG")
        assertEquals(2, src1(pushArg1), "Second PUSH_ARG src1 should be register 2")

        val callWord = chunk.code[4]
        assertEquals(OpCode.CALL.code.toInt() and 0xFF, opcode(callWord), "Fifth word should be CALL")
        assertEquals(3, dst(callWord),  "CALL dst should be 3")
        assertEquals(0, src1(callWord), "CALL src1 (func reg) should be 0")
        assertEquals(2, imm(callWord),  "CALL imm (arg count) should be 2")
    }

    // ---------------------------------------------------------------------------
    // 8. NewArray — PUSH_ARG per element, then NEW_ARRAY
    // ---------------------------------------------------------------------------

    @Test
    fun testNewArray_emitsPushArgForEachElement() {
        val constants = listOf(Value.Int(1), Value.Int(2))
        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.LoadImm(1, 1),
            IrInstr.NewArray(2, listOf(0, 1)),
            IrInstr.Return(2)
        )
        val chunk = compile(instrs, constants)

        // LOAD_IMM, LOAD_IMM, PUSH_ARG(r0), PUSH_ARG(r1), NEW_ARRAY, RETURN  => 6 words
        assertEquals(6, chunk.code.size, "Expected 6 words: 2 loads + 2 PUSH_ARGs + NEW_ARRAY + RETURN")

        val pushElem0 = chunk.code[2]
        assertEquals(OpCode.PUSH_ARG.code.toInt() and 0xFF, opcode(pushElem0), "Third word should be PUSH_ARG for element 0")
        assertEquals(0, src1(pushElem0), "First element PUSH_ARG src1 should be register 0")

        val pushElem1 = chunk.code[3]
        assertEquals(OpCode.PUSH_ARG.code.toInt() and 0xFF, opcode(pushElem1), "Fourth word should be PUSH_ARG for element 1")
        assertEquals(1, src1(pushElem1), "Second element PUSH_ARG src1 should be register 1")

        val newArrayWord = chunk.code[4]
        assertEquals(OpCode.NEW_ARRAY.code.toInt() and 0xFF, opcode(newArrayWord), "Fifth word should be NEW_ARRAY")
        assertEquals(2, dst(newArrayWord), "NEW_ARRAY dst should be 2")
        assertEquals(2, imm(newArrayWord), "NEW_ARRAY imm (element count) should be 2")
    }
}
