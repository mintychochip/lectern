# String Methods Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 15 string instance methods (`split`, `trim`, `contains`, `replace`, `replaceAll`, `toUpperCase`, `toLowerCase`, `startsWith`, `endsWith`, `indexOf`, `length`, `isEmpty`, `isBlank`, `chars`, `get`) via special-case handling in the VM's `GET_FIELD` opcode.

**Architecture:** `Value.String` gets a dedicated `GET_FIELD` handler that returns `NativeFunction` closures capturing the string value. This avoids needing to change `BoundMethod`'s `Instance`-typed `instance` field. No new VM globals or opcodes needed.

**Tech Stack:** Kotlin, register-based VM, existing `Builtins.newArray` for array return values.

---

## Chunk 1: Implement `Value.kt` — `getStringMethod` and helpers

**Files:**
- Modify: `src/main/kotlin/org/quill/lang/Value.kt`

- [ ] **Step 1: Add helper functions at the end of `Value.kt`, before the `Builtins` object**

```kotlin
// === String method helpers ===

private fun expectString(v: Value): String = (v as? Value.String)?.value
    ?: error("Expected string, got $v")

private fun expectInt(v: Value): Int = (v as? Value.Int)?.value
    ?: error("Expected int, got $v")

private fun stringSplit(self: String, delim: String?): Value.Instance {
    val parts = if (delim == null || delim.isEmpty()) {
        self.map { it.toString() }
    } else {
        self.split(delim)
    }
    return Builtins.newArray(parts.map { Value.String(it) }.toMutableList())
}

fun getStringMethod(self: Value.String, name: String): Value? {
    return when (name) {
        "split" -> Value.NativeFunction { args ->
            stringSplit(self.value, args.getOrNull(0)?.let { expectString(it) })
        }
        "trim" -> Value.NativeFunction { _ ->
            Value.String(self.value.trim())
        }
        "contains" -> Value.NativeFunction { args ->
            Value.Boolean(self.value.contains(expectString(args[0])))
        }
        "replace" -> Value.NativeFunction { args ->
            Value.String(self.value.replace(expectString(args[0]), expectString(args[1]), false))
        }
        "replaceAll" -> Value.NativeFunction { args ->
            Value.String(self.value.replace(expectString(args[0]), expectString(args[1]), true))
        }
        "toUpperCase" -> Value.NativeFunction { _ ->
            Value.String(self.value.uppercase())
        }
        "toLowerCase" -> Value.NativeFunction { _ ->
            Value.String(self.value.lowercase())
        }
        "startsWith" -> Value.NativeFunction { args ->
            Value.Boolean(self.value.startsWith(expectString(args[0])))
        }
        "endsWith" -> Value.NativeFunction { args ->
            Value.Boolean(self.value.endsWith(expectString(args[0])))
        }
        "indexOf" -> Value.NativeFunction { args ->
            Value.Int(self.value.indexOf(expectString(args[0])).also { if (it < 0) return@NativeFunction Value.Int(-1) })
        }
        "length" -> Value.NativeFunction { _ ->
            Value.Int(self.value.length)
        }
        "isEmpty" -> Value.NativeFunction { _ ->
            if (self.value.isEmpty()) Value.Boolean.TRUE else Value.Boolean.FALSE
        }
        "isBlank" -> Value.NativeFunction { _ ->
            if (self.value.isBlank()) Value.Boolean.TRUE else Value.Boolean.FALSE
        }
        "chars" -> Value.NativeFunction { _ ->
            Builtins.newArray(self.value.map { Value.String(it.toString()) }.toMutableList())
        }
        "get" -> Value.NativeFunction { args ->
            val idx = expectInt(args[0])
            if (idx >= 0 && idx < self.value.length) {
                Value.String(self.value[idx].toString())
            } else {
                // TODO: throw StringIndexOutOfBoundsError instead of returning null
                Value.Null
            }
        }
        else -> null
    }
}
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew compileKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL (no errors from Value.kt)

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/quill/lang/Value.kt
git commit -m "feat: add getStringMethod and string method helpers in Value.kt

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Chunk 2: Modify `VM.kt` — `Value.String` case in `GET_FIELD`

**Files:**
- Modify: `src/main/kotlin/org/quill/ast/VM.kt:195-208`

- [ ] **Step 1: Add `Value.String` case to the `GET_FIELD` handler**

In `VM.kt`, find the `GET_FIELD` opcode handler (around line 195). The existing code is:

```kotlin
OpCode.GET_FIELD -> {
    val obj = frame.regs[src1] ?: error("Cannot get field on null")
    val fieldName = frame.chunk.strings[imm]
    frame.regs[dst] = when (obj) {
        is Value.Instance -> {
            // Check fields first
            obj.fields[fieldName]?.let { it }
                // Then walk the class hierarchy for methods
                ?: lookupMethod(obj, fieldName)
                    ?.let { Value.BoundMethod(obj, it) }
                ?: error("Instance has no field '$fieldName'")
        }
        else -> error("Cannot get field on ${obj::class.simpleName}")
    }
}
```

Replace the `when` body with:

```kotlin
frame.regs[dst] = when (obj) {
    is Value.String -> getStringMethod(obj, fieldName)
        ?: error("String has no method '$fieldName'")
    is Value.Instance -> {
        // Check fields first
        obj.fields[fieldName]?.let { it }
            // Then walk the class hierarchy for methods
            ?: lookupMethod(obj, fieldName)
                ?.let { Value.BoundMethod(obj, it) }
            ?: error("Instance has no field '$fieldName'")
    }
    else -> error("Cannot get field on ${obj::class.simpleName}")
}
```

Also add the import at the top of the file:
```kotlin
import org.quill.lang.getStringMethod
```

- [ ] **Step 2: Verify the file compiles**

Run: `./gradlew compileKotlin 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/org/quill/ast/VM.kt
git commit -m "feat: wire Value.String GET_FIELD to getStringMethod in VM

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Chunk 3: Add tests in `VMTest.kt`

**Files:**
- Modify: `src/test/kotlin/org/quill/ast/VMTest.kt`

- [ ] **Step 1: Add string method tests — first batch (split, trim, contains, replace)**

Add after the existing `testStringEscapeQuote` test (~line 560):

```kotlin
    // String method tests

    @Test
    fun testStringSplit() {
        val output = compileAndRun("""print("a,b,c".split(","))""")
        // split(",") returns Array, which toString() is [a, b, c]
        assertEquals(listOf("[a, b, c]"), output)
    }

    @Test
    fun testStringSplitEmptyResult() {
        val output = compileAndRun("""print("".split(","))""")
        assertEquals(listOf("[]"), output)
    }

    @Test
    fun testStringSplitMultiChar() {
        val output = compileAndRun("""print("a--b--c".split("--"))""")
        assertEquals(listOf("[a, b, c]"), output)
    }

    @Test
    fun testStringTrim() {
        val output = compileAndRun("""print("  hello  ".trim())""")
        assertEquals(listOf("hello"), output)
    }

    @Test
    fun testStringTrimNeither() {
        val output = compileAndRun("""print("hello".trim())""")
        assertEquals(listOf("hello"), output)
    }

    @Test
    fun testStringContainsTrue() {
        val output = compileAndRun("""print("hello world".contains("world"))""")
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testStringContainsFalse() {
        val output = compileAndRun("""print("hello world".contains("foo"))""")
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testStringReplace() {
        val output = compileAndRun("""print("hello".replace("l", "x"))""")
        // replace replaces first occurrence only
        assertEquals(listOf("hexlo"), output)
    }

    @Test
    fun testStringReplaceAll() {
        val output = compileAndRun("""print("hello".replaceAll("l", "x"))""")
        assertEquals(listOf("hexxo"), output)
    }
```

- [ ] **Step 2: Run the first batch of tests**

Run: `./gradlew test --tests "org.quill.ast.VMTest.testStringSplit" --tests "org.quill.ast.VMTest.testStringTrim" --tests "org.quill.ast.VMTest.testStringContainsTrue" --tests "org.quill.ast.VMTest.testStringReplace" -v 2>&1 | tail -30`
Expected: All PASS

- [ ] **Step 3: Add second batch of tests (toUpperCase, toLowerCase, startsWith, endsWith, indexOf)**

Add after the first batch:

```kotlin
    @Test
    fun testStringToUpperCase() {
        val output = compileAndRun("""print("hello".toUpperCase())""")
        assertEquals(listOf("HELLO"), output)
    }

    @Test
    fun testStringToLowerCase() {
        val output = compileAndRun("""print("HELLO".toLowerCase())""")
        assertEquals(listOf("hello"), output)
    }

    @Test
    fun testStringStartsWithTrue() {
        val output = compileAndRun("""print("hello".startsWith("hel"))""")
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testStringStartsWithFalse() {
        val output = compileAndRun("""print("hello".startsWith("world"))""")
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testStringEndsWithTrue() {
        val output = compileAndRun("""print("hello".endsWith("llo"))""")
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testStringEndsWithFalse() {
        val output = compileAndRun("""print("hello".endsWith("world"))""")
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testStringIndexOfFound() {
        val output = compileAndRun("""print("hello".indexOf("l"))""")
        assertEquals(listOf("2"), output)
    }

    @Test
    fun testStringIndexOfNotFound() {
        val output = compileAndRun("""print("hello".indexOf("z"))""")
        assertEquals(listOf("-1"), output)
    }
```

- [ ] **Step 4: Run second batch**

Run: `./gradlew test --tests "org.quill.ast.VMTest.testStringToUpperCase" --tests "org.quill.ast.VMTest.testStringStartsWithTrue" --tests "org.quill.ast.VMTest.testStringIndexOfNotFound" -v 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 5: Add third batch (length, isEmpty, isBlank, chars, get)**

```kotlin
    @Test
    fun testStringLength() {
        val output = compileAndRun("""print("hello".length())""")
        assertEquals(listOf("5"), output)
    }

    @Test
    fun testStringLengthEmpty() {
        val output = compileAndRun("""print("".length())""")
        assertEquals(listOf("0"), output)
    }

    @Test
    fun testStringIsEmptyTrue() {
        val output = compileAndRun("""print("".isEmpty())""")
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testStringIsEmptyFalse() {
        val output = compileAndRun("""print("hello".isEmpty())""")
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testStringIsBlankTrue() {
        val output = compileAndRun("""print("   ".isBlank())""")
        assertEquals(listOf("Boolean(value=true)"), output)
    }

    @Test
    fun testStringIsBlankFalse() {
        val output = compileAndRun("""print("hello".isBlank())""")
        assertEquals(listOf("Boolean(value=false)"), output)
    }

    @Test
    fun testStringChars() {
        val output = compileAndRun("""print("abc".chars())""")
        assertEquals(listOf("[a, b, c]"), output)
    }

    @Test
    fun testStringCharsEmpty() {
        val output = compileAndRun("""print("".chars())""")
        assertEquals(listOf("[]"), output)
    }

    @Test
    fun testStringGetValidIndex() {
        val output = compileAndRun("""print("hello".get(1))""")
        assertEquals(listOf("e"), output)
    }

    @Test
    fun testStringGetOutOfBoundsReturnsNull() {
        // TODO: should throw StringIndexOutOfBoundsError; currently returns null
        val output = compileAndRun("""print("hello".get(10))""")
        assertEquals(listOf("null"), output)
    }
```

- [ ] **Step 6: Run third batch**

Run: `./gradlew test --tests "org.quill.ast.VMTest.testStringLength" --tests "org.quill.ast.VMTest.testStringIsEmptyTrue" --tests "org.quill.ast.VMTest.testStringChars" --tests "org.quill.ast.VMTest.testStringGetValidIndex" --tests "org.quill.ast.VMTest.testStringGetOutOfBoundsReturnsNull" -v 2>&1 | tail -20`
Expected: All PASS

- [ ] **Step 7: Run all string method tests together**

Run: `./gradlew test --tests "org.quill.ast.VMTest.testString*" -v 2>&1 | tail -40`
Expected: All PASS

- [ ] **Step 8: Commit**

```bash
git add src/test/kotlin/org/quill/ast/VMTest.kt
git commit -m "test: add VM tests for all string methods

Co-Authored-By: Claude Opus 4.6 <noreply@anthropic.com>"
```

---

## Chunk 4: Run full test suite

- [ ] **Step 1: Run the full test suite**

Run: `./gradlew test 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL with no new failures

- [ ] **Step 2: Handle any test failures**

Run: `./gradlew test 2>&1 | grep -E "(FAILED|testString|BUILD)" | tail -30`

If any `testString*` test fails: investigate and fix the implementation or test assertions.
If failures are in unrelated tests (pre-existing, e.g. `testDeadCodeEliminated` which is `@Ignore`'d): they are expected, proceed.
If there are new unexpected failures: diagnose before committing.
