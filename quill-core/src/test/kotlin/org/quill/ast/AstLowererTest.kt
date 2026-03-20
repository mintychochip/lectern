package org.quill.ast

import org.quill.lang.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AstLowererTest {

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun lowerSource(source: String): AstLowerer.LoweredResult {
        val tokens = tokenize(source)
        val stmts = Parser(tokens).parse()
        return AstLowerer().lower(stmts)
    }

    // ---------------------------------------------------------------------------
    // 1. Variable declaration: `let x = 5` → LoadImm for 5
    // ---------------------------------------------------------------------------

    @Test
    fun testVarDeclaration_producesLoadImm() {
        val result = lowerSource("let x = 5")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(1, loadImms.size, "Expected exactly one LoadImm")

        val constIndex = loadImms[0].index
        assertEquals(Value.Int(5), result.constants[constIndex], "Constant at index should be Int(5)")
    }

    // ---------------------------------------------------------------------------
    // 2. Variable assignment: `let x = 5\nx = 10` → two LoadImm, check constants
    // ---------------------------------------------------------------------------

    @Test
    fun testVarAssignment_twoLoadImms() {
        val result = lowerSource("let x = 5\nx = 10")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(2, loadImms.size, "Expected two LoadImm instructions")

        val values = loadImms.map { result.constants[it.index] }.toSet()
        assertTrue(Value.Int(5) in values, "Constant 5 should be present")
        assertTrue(Value.Int(10) in values, "Constant 10 should be present")
    }

    // ---------------------------------------------------------------------------
    // 3. Binary expression: `let x = 1 + 2` → LoadImm, LoadImm, BinaryOp(PLUS)
    // ---------------------------------------------------------------------------

    @Test
    fun testBinaryExpression_loadImmAndBinaryOp() {
        val result = lowerSource("let x = 1 + 2")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(2, loadImms.size, "Expected two LoadImm for operands")

        val binaryOps = result.instrs.filterIsInstance<IrInstr.BinaryOp>()
        assertEquals(1, binaryOps.size, "Expected one BinaryOp")
        assertEquals(TokenType.PLUS, binaryOps[0].op, "BinaryOp should use PLUS")

        val constValues = loadImms.map { result.constants[it.index] }.toSet()
        assertTrue(Value.Int(1) in constValues)
        assertTrue(Value.Int(2) in constValues)
    }

    // ---------------------------------------------------------------------------
    // 4. If statement: `let x = true\nif x { let y = 1 }` → JumpIfFalse, Label
    // ---------------------------------------------------------------------------

    @Test
    fun testIfStatement_jumpIfFalseAndLabel() {
        val result = lowerSource("let x = true\nif x { let y = 1 }")

        val jumpIfFalse = result.instrs.filterIsInstance<IrInstr.JumpIfFalse>()
        assertTrue(jumpIfFalse.isNotEmpty(), "Expected at least one JumpIfFalse")

        val labels = result.instrs.filterIsInstance<IrInstr.Label>()
        assertTrue(labels.isNotEmpty(), "Expected at least one Label")
    }

    // ---------------------------------------------------------------------------
    // 5. If-else: generates Jump to skip else, Label for else, Label for end
    // ---------------------------------------------------------------------------

    @Test
    fun testIfElse_jumpAndTwoLabels() {
        val result = lowerSource("let x = true\nif x { let y = 1 } else { let y = 2 }")

        // There should be a JumpIfFalse for the condition
        val jumpIfFalse = result.instrs.filterIsInstance<IrInstr.JumpIfFalse>()
        assertEquals(1, jumpIfFalse.size, "Expected one JumpIfFalse")

        // There should be an unconditional Jump to skip the else branch
        val jumps = result.instrs.filterIsInstance<IrInstr.Jump>()
        assertTrue(jumps.isNotEmpty(), "Expected at least one Jump (to skip else)")

        // Two labels: one marking the start of else, one marking the end
        val labels = result.instrs.filterIsInstance<IrInstr.Label>()
        assertEquals(2, labels.size, "Expected two Labels (else label and end label)")
        // Labels should be distinct
        val labelIds = labels.map { it.label.id }.toSet()
        assertEquals(2, labelIds.size, "Label ids should be distinct")
    }

    // ---------------------------------------------------------------------------
    // 6. While loop: `let x = true\nwhile x { let y = 1 }` → Label, JumpIfFalse, Jump back
    // ---------------------------------------------------------------------------

    @Test
    fun testWhileLoop_topLabelJumpIfFalseAndJumpBack() {
        val result = lowerSource("let x = true\nwhile x { let y = 1 }")

        // Top-of-loop label must come before JumpIfFalse
        val instrList = result.instrs
        val topLabelIdx = instrList.indexOfFirst { it is IrInstr.Label }
        assertTrue(topLabelIdx >= 0, "Expected a top-of-loop Label")

        val jumpIfFalseIdx = instrList.indexOfFirst { it is IrInstr.JumpIfFalse }
        assertTrue(jumpIfFalseIdx > topLabelIdx, "JumpIfFalse should follow the top label")

        // The unconditional Jump at the bottom should target the top label
        val topLabel = (instrList[topLabelIdx] as IrInstr.Label).label
        val jumps = instrList.filterIsInstance<IrInstr.Jump>()
        val backEdge = jumps.find { it.target == topLabel }
        assertTrue(backEdge != null, "Expected a back-edge Jump targeting the top-of-loop label")

        // End-of-loop label (JumpIfFalse target)
        val jif = instrList[jumpIfFalseIdx] as IrInstr.JumpIfFalse
        val endLabel = jif.target
        val endLabelInstr = instrList.filterIsInstance<IrInstr.Label>().find { it.label == endLabel }
        assertTrue(endLabelInstr != null, "Expected a Label instruction for the end-of-loop target")
    }

    // ---------------------------------------------------------------------------
    // 7. Function declaration: arity 2, nested instrs contain Return
    // ---------------------------------------------------------------------------

    @Test
    fun testFunctionDeclaration_loadFuncWithArityAndReturn() {
        val result = lowerSource("fn foo(a, b) { return a }")

        val loadFuncs = result.instrs.filterIsInstance<IrInstr.LoadFunc>()
        assertEquals(1, loadFuncs.size, "Expected one LoadFunc")

        val lf = loadFuncs[0]
        assertEquals("foo", lf.name)
        assertEquals(2, lf.arity, "Arity should be 2")

        val nestedReturn = lf.instrs.filterIsInstance<IrInstr.Return>()
        assertTrue(nestedReturn.isNotEmpty(), "Nested instructions should contain a Return")
    }

    // ---------------------------------------------------------------------------
    // 8. Function call: LoadFunc + StoreGlobal + LoadGlobal/local + Call
    // ---------------------------------------------------------------------------

    @Test
    fun testFunctionCall_loadFuncStoreGlobalAndCall() {
        val result = lowerSource("fn foo() { return 1 }\nfoo()")

        val loadFuncs = result.instrs.filterIsInstance<IrInstr.LoadFunc>()
        assertEquals(1, loadFuncs.size, "Expected one LoadFunc for fn foo")

        // lowerFunc always emits StoreGlobal after LoadFunc
        val storeGlobals = result.instrs.filterIsInstance<IrInstr.StoreGlobal>()
        assertTrue(storeGlobals.any { it.name == "foo" }, "Expected StoreGlobal(\"foo\")")

        // Call instruction must be present
        val calls = result.instrs.filterIsInstance<IrInstr.Call>()
        assertEquals(1, calls.size, "Expected one Call instruction")
        assertEquals(0, calls[0].args.size, "foo() takes no arguments")
    }

    // ---------------------------------------------------------------------------
    // 9. List literal: `let x = [1, 2, 3]` → NewArray with 3 elements
    // ---------------------------------------------------------------------------

    @Test
    fun testListLiteral_newArrayWithThreeElements() {
        val result = lowerSource("let x = [1, 2, 3]")

        val newArrays = result.instrs.filterIsInstance<IrInstr.NewArray>()
        assertEquals(1, newArrays.size, "Expected one NewArray")
        assertEquals(3, newArrays[0].elements.size, "NewArray should have 3 element registers")

        // The three integer constants must all be present
        val constValues = result.constants.toSet()
        assertTrue(Value.Int(1) in constValues)
        assertTrue(Value.Int(2) in constValues)
        assertTrue(Value.Int(3) in constValues)
    }

    // ---------------------------------------------------------------------------
    // 10. Field access: produces GetField
    // ---------------------------------------------------------------------------

    @Test
    fun testFieldAccess_producesGetField() {
        // Declare a variable so we have something to access a field on
        val result = lowerSource("let obj = 1\nlet y = obj.foo")

        val getFields = result.instrs.filterIsInstance<IrInstr.GetField>()
        assertTrue(getFields.isNotEmpty(), "Expected at least one GetField")
        assertEquals("foo", getFields[0].name, "GetField should target field 'foo'")
    }

    // ---------------------------------------------------------------------------
    // 11. Index access: produces GetIndex
    // ---------------------------------------------------------------------------

    @Test
    fun testIndexAccess_producesGetIndex() {
        val result = lowerSource("let arr = [1, 2, 3]\nlet y = arr[0]")

        val getIndices = result.instrs.filterIsInstance<IrInstr.GetIndex>()
        assertTrue(getIndices.isNotEmpty(), "Expected at least one GetIndex")
    }

    // ---------------------------------------------------------------------------
    // 12. Return statement: produces Return
    // ---------------------------------------------------------------------------

    @Test
    fun testReturnStatement_producesReturn() {
        // Return at top-level is wrapped in a function so we can observe it naturally;
        // the lowerer emits Return when it sees Stmt.ReturnStmt regardless of nesting.
        val result = lowerSource("fn bar() { return 42 }")

        val loadFuncs = result.instrs.filterIsInstance<IrInstr.LoadFunc>()
        assertEquals(1, loadFuncs.size)

        val nestedReturns = loadFuncs[0].instrs.filterIsInstance<IrInstr.Return>()
        assertEquals(1, nestedReturns.size, "Expected exactly one Return inside bar()")
    }

    // ---------------------------------------------------------------------------
    // 13. Unary expression: `let x = -5` → LoadImm, UnaryOp(MINUS)
    // ---------------------------------------------------------------------------

    @Test
    fun testUnaryExpression_loadImmAndUnaryOp() {
        val result = lowerSource("let x = -5")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(1, loadImms.size, "Expected one LoadImm for the operand")
        assertEquals(Value.Int(5), result.constants[loadImms[0].index])

        val unaryOps = result.instrs.filterIsInstance<IrInstr.UnaryOp>()
        assertEquals(1, unaryOps.size, "Expected one UnaryOp")
        assertEquals(TokenType.MINUS, unaryOps[0].op, "UnaryOp should use MINUS")

        // Destination of UnaryOp should differ from its source (fresh dst reg)
        assertTrue(unaryOps[0].dst != unaryOps[0].src, "UnaryOp dst and src should be different registers")
    }

    // ---------------------------------------------------------------------------
    // 14. Class declaration: LoadClass with method "bar" present
    // ---------------------------------------------------------------------------

    @Test
    fun testClassDeclaration_loadClassWithMethods() {
        val result = lowerSource("class Foo { fn bar() { return 1 } }")

        val loadClasses = result.instrs.filterIsInstance<IrInstr.LoadClass>()
        assertEquals(1, loadClasses.size, "Expected one LoadClass")

        val lc = loadClasses[0]
        assertEquals("Foo", lc.name)
        assertNull(lc.superClass, "Foo has no superclass")
        assertTrue("bar" in lc.methods, "LoadClass should contain method 'bar'")

        val barMethod = lc.methods["bar"]!!
        // arity = params.size + 1 (self); bar has 0 params so arity = 1
        assertEquals(1, barMethod.arity, "bar arity should be 1 (self only)")

        val barReturn = barMethod.instrs.filterIsInstance<IrInstr.Return>()
        assertTrue(barReturn.isNotEmpty(), "bar should have a Return in its body")
    }

    @Test
    fun testClassDeclaration_storesGlobal() {
        val result = lowerSource("class Foo { fn bar() { return 1 } }")

        val storeGlobals = result.instrs.filterIsInstance<IrInstr.StoreGlobal>()
        assertTrue(storeGlobals.any { it.name == "Foo" }, "Class Foo should be stored as a global")
    }

    // Note: 'extends' is not in the Lexer keyword map (pre-existing bug),
    // so class inheritance cannot be tested through source parsing yet.

    // ---------------------------------------------------------------------------
    // 15. Compound assignment: `let x = 5\nx += 1` → BinaryOp with original value
    // ---------------------------------------------------------------------------

    @Test
    fun testCompoundAssignment_emitsBinaryOp() {
        val result = lowerSource("let x = 5\nx += 1")

        val binaryOps = result.instrs.filterIsInstance<IrInstr.BinaryOp>()
        assertTrue(binaryOps.isNotEmpty(), "Expected at least one BinaryOp for compound assignment")

        val addOp = binaryOps.find { it.op == TokenType.PLUS }
        assertTrue(addOp != null, "Expected a PLUS BinaryOp for +=")

        // Both src1 and src2 of the add should point to real registers (non-negative)
        assertTrue(addOp!!.src1 >= 0)
        assertTrue(addOp.src2 >= 0)
    }

    @Test
    fun testCompoundAssignment_allOperators() {
        // -= * /= %=
        val ops = listOf(
            "-=" to TokenType.MINUS,
            "*=" to TokenType.STAR,
            "/=" to TokenType.SLASH,
            "%=" to TokenType.PERCENT
        )
        for ((srcOp, expectedToken) in ops) {
            val result = lowerSource("let x = 10\nx $srcOp 2")
            val binaryOps = result.instrs.filterIsInstance<IrInstr.BinaryOp>()
            assertTrue(
                binaryOps.any { it.op == expectedToken },
                "Expected BinaryOp($expectedToken) for compound operator $srcOp"
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Extra: empty variable declaration (no initialiser → LoadImm of Null)
    // ---------------------------------------------------------------------------

    @Test
    fun testVarDeclarationNoInitialiser_loadsNull() {
        val result = lowerSource("let x")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(1, loadImms.size, "Expected one LoadImm even without initialiser")
        assertEquals(Value.Null, result.constants[loadImms[0].index], "Uninitialised var should load Null")
    }

    // ---------------------------------------------------------------------------
    // Extra: return without value → LoadImm(Null) then Return
    // ---------------------------------------------------------------------------

    @Test
    fun testReturnNoValue_loadsNullThenReturns() {
        val result = lowerSource("fn empty() { return }")

        val lf = result.instrs.filterIsInstance<IrInstr.LoadFunc>().single()
        val nullLoads = lf.instrs.filterIsInstance<IrInstr.LoadImm>()
            .filter { lf.constants.getOrNull(it.index) == Value.Null }
        assertTrue(nullLoads.isNotEmpty(), "return with no value should emit LoadImm(Null)")
        assertTrue(lf.instrs.filterIsInstance<IrInstr.Return>().isNotEmpty())
    }

    // ---------------------------------------------------------------------------
    // Extra: boolean literals produce the correct constants
    // ---------------------------------------------------------------------------

    @Test
    fun testBooleanLiteral_trueAndFalse() {
        val resultTrue = lowerSource("let x = true")
        val trueLoad = resultTrue.instrs.filterIsInstance<IrInstr.LoadImm>().single()
        assertEquals(Value.Boolean(true), resultTrue.constants[trueLoad.index])

        val resultFalse = lowerSource("let x = false")
        val falseLoad = resultFalse.instrs.filterIsInstance<IrInstr.LoadImm>().single()
        assertEquals(Value.Boolean(false), resultFalse.constants[falseLoad.index])
    }

    // ---------------------------------------------------------------------------
    // Extra: string literal
    // ---------------------------------------------------------------------------

    @Test
    fun testStringLiteral_loadsCorrectConstant() {
        val result = lowerSource("let s = \"hello\"")
        val load = result.instrs.filterIsInstance<IrInstr.LoadImm>().single()
        assertEquals(Value.String("hello"), result.constants[load.index])
    }

    // ---------------------------------------------------------------------------
    // Extra: for-in loop desugars to iter/hasNext/next pattern
    // ---------------------------------------------------------------------------

    @Test
    fun testForInLoop_desugarToIterPattern() {
        val result = lowerSource("let arr = [1, 2]\nfor x in arr { let z = x }")

        // Should have Label, GetField("iter"), Call, Label(top), GetField("hasNext"), Call, JumpIfFalse, GetField("next"), Call
        val getFields = result.instrs.filterIsInstance<IrInstr.GetField>()
        val fieldNames = getFields.map { it.name }
        assertTrue("iter" in fieldNames, "for-in should call .iter()")
        assertTrue("hasNext" in fieldNames, "for-in should call .hasNext()")
        assertTrue("next" in fieldNames, "for-in should call .next()")

        val jumpIfFalse = result.instrs.filterIsInstance<IrInstr.JumpIfFalse>()
        assertTrue(jumpIfFalse.isNotEmpty(), "for-in should emit JumpIfFalse for loop condition")

        val backJumps = result.instrs.filterIsInstance<IrInstr.Jump>()
        assertTrue(backJumps.isNotEmpty(), "for-in should emit a back-edge Jump")
    }

    // ---------------------------------------------------------------------------
    // Extra: function with default parameter emits DefaultValueInfo
    // ---------------------------------------------------------------------------

    @Test
    fun testFunctionDefaultParam_defaultValueInfoPresent() {
        val result = lowerSource("fn greet(name, times = 1) { return times }")

        val lf = result.instrs.filterIsInstance<IrInstr.LoadFunc>().single()
        assertEquals(2, lf.arity)
        assertEquals(2, lf.defaultValues.size, "defaultValues list should have one entry per param")
        // First param has no default
        assertEquals(null, lf.defaultValues[0])
        // Second param has a default of 1
        val defaultInfo = lf.defaultValues[1]
        assertTrue(defaultInfo != null, "Second param should have a DefaultValueInfo")
        assertTrue(defaultInfo!!.instrs.filterIsInstance<IrInstr.LoadImm>().isNotEmpty(),
            "Default value IR should contain a LoadImm")
        assertEquals(Value.Int(1), defaultInfo.constants.first())
    }

    // ---------------------------------------------------------------------------
    // Extra: index assignment produces SetIndex
    // ---------------------------------------------------------------------------

    @Test
    fun testIndexAssignment_producesSetIndex() {
        val result = lowerSource("let arr = [1, 2, 3]\narr[0] = 99")

        val setIndices = result.instrs.filterIsInstance<IrInstr.SetIndex>()
        assertTrue(setIndices.isNotEmpty(), "Expected at least one SetIndex")
    }

    // ---------------------------------------------------------------------------
    // Extra: field assignment produces SetField
    // ---------------------------------------------------------------------------

    @Test
    fun testFieldAssignment_producesSetField() {
        val result = lowerSource("let obj = 1\nobj.value = 42")

        val setFields = result.instrs.filterIsInstance<IrInstr.SetField>()
        assertTrue(setFields.isNotEmpty(), "Expected at least one SetField")
        assertEquals("value", setFields[0].name)
    }

    // ---------------------------------------------------------------------------
    // Extra: is-expression produces IsType
    // ---------------------------------------------------------------------------

    @Test
    fun testIsExpression_producesIsType() {
        val result = lowerSource("let x = 1\nlet y = x is int")

        val isTypes = result.instrs.filterIsInstance<IrInstr.IsType>()
        assertEquals(1, isTypes.size, "Expected one IsType instruction")
        assertEquals("int", isTypes[0].typeName)
    }

    // ---------------------------------------------------------------------------
    // Extra: multiple variables get distinct registers
    // ---------------------------------------------------------------------------

    @Test
    fun testMultipleVars_distinctRegisters() {
        val result = lowerSource("let a = 1\nlet b = 2\nlet c = 3")

        val loadImms = result.instrs.filterIsInstance<IrInstr.LoadImm>()
        assertEquals(3, loadImms.size)
        val dstRegs = loadImms.map { it.dst }.toSet()
        assertEquals(3, dstRegs.size, "Each variable should be assigned a distinct destination register")
    }

    // ---------------------------------------------------------------------------
    // Extra: nested binary expressions preserve evaluation order
    // ---------------------------------------------------------------------------

    @Test
    fun testNestedBinaryExpr_correctOpCount() {
        // (1 + 2) * (3 - 4) → 4 LoadImm + 3 BinaryOps (PLUS, MINUS, STAR)
        val result = lowerSource("let x = (1 + 2) * (3 - 4)")

        val binaryOps = result.instrs.filterIsInstance<IrInstr.BinaryOp>()
        assertEquals(3, binaryOps.size, "Should emit three BinaryOps for nested expression")

        val ops = binaryOps.map { it.op }.toSet()
        assertTrue(TokenType.PLUS in ops)
        assertTrue(TokenType.MINUS in ops)
        assertTrue(TokenType.STAR in ops)
    }

    // ---------------------------------------------------------------------------
    // Helper assertion not in kotlin.test
    // ---------------------------------------------------------------------------

    private fun assertNull(value: Any?, message: String) {
        assertTrue(value == null, message)
    }
}
