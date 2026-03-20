# `has` Field Check Operator Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the `has` operator — `expr has expr` — which checks if an object or map has a named field, returning `true`/`false`.

**Architecture:** `has` is a prefix binary operator parsed at precedence 45 (between `==`/`!=` at 40 and `<`/`>` at 50). It lowers to a new `HasCheck(dst, obj, fieldReg)` IR instruction, compiled to a new `HAS(0x29)` opcode. The VM dispatches it and checks own-instance fields or map entries. No inheritance walk.

**Tech Stack:** Kotlin 2.2.21, JVM 21, kotlin.test, Gradle (`./gradlew test`)

**Test command:** `./gradlew test`
**Test file:** `src/test/kotlin/org/quill/ast/VMTest.kt`

---

## File Map

| File | Changes |
|------|---------|
| `src/main/kotlin/org/quill/lang/Token.kt` | Add `KW_HAS` to `TokenType` enum |
| `src/main/kotlin/org/quill/lang/Lexer.kt` | Add `"has" to TokenType.KW_HAS` to `keywords` map |
| `src/main/kotlin/org/quill/lang/AST.kt` | Add `HasExpr(target: Expr, field: Expr)` to `Expr` sealed class |
| `src/main/kotlin/org/quill/lang/Parser.kt` | Add `TokenType.KW_HAS to 45` in `weights`; add `TokenType.KW_HAS` case to `parsePrefix()` |
| `src/main/kotlin/org/quill/lang/IR.kt` | Add `HasCheck(dst: Int, obj: Int, field: Int)` data class |
| `src/main/kotlin/org/quill/lang/OpCode.kt` | Add `HAS(0x29)` opcode |
| `src/main/kotlin/org/quill/ast/AstLowerer.kt` | Add `is Expr.HasExpr` case to `lowerExpr` |
| `src/main/kotlin/org/quill/ast/IrCompiler.kt` | Add `is IrInstr.HasCheck` case in `compile()` `when` block |
| `src/main/kotlin/org/quill/ast/LivenessAnalyzer.kt` | Add `is IrInstr.HasCheck` case in `when` block |
| `src/main/kotlin/org/quill/ast/SpillInserter.kt` | Add `is IrInstr.HasCheck` case in `when` block |
| `src/main/kotlin/org/quill/ast/VM.kt` | Add `OpCode.HAS` dispatch in both `execute()` and `executeDefaultChunk()` |

---

## Chunk 1: Foundation (Token, Lexer, AST, IR, OpCode)

### Task 1: Add `KW_HAS` token and lexer keyword

**Files:**
- Modify: `src/main/kotlin/org/quill/lang/Token.kt:3-74`
- Modify: `src/main/kotlin/org/quill/lang/Lexer.kt:23-34`

- [ ] **Step 1: Add `KW_HAS` to TokenType enum**

In `src/main/kotlin/org/quill/lang/Token.kt`, add `KW_HAS` to the `TokenType` enum. Place it after `KW_IS` (line 31):

```kotlin
    KW_IS,
    KW_HAS,   // new
    KW_TABLE,
```

- [ ] **Step 2: Add `"has"` to Lexer keywords map**

In `src/main/kotlin/org/quill/lang/Lexer.kt`, inside the `keywords` map (around line 31), add:

```kotlin
    "is" to TokenType.KW_IS,
    "has" to TokenType.KW_HAS,   // new
    "table" to TokenType.KW_TABLE,
```

- [ ] **Step 3: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/quill/lang/Token.kt src/main/kotlin/org/quill/lang/Lexer.kt
git commit -m "feat: add KW_HAS token and lexer keyword for has operator"
```

---

### Task 2: Add `HasExpr` to AST and `HasCheck` to IR

**Files:**
- Modify: `src/main/kotlin/org/quill/lang/AST.kt`
- Modify: `src/main/kotlin/org/quill/lang/IR.kt`
- Modify: `src/main/kotlin/org/quill/lang/OpCode.kt`

- [ ] **Step 1: Add `HasExpr` to AST**

In `src/main/kotlin/org/quill/lang/AST.kt`, inside the `Expr` sealed class (around line 75, after `IsExpr`):

```kotlin
    data class IsExpr(val expr: Expr, val type: Token) : Expr()
    data class HasExpr(val target: Expr, val field: Expr) : Expr()  // new
}
```

- [ ] **Step 2: Add `HasCheck` to IR**

In `src/main/kotlin/org/quill/lang/IR.kt`, add a new data class inside `IrInstr` sealed class (after `IsType` on line 29):

```kotlin
    data class IsType(val dst: Int, val src: Int, val typeName: String) : IrInstr()
    data class HasCheck(val dst: Int, val obj: Int, val field: Int) : IrInstr()  // new
```

- [ ] **Step 3: Add `HAS` opcode**

In `src/main/kotlin/org/quill/lang/OpCode.kt`, add after `POW(0x28)` (line 56):

```kotlin
    POW(0x28),      // dst = src1 ^ src2
    HAS(0x29),      // dst = obj.has(field) — true if field exists
```

- [ ] **Step 4: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/quill/lang/AST.kt src/main/kotlin/org/quill/lang/IR.kt src/main/kotlin/org/quill/lang/OpCode.kt
git commit -m "feat: add HasExpr AST node and HasCheck IR instruction"
```

---

## Chunk 2: Parser

### Task 3: Parse `has` expression

**Files:**
- Modify: `src/main/kotlin/org/quill/lang/Parser.kt`

- [ ] **Step 1: Add `KW_HAS` to precedence weights**

In `src/main/kotlin/org/quill/lang/Parser.kt`, add `TokenType.KW_HAS to 45` to the `weights` map. Place it between `EQ_EQ/BANG_EQ (40)` and `LT/GT/LTE/GTE (50)`:

```kotlin
            TokenType.EQ_EQ to 40, TokenType.BANG_EQ to 40,
            TokenType.KW_HAS to 45,   // new — tighter than < > <= >=, looser than . (90)
            TokenType.LT to 50, TokenType.GT to 50, TokenType.LTE to 50, TokenType.GTE to 50,
```

- [ ] **Step 2: Add `has` handling in `parseExpression()` infix loop**

`has` is a **binary infix operator**, NOT a prefix operator. It is handled in `parseExpression()` (around line 302), similar to how `is` is handled:

In `src/main/kotlin/org/quill/lang/Parser.kt`, in `parseExpression()` after the `KW_IS` check (around line 301):

```kotlin
            // 'is' takes a type name (identifier), not an expression
            if (token.type == TokenType.KW_IS) {
                advance()
                val typeName = parseType()
                left = Expr.IsExpr(left, typeName)
                continue
            }

            // 'has' takes a field name expression (right-hand side)
            if (token.type == TokenType.KW_HAS) {   // new
                advance()
                val field = parseExpression(45)
                left = Expr.HasExpr(left, field)
                continue
            }

            // Ternary: condition ? then : else
            if (token.type == TokenType.QUESTION) {
```

Note: `has` uses precedence 45 (right-hand side parsed at 45, same as the `has` weight itself) — correct for left-associative infix binary operators.

- [ ] **Step 3: Write a parsing test first**

Add to `src/test/kotlin/org/quill/lang/ParserTest.kt`:

```kotlin
    @Test
    fun testHasExpression() {
        val tokens = tokenize("obj has "field"")
        val stmts = Parser(tokens).parse()
        assertEquals(1, stmts.size)
        val expr = (stmts[0] as Stmt.ExprStmt).expr
        assertTrue(expr is Expr.HasExpr, "Expected HasExpr")
        val hasExpr = expr as Expr.HasExpr
        assertTrue(hasExpr.target is Expr.VariableExpr)
        assertTrue(hasExpr.field is Expr.LiteralExpr)
    }

    @Test
    fun testHasPrecedence() {
        // obj has "field" == true  parses as  (obj has "field") == true
        val tokens = tokenize("obj has "field" == true")
        val stmts = Parser(tokens).parse()
        val expr = (stmts[0] as Stmt.ExprStmt).expr
        assertTrue(expr is Expr.BinaryExpr, "Expected BinaryExpr at top level")
        assertEquals(TokenType.EQ_EQ, (expr as Expr.BinaryExpr).op.type)
    }
```

- [ ] **Step 4: Run parser tests**

```
./gradlew test --tests "org.quill.lang.ParserTest.testHasExpression" --tests "org.quill.lang.ParserTest.testHasPrecedence"
```
Expected: both PASS

- [ ] **Step 5: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/main/kotlin/org/quill/lang/Parser.kt src/test/kotlin/org/quill/lang/ParserTest.kt
git commit -m "feat: parse has expression with precedence 45"
```

---

## Chunk 3: Lowering and Compilation

### Task 4: Lower `HasExpr` in AstLowerer

**Files:**
- Modify: `src/main/kotlin/org/quill/ast/AstLowerer.kt`

- [ ] **Step 1: Add `is Expr.HasExpr` case to `lowerExpr`**

In `src/main/kotlin/org/quill/ast/AstLowerer.kt`, in the `lowerExpr` function (around line 543, after `is Expr.TernaryExpr`), add:

```kotlin
        is Expr.TernaryExpr -> {
            // ... existing code
        }
        is Expr.HasExpr -> {
            val objReg = lowerExpr(expr.target, freshReg())
            val fieldReg = lowerExpr(expr.field, freshReg())
            emit(IrInstr.HasCheck(dst, objReg, fieldReg))
            dst
        }
    }
```

- [ ] **Step 2: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/quill/ast/AstLowerer.kt
git commit -m "feat: lower HasExpr to HasCheck IR instruction"
```

---

### Task 5: Compile `HasCheck` in IrCompiler

**Files:**
- Modify: `src/main/kotlin/org/quill/ast/IrCompiler.kt`

- [ ] **Step 1: Add `is IrInstr.HasCheck` case in `compile()`**

In `src/main/kotlin/org/quill/ast/IrCompiler.kt`, in the `when (instr)` block (around line 64), add before the closing `}` of the `when`:

```kotlin
                is IrInstr.IsType -> chunk.write(OpCode.IS_TYPE, dst = instr.dst, src1 = instr.src, imm = chunk.addString(instr.typeName))
                is IrInstr.HasCheck -> {   // new
                    val fieldIdx = chunk.addString(instr.field.toString())  // field name as string
                    chunk.write(OpCode.HAS, dst = instr.dst, src1 = instr.obj, imm = fieldIdx)
                }
```

**Important:** The `field` register holds a string `Value`. We need to store the field name in the constant table and pass its index as `imm`. But since `field` is a register (runtime value), not a compile-time constant, we cannot use `imm` directly — the field name string is in a register at runtime.

Wait — this is a problem. The `HasCheck` IR instruction has `field: Int` (a register number), but the `HAS` opcode uses `imm` for the field name index. We need to think about this differently.

The `HasCheck` takes a **register** containing the field name string at runtime. The opcode needs the string index — but we don't know it at compile time (it's a runtime register). We need a different approach.

**Revised approach:** Store the field name string in the constant table during lowering, so we have a compile-time index.

In `AstLowerer`, when we lower `HasExpr`, we should store the field expression result in a register, but also add the field name string as a constant. Wait — the field name is not known at compile time if it's a dynamic expression.

But in the common case (string literal), we DO know it at compile time. For dynamic cases, we need the VM to read from the register.

**Final approach:** Keep `HasCheck(field: Int)` where `field` is a register. In IrCompiler, for the `HAS` opcode, we use `imm` as a hint for the string table... but that's wrong since we don't know the string index at compile time for dynamic fields.

Actually, looking at how `GET_FIELD` works: it takes `fieldName` as an `imm` index into `chunk.strings`. But `GET_FIELD` only works with static field names (known at compile time).

For `has` with a dynamic field expression, we need the VM to read the field name from a register, not from `imm`.

**Solution:** We need two opcodes, or we need to handle this differently.

Actually, the cleanest approach is: add a new `IrInstr` variant that stores the field name as a compile-time string constant when it's a literal, and use a register when it's dynamic. But that adds complexity.

**Simpler approach for MVP:** The `HasCheck` IR instruction holds `field: Int` (a register). The `HAS` opcode uses `imm` to reference a compile-time string constant for the common case (static field name). But when the field is dynamic (register), we need a different opcode variant.

Let's keep it simple: make `HasCheck` store the field name as a compile-time string constant, same as `GET_FIELD`. For dynamic field names, the user can use `m.get(dynamicVar) != null` instead — which already works.

Update `HasCheck` to use `fieldName: String` (compile-time constant), not `field: Int`:

```kotlin
data class HasCheck(val dst: Int, val obj: Int, val fieldName: String) : IrInstr()
```

Then IrCompiler can use `chunk.addString(instr.fieldName)` as the `imm`.

**For dynamic field names** (expression, not string literal): during parsing, if the field expression is not a literal string, emit an error for now. This is consistent with how `GET_FIELD` works — it only accepts static field names.

**Revised Task 4 and 5:**

- [ ] **Step 1 (Revised): Update `HasCheck` IR to use `fieldName: String`**

In `src/main/kotlin/org/quill/lang/IR.kt`, change the `HasCheck` definition:

```kotlin
data class HasCheck(val dst: Int, val obj: Int, val fieldName: String) : IrInstr()
```

- [ ] **Step 2 (Revised): Update `AstLowerer` to emit `HasCheck` with string field name**

In `src/main/kotlin/org/quill/ast/AstLowerer.kt`, update the `HasExpr` case:

```kotlin
is Expr.HasExpr -> {
    val objReg = lowerExpr(expr.target, freshReg())
    val fieldExpr = expr.field
    // field must be a string literal (compile-time known)
    val fieldName = (fieldExpr as? Expr.LiteralExpr)
        ?.literal as? Value.String
        ?: error("has: field name must be a string literal")
    emit(IrInstr.HasCheck(dst, objReg, fieldName.value))
    dst
}
```

Note: The `dst` register is already the destination for the `has` result (boolean). We evaluate `target` into a fresh register `objReg`. The field name is a compile-time string constant stored in `HasCheck.fieldName`, not a register — IrCompiler encodes it into `imm` via `chunk.addString()`.

- [ ] **Step 3: Add `is IrInstr.HasCheck` case in IrCompiler**

In `src/main/kotlin/org/quill/ast/IrCompiler.kt`, in the `when (instr)` block, add:

```kotlin
                is IrInstr.HasCheck -> chunk.write(OpCode.HAS, dst = instr.dst, src1 = instr.obj, imm = chunk.addString(instr.fieldName))
```

- [ ] **Step 4: Verify compilation**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/org/quill/lang/IR.kt src/main/kotlin/org/quill/ast/AstLowerer.kt src/main/kotlin/org/quill/ast/IrCompiler.kt
git commit -m "feat: lower and compile HasCheck IR to HAS opcode"
```

---

## Chunk 4: VM and Analysis

### Task 6: Dispatch `HAS` in VM

**Files:**
- Modify: `src/main/kotlin/org/quill/ast/VM.kt`

- [ ] **Step 1: Add `OpCode.HAS` dispatch in main `execute()` loop**

In `src/main/kotlin/org/quill/ast/VM.kt`, in the `when (opcode)` block inside `execute()` (around line 60), add before the closing `}` of the `when`:

```kotlin
                OpCode.HAS -> {
                    val obj = frame.regs[src1] ?: error("Cannot has on null")
                    val fieldName = frame.chunk.strings[imm]
                    val result = when (obj) {
                        is Value.Instance -> {
                            if (obj.clazz == Builtins.MapClass) {
                                // Map: check __entries InternalMap
                                val entriesVal = obj.fields["__entries"]
                                if (entriesVal is Value.InternalMap) {
                                    Value.Boolean(entriesVal.entries.containsKey(Value.String(fieldName)))
                                } else {
                                    Value.Boolean(false)
                                }
                            } else if (obj.clazz == Builtins.ArrayClass) {
                                // Array: no named fields
                                Value.Boolean(false)
                            } else {
                                // Regular instance: check own fields only
                                Value.Boolean(obj.fields.containsKey(fieldName))
                            }
                        }
                        else -> Value.Boolean(false)
                    }
                    frame.regs[dst] = result
                }
```

- [ ] **Step 2: Add `OpCode.HAS` dispatch in `executeDefaultChunk()`**

In `src/main/kotlin/org/quill/ast/VM.kt`, in the `executeDefaultChunk()` function's `when (opcode)` block (around line 442), add:

```kotlin
                OpCode.HAS -> {
                    val obj = frame.regs[src1] ?: error("Cannot has on null")
                    val fieldName = frame.chunk.strings[imm]
                    val result = when (obj) {
                        is Value.Instance -> {
                            if (obj.clazz == Builtins.MapClass) {
                                val entriesVal = obj.fields["__entries"]
                                if (entriesVal is Value.InternalMap) {
                                    Value.Boolean(entriesVal.entries.containsKey(Value.String(fieldName)))
                                } else {
                                    Value.Boolean(false)
                                }
                            } else if (obj.clazz == Builtins.ArrayClass) {
                                Value.Boolean(false)
                            } else {
                                Value.Boolean(obj.fields.containsKey(fieldName))
                            }
                        }
                        else -> Value.Boolean(false)
                    }
                    frame.regs[dst] = result
                }
```

Note: Both dispatch blocks are identical — extract to a private method `hasOpcode(frame, dst, src1, imm)` if desired, but duplicating it is fine for now to match existing patterns.

- [ ] **Step 3: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/quill/ast/VM.kt
git commit -m "feat: dispatch HAS opcode in VM for has operator"
```

---

### Task 7: Handle `HasCheck` in LivenessAnalyzer and SpillInserter

**Files:**
- Modify: `src/main/kotlin/org/quill/ast/LivenessAnalyzer.kt`
- Modify: `src/main/kotlin/org/quill/ast/SpillInserter.kt`

- [ ] **Step 1: Add `HasCheck` case in LivenessAnalyzer**

In `src/main/kotlin/org/quill/ast/LivenessAnalyzer.kt`, in the `when (instr)` block (around line 45), add before the closing `}`:

```kotlin
                is IrInstr.IsType -> define(instr.dst, idx)
                is IrInstr.HasCheck -> {   // new
                    use(instr.obj, idx)
                    define(instr.dst, idx)
                }
                is IrInstr.LoadClass -> define(instr.dst, idx)
```

- [ ] **Step 2: Add `HasCheck` case in SpillInserter**

In `src/main/kotlin/org/quill/ast/SpillInserter.kt`, in the `when (instr)` block (around line 105), add before the `else -> instr` line:

```kotlin
                is IrInstr.IsType -> instr.copy(src = resolveSrc(instr.src), dst = resolveDst(instr.dst))
                is IrInstr.HasCheck -> instr.copy(obj = resolveSrc(instr.obj), dst = resolveDst(instr.dst))  // new
                is IrInstr.LoadClass -> instr.copy(dst = resolveDst(instr.dst))
```

- [ ] **Step 3: Verify it compiles**

```
./gradlew compileKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/org/quill/ast/LivenessAnalyzer.kt src/main/kotlin/org/quill/ast/SpillInserter.kt
git commit -m "feat: handle HasCheck in LivenessAnalyzer and SpillInserter"
```

---

## Chunk 5: End-to-End Tests

### Task 8: Write and run end-to-end tests

**Files:**
- Modify: `src/test/kotlin/org/quill/ast/VMTest.kt`

- [ ] **Step 1: Write failing tests**

Add to `src/test/kotlin/org/quill/ast/VMTest.kt` (inside the `VMTest` class):

```kotlin
    @Test
    fun testHasBasic() {
        val output = compileAndRun(
            """
            class Foo { init() { this.x = 1 } }
            let f = Foo()
            print(f has "x")
            print(f has "y")
            """.trimIndent()
        )
        assertEquals(listOf("Boolean(value=true)", "Boolean(value=false)"), output)
    }

    @Test
    fun testHasMap() {
        val output = compileAndRun(
            """
            let m = { "key": 42 }
            print(m has "key")
            print(m has "other")
            """.trimIndent()
        )
        assertEquals(listOf("Boolean(value=true)", "Boolean(value=false)"), output)
    }

    @Test
    fun testHasDynamicField() {
        // Dynamic field name via variable — only works if field is a string literal
        val output = compileAndRun(
            """
            class Foo { init() { this.name = "Bob" } }
            let f = Foo()
            let field = "name"
            print(f has "name")
            """.trimIndent()
        )
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testHasArrayReturnsFalse() {
        val output = compileAndRun(
            """
            let arr = [1, 2, 3]
            print(arr has "length")
            """.trimIndent()
        )
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testHasNonObjectReturnsFalse() {
        val output = compileAndRun(
            """
            let x = 42
            print(x has "foo")
            """.trimIndent()
        )
        assertEquals(listOf("Boolean(value=false)"), output)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew test --tests "org.quill.ast.VMTest.testHasBasic" --tests "org.quill.ast.VMTest.testHasMap" --tests "org.quill.ast.VMTest.testHasDynamicField" --tests "org.quill.ast.VMTest.testHasArrayReturnsFalse" --tests "org.quill.ast.VMTest.testHasNonObjectReturnsFalse"
```
Expected: all FAIL with compile errors (HasExpr/HasCheck not yet wired everywhere) or test failures

- [ ] **Step 3: Fix any issues — rerun tests**

If tests fail due to parsing errors, check Parser changes.
If tests fail due to missing IR compilation, check IrCompiler changes.

- [ ] **Step 4: Run full test suite**

```
./gradlew test
```
Expected: all existing tests pass + new `has` tests pass

- [ ] **Step 5: Commit**

```bash
git add src/test/kotlin/org/quill/ast/VMTest.kt
git commit -m "test: add end-to-end tests for has operator"
```

---

## Chunk 6: Dynamic Field Names (Bonus)

**This chunk is OPTIONAL.** The implementation above requires the field name to be a string literal (known at compile time). If you want to support dynamic field names (e.g., `obj has someVariable`), this requires a different opcode that reads the field name from a register instead of `imm`.

To support dynamic field names:

1. Add a new `HAS_REG(0x2A)` opcode where `src2` (instead of `imm`) holds the register containing the field name string
2. In `AstLowerer`, for non-literal field expressions, emit `HAS_REG` instead of `HasCheck`
3. In `IrCompiler`, compile `HasCheck` → `HAS` (static) and add a new `HasCheckReg` IR variant → `HAS_REG` (dynamic)
4. In `VM`, dispatch both opcodes — `HAS` uses `chunk.strings[imm]`, `HAS_REG` uses `frame.regs[src2]`

**For now, if a dynamic field name is passed, `AstLowerer` throws `error("has: field name must be a string literal")`.**

---

## Final Verification

- [ ] **Run complete test suite**

```
./gradlew test
```
Expected: all tests pass, zero failures

- [ ] **Verify `test.lec` (or equivalent) runs end-to-end**

```
./gradlew run --args="test.lec"
```
(or `java -jar build/...jar test.lec` if built as fat jar)

Expected: runs without errors
