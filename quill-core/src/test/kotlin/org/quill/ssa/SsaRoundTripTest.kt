package org.quill.ssa

import org.quill.lang.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SsaRoundTripTest {

    // Helper to create unique labels
    private var labelCounter = 0
    private fun label(): IrLabel = IrLabel(labelCounter++)

    private fun roundTrip(instrs: List<IrInstr>, constants: List<Value>, arity: Int = 0): List<IrInstr> {
        val ssaFunc = SsaBuilder.build(instrs, constants, arity)
        return SsaDeconstructor.deconstruct(ssaFunc)
    }

    // -------------------------------------------------------------------------
    // Test 1: Linear block round-trip
    // -------------------------------------------------------------------------

    /**
     * Simplest possible program: load two constants, add them, return.
     * No branches, no loops — single basic block.
     * Verifies that the deconstructor emits the same number of instructions
     * with the same types, and that Return is last.
     */
    @Test
    fun testLinearBlockRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.Int(3), Value.Int(7))

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),                        // r0 = 3
            IrInstr.LoadImm(1, 1),                        // r1 = 7
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),   // r2 = r0 + r1
            IrInstr.Return(2)
        )

        val result = roundTrip(instrs, constants)

        // Instruction count must be preserved
        assertEquals(instrs.size, result.size, "Linear block: instruction count must be preserved")

        // Types must match positionally
        assertTrue(result[0] is IrInstr.LoadImm,  "instr[0] should be LoadImm")
        assertTrue(result[1] is IrInstr.LoadImm,  "instr[1] should be LoadImm")
        assertTrue(result[2] is IrInstr.BinaryOp, "instr[2] should be BinaryOp")
        assertTrue(result[3] is IrInstr.Return,   "instr[3] should be Return")

        // Constant indices must be preserved
        assertEquals(0, (result[0] as IrInstr.LoadImm).index, "First LoadImm index must be 0")
        assertEquals(1, (result[1] as IrInstr.LoadImm).index, "Second LoadImm index must be 1")

        // BinaryOp operator must be preserved
        assertEquals(TokenType.PLUS, (result[2] as IrInstr.BinaryOp).op, "BinaryOp operator must be PLUS")

        // Return is last
        assertTrue(result.last() is IrInstr.Return, "Last instruction must be Return")
    }

    // -------------------------------------------------------------------------
    // Test 2: If-else branch round-trip
    // -------------------------------------------------------------------------

    /**
     * if-else with a value merge:
     *   cond = constants[0]
     *   if cond goto lThen else lElse
     *   lThen: x = constants[1]; goto lMerge
     *   lElse: x = constants[2]
     *   lMerge: return x
     *
     * SSA must insert a phi at lMerge; deconstruction must resolve it with
     * Move instructions so that the final Return still works.
     */
    @Test
    fun testIfElseBranchRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.Int(1), Value.Int(2), Value.Int(3))

        val lElse  = label()
        val lThen  = label()
        val lMerge = label()

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),           // cond = 1
            IrInstr.JumpIfFalse(0, lElse),
            IrInstr.Label(lThen),
            IrInstr.LoadImm(1, 1),           // x = 2 (then branch)
            IrInstr.Jump(lMerge),
            IrInstr.Label(lElse),
            IrInstr.LoadImm(1, 2),           // x = 3 (else branch)
            IrInstr.Label(lMerge),
            IrInstr.Return(1)
        )

        val result = roundTrip(instrs, constants)

        // Must still end with Return
        assertTrue(result.last() is IrInstr.Return, "If-else: last instruction must be Return")

        // Phi resolution means we need Move instructions to copy the chosen branch value
        // into the phi-result register before reaching the merge block
        val moves = result.filterIsInstance<IrInstr.Move>()
        assertTrue(moves.isNotEmpty(), "If-else: phi resolution must introduce at least one Move")

        // Both LoadImm constant indices must still be present in the output
        val loadImms = result.filterIsInstance<IrInstr.LoadImm>()
        val indices = loadImms.map { it.index }.toSet()
        assertTrue(1 in indices, "If-else: constant index 1 (then-branch value) must survive")
        assertTrue(2 in indices, "If-else: constant index 2 (else-branch value) must survive")
    }

    // -------------------------------------------------------------------------
    // Test 3: Loop round-trip
    // -------------------------------------------------------------------------

    /**
     * Counting loop:
     *   i = 0; limit = 10; step = 1
     *   lHeader: if i < limit goto body else lExit
     *   body:    i = i + step; goto lHeader
     *   lExit:   return i
     *
     * The loop header gets a phi for i. After deconstruction the phi is
     * resolved into a Move inserted in the back-edge predecessor.
     * Return must still appear as the last non-label instruction.
     */
    @Test
    fun testLoopRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.Int(0), Value.Int(10), Value.Int(1))

        val lHeader = label()
        val lExit   = label()

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),                       // i     = 0
            IrInstr.LoadImm(1, 1),                       // limit = 10
            IrInstr.LoadImm(2, 2),                       // step  = 1
            IrInstr.Label(lHeader),                      // loop header
            IrInstr.BinaryOp(3, TokenType.LT, 0, 1),    // r3 = i < limit
            IrInstr.JumpIfFalse(3, lExit),
            IrInstr.BinaryOp(0, TokenType.PLUS, 0, 2),  // i = i + step
            IrInstr.Jump(lHeader),
            IrInstr.Label(lExit),
            IrInstr.Return(0)
        )

        val result = roundTrip(instrs, constants)

        // Must end with Return
        assertTrue(result.last() is IrInstr.Return, "Loop: last instruction must be Return")

        // Must still contain a BinaryOp with LT (loop condition)
        val hasLt = result.filterIsInstance<IrInstr.BinaryOp>().any { it.op == TokenType.LT }
        assertTrue(hasLt, "Loop: BinaryOp(LT) must survive the round-trip")

        // Must still contain a BinaryOp with PLUS (increment)
        val hasPlus = result.filterIsInstance<IrInstr.BinaryOp>().any { it.op == TokenType.PLUS }
        assertTrue(hasPlus, "Loop: BinaryOp(PLUS) must survive the round-trip")

        // Loop body back-edge must be preserved as a Jump
        val hasJump = result.filterIsInstance<IrInstr.Jump>().isNotEmpty()
        assertTrue(hasJump, "Loop: Jump (back-edge) must survive the round-trip")

        // The phi for i must have been resolved; look for the Move that copies
        // the updated i into the phi-result register on the back-edge path
        val moves = result.filterIsInstance<IrInstr.Move>()
        assertTrue(moves.isNotEmpty(), "Loop: phi for i must be resolved with at least one Move")
    }

    // -------------------------------------------------------------------------
    // Test 4: Multiple variables round-trip
    // -------------------------------------------------------------------------

    /**
     * Several independent LoadImm + BinaryOp chains in a single block.
     * Verifies that all instructions are preserved and that their types
     * remain correct after the round-trip.
     */
    @Test
    fun testMultipleVariablesRoundTrip() {
        labelCounter = 0
        val constants = listOf(
            Value.Int(1), Value.Int(2), Value.Int(3),
            Value.Int(4), Value.Int(5)
        )

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),                       // a = 1
            IrInstr.LoadImm(1, 1),                       // b = 2
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),  // c = a + b
            IrInstr.LoadImm(3, 2),                       // d = 3
            IrInstr.LoadImm(4, 3),                       // e = 4
            IrInstr.BinaryOp(5, TokenType.STAR, 3, 4),  // f = d * e
            IrInstr.LoadImm(6, 4),                       // g = 5
            IrInstr.BinaryOp(7, TokenType.MINUS, 5, 6), // h = f - g
            IrInstr.Return(7)
        )

        val result = roundTrip(instrs, constants)

        // Instruction count must match (single block, no phis)
        assertEquals(instrs.size, result.size, "Multi-var: instruction count must be preserved")

        // All three operators must survive
        val ops = result.filterIsInstance<IrInstr.BinaryOp>().map { it.op }.toSet()
        assertTrue(TokenType.PLUS  in ops, "Multi-var: PLUS operator must survive")
        assertTrue(TokenType.STAR  in ops, "Multi-var: STAR operator must survive")
        assertTrue(TokenType.MINUS in ops, "Multi-var: MINUS operator must survive")

        // All five constant indices must appear
        val loadedIndices = result.filterIsInstance<IrInstr.LoadImm>().map { it.index }.toSet()
        for (idx in 0..4) {
            assertTrue(idx in loadedIndices, "Multi-var: constant index $idx must survive")
        }

        assertTrue(result.last() is IrInstr.Return, "Multi-var: last instruction must be Return")
    }

    // -------------------------------------------------------------------------
    // Test 5: Nested branches round-trip
    // -------------------------------------------------------------------------

    /**
     * if inside if — two merge points each with their own phi:
     *
     *   a = 1
     *   if a goto outerThen else outerElse
     *   outerThen:
     *     b = 2
     *     if a goto innerThen else innerElse
     *     innerThen: x = 3; goto innerMerge
     *     innerElse: x = 4
     *     innerMerge: goto outerMerge
     *   outerElse:
     *     x = 5
     *   outerMerge:
     *   return x
     */
    @Test
    fun testNestedBranchesRoundTrip() {
        labelCounter = 0
        val constants = listOf(
            Value.Int(1), Value.Int(2), Value.Int(3),
            Value.Int(4), Value.Int(5)
        )

        val outerElse  = label()
        val outerThen  = label()
        val innerElse  = label()
        val innerThen  = label()
        val innerMerge = label()
        val outerMerge = label()

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),                // a = 1
            IrInstr.JumpIfFalse(0, outerElse),
            IrInstr.Label(outerThen),
            IrInstr.LoadImm(1, 1),                // b = 2  (only in outer-then)
            IrInstr.JumpIfFalse(0, innerElse),
            IrInstr.Label(innerThen),
            IrInstr.LoadImm(2, 2),                // x = 3  (inner-then)
            IrInstr.Jump(innerMerge),
            IrInstr.Label(innerElse),
            IrInstr.LoadImm(2, 3),                // x = 4  (inner-else)
            IrInstr.Jump(outerMerge),
            IrInstr.Label(innerMerge),
            IrInstr.Jump(outerMerge),
            IrInstr.Label(outerElse),
            IrInstr.LoadImm(2, 4),                // x = 5  (outer-else)
            IrInstr.Label(outerMerge),
            IrInstr.Return(2)
        )

        val result = roundTrip(instrs, constants)

        // Must still end with Return
        assertTrue(result.last() is IrInstr.Return, "Nested branches: last instruction must be Return")

        // Phi resolution must produce Moves
        val moves = result.filterIsInstance<IrInstr.Move>()
        assertTrue(moves.isNotEmpty(), "Nested branches: phis must be resolved with Move instructions")

        // All three x-assignment constant indices (2,3,4) must survive
        val loadedIndices = result.filterIsInstance<IrInstr.LoadImm>().map { it.index }.toSet()
        assertTrue(2 in loadedIndices, "Nested branches: constant index 2 (inner-then x=3) must survive")
        assertTrue(3 in loadedIndices, "Nested branches: constant index 3 (inner-else x=4) must survive")
        assertTrue(4 in loadedIndices, "Nested branches: constant index 4 (outer-else x=5) must survive")
    }

    // -------------------------------------------------------------------------
    // Test 6: Function-arity round-trip
    // -------------------------------------------------------------------------

    /**
     * Build with arity=2 so r0 and r1 are pre-assigned as parameters.
     * The single instruction in the function body adds them and returns the
     * result. After round-trip the BinaryOp sources must trace back to the
     * original parameter registers (0 and 1).
     */
    @Test
    fun testFunctionArityRoundTrip() {
        labelCounter = 0
        val constants = emptyList<Value>()

        // Function body: r2 = param0 + param1; return r2
        val instrs = listOf(
            IrInstr.BinaryOp(2, TokenType.PLUS, 0, 1),  // r2 = r0 + r1
            IrInstr.Return(2)
        )

        val result = roundTrip(instrs, constants, arity = 2)

        // Must still end with Return
        assertTrue(result.last() is IrInstr.Return, "Arity: last instruction must be Return")

        // Must still have a BinaryOp
        val binaryOps = result.filterIsInstance<IrInstr.BinaryOp>()
        assertEquals(1, binaryOps.size, "Arity: exactly one BinaryOp must survive")

        val op = binaryOps[0]
        assertEquals(TokenType.PLUS, op.op, "Arity: operator must be PLUS")

        // Parameters r0 and r1 should be pre-assigned to registers 0 and 1
        // by the deconstructor's arity-aware register assignment
        assertEquals(0, op.src1, "Arity: first source must be register 0 (param0)")
        assertEquals(1, op.src2, "Arity: second source must be register 1 (param1)")

        // Return register should be the result of the BinaryOp
        assertEquals(op.dst, (result.last() as IrInstr.Return).src,
            "Arity: Return must use the BinaryOp destination register")
    }

    // -------------------------------------------------------------------------
    // Test 7: StoreGlobal / LoadGlobal round-trip
    // -------------------------------------------------------------------------

    /**
     * Store a value to a global and load it back.
     * Verifies that global-name strings are preserved verbatim through
     * the SSA transform.
     */
    @Test
    fun testStoreLoadGlobalRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.Int(42))

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),           // r0 = 42
            IrInstr.StoreGlobal("x", 0),     // globals["x"] = r0
            IrInstr.LoadGlobal(1, "x"),      // r1 = globals["x"]
            IrInstr.Return(1)
        )

        val result = roundTrip(instrs, constants)

        // Must end with Return
        assertTrue(result.last() is IrInstr.Return, "Global: last instruction must be Return")

        // StoreGlobal must survive with correct name
        val stores = result.filterIsInstance<IrInstr.StoreGlobal>()
        assertEquals(1, stores.size, "Global: exactly one StoreGlobal must survive")
        assertEquals("x", stores[0].name, "Global: StoreGlobal name must be 'x'")

        // LoadGlobal must survive with correct name
        val loads = result.filterIsInstance<IrInstr.LoadGlobal>()
        assertEquals(1, loads.size, "Global: exactly one LoadGlobal must survive")
        assertEquals("x", loads[0].name, "Global: LoadGlobal name must be 'x'")

        // LoadImm with index 0 must survive
        val loadImms = result.filterIsInstance<IrInstr.LoadImm>()
        assertTrue(loadImms.any { it.index == 0 }, "Global: LoadImm(index=0) must survive")
    }

    // -------------------------------------------------------------------------
    // Test 8: Call instruction round-trip
    // -------------------------------------------------------------------------

    /**
     * Call a function obtained from globals:
     *   r0 = LoadGlobal("print")
     *   r1 = LoadImm(0)          // argument
     *   r2 = Call(r0, [r1])
     *   return r2
     *
     * Verifies that Call's argument list and function register survive.
     */
    @Test
    fun testCallRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.String("hello"))

        val instrs = listOf(
            IrInstr.LoadGlobal(0, "print"),   // r0 = print
            IrInstr.LoadImm(1, 0),            // r1 = "hello"
            IrInstr.Call(2, 0, listOf(1)),    // r2 = r0(r1)
            IrInstr.Return(2)
        )

        val result = roundTrip(instrs, constants)

        // Must end with Return
        assertTrue(result.last() is IrInstr.Return, "Call: last instruction must be Return")

        // Call must survive
        val calls = result.filterIsInstance<IrInstr.Call>()
        assertEquals(1, calls.size, "Call: exactly one Call instruction must survive")

        val call = calls[0]

        // The Call must have exactly one argument
        assertEquals(1, call.args.size, "Call: argument list must have exactly one element")

        // LoadGlobal for "print" must survive
        val loadGlobals = result.filterIsInstance<IrInstr.LoadGlobal>()
        assertTrue(loadGlobals.any { it.name == "print" }, "Call: LoadGlobal('print') must survive")

        // LoadImm for the argument must survive
        val loadImms = result.filterIsInstance<IrInstr.LoadImm>()
        assertTrue(loadImms.any { it.index == 0 }, "Call: LoadImm(index=0) must survive")

        // The Call's function register must match the LoadGlobal destination
        val printReg = loadGlobals.first { it.name == "print" }.dst
        assertEquals(printReg, call.func, "Call: func register must match LoadGlobal('print') destination")

        // The Call result register must match what Return uses
        assertEquals(call.dst, (result.last() as IrInstr.Return).src,
            "Call: Return must use the Call destination register")
    }

    // -------------------------------------------------------------------------
    // Structural sanity: empty instruction list
    // -------------------------------------------------------------------------

    /**
     * An empty program should produce an empty deconstruction without crashing.
     */
    @Test
    fun testEmptyProgramRoundTrip() {
        labelCounter = 0
        val result = roundTrip(emptyList(), emptyList())
        assertTrue(result.isEmpty(), "Empty program must round-trip to empty list")
    }

    // -------------------------------------------------------------------------
    // Structural sanity: single Return
    // -------------------------------------------------------------------------

    /**
     * A program that is just a Return of a LoadImm should survive unchanged
     * in terms of instruction count and types.
     */
    @Test
    fun testSingleReturnRoundTrip() {
        labelCounter = 0
        val constants = listOf(Value.Int(0))

        val instrs = listOf(
            IrInstr.LoadImm(0, 0),
            IrInstr.Return(0)
        )

        val result = roundTrip(instrs, constants)

        assertEquals(instrs.size, result.size, "Single-return: instruction count must be preserved")
        assertTrue(result[0] is IrInstr.LoadImm, "Single-return: first instr must be LoadImm")
        assertTrue(result[1] is IrInstr.Return,  "Single-return: second instr must be Return")
        assertTrue(result.last() is IrInstr.Return, "Single-return: last instruction must be Return")
    }
}
