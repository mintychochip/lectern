# String Methods — Design Spec

## Status

**Feature:** String instance methods
**Created:** 2026-03-19
**Type:** Language feature (runtime)
**Priority:** High

## Overview

Add methods on string values to enable idiomatic string manipulation. Currently strings (`Value.String`) have no methods — calling `.split()` or `.trim()` would fail. This spec adds a curated set of methods via special-case handling in the VM's `GET_FIELD` opcode.

## Approach

### Implementation Architecture

When `GET_FIELD` is dispatched on a `Value.String`, the VM will check if the field name matches a built-in string method and return a `BoundMethod` wrapping a `NativeFunction`. This mirrors how `BoundMethod` works for class instances.

### Changes

#### 1. `VM.kt` — Special-case `GET_FIELD` for strings

In the `GET_FIELD` opcode handler, add a branch:

```kotlin
OpCode.GET_FIELD -> {
    val obj = frame.regs[src1] ?: error("Cannot get field on null")
    val fieldName = frame.chunk.strings[imm]
    frame.regs[dst] = when (obj) {
        is Value.String -> getStringMethod(obj, fieldName)
            ?: error("String has no method '$fieldName'")
        is Value.Instance -> { /* existing logic */ }
        else -> error("Cannot get field on ${obj::class.simpleName}")
    }
}
```

#### 2. `Value.kt` — Add `getStringMethod` function

```kotlin
fun getStringMethod(self: Value.String, name: String): Value? {
    return when (name) {
        "split"   -> Value.NativeFunction { args -> stringSplit(self, args.getOrNull(1)?.let { expectString(it) }) }
        "trim"    -> Value.NativeFunction { args -> Value.String(self.value.trim()) }
        "contains"-> Value.NativeFunction { args -> Value.Boolean(self.value.contains(expectString(args[1]))) }
        // ... etc
        else -> null
    }
}
```

#### 3. `Value.kt` — Helper functions

```kotlin
private fun expectString(v: Value): String = (v as? Value.String)?.value
    ?: error("Expected string, got $v")
```

## Methods

### Implemented

| Method | Signature | Returns | Notes |
|--------|-----------|---------|-------|
| `split` | `(delim: string): Array` | `Array<string>` | Splits by delimiter. Returns empty array on empty string. |
| `trim` | `(): String` | `String` | Removes leading/trailing whitespace (Kotlin `trim()`) |
| `contains` | `(sub: string): bool` | `bool` | True if string contains substring |
| `replace` | `(old: string, new: string): String` | `String` | Replaces first occurrence only |
| `replaceAll` | `(old: string, new: string): String` | `String` | Replaces all occurrences |
| `toUpperCase` | `(): String` | `String` | All uppercase |
| `toLowerCase` | `(): String` | `String` | All lowercase |
| `startsWith` | `(prefix: string): bool` | `bool` | |
| `endsWith` | `(suffix: string): bool` | `bool` | |
| `indexOf` | `(sub: string): int` | `int` | Position, or `-1` if not found |
| `length` | `(): int` | `int` | Character count |
| `isEmpty` | `(): bool` | `bool` | True if `length() == 0` |
| `isBlank` | `(): bool` | `bool` | True if whitespace-only or empty |
| `chars` | `(): Array` | `Array<string>` | Array of 1-char strings |
| `get` | `(index: int): String` | `String` | 1-char string at index. **TODO:** Out-of-bounds should throw error. Currently returns `null`. |

### Out-of-bounds behavior for `get()`

**TODO (exceptions):** Currently `get(index)` returns `Value.Null` when index is out of bounds. When exception handling is added to Quill, this should throw a `StringIndexOutOfBoundsError` instead.

## VM Globals

No new globals needed. String methods are dispatched entirely through `GET_FIELD` + `CALL`.

## Testing

Add tests in `VMTest.kt` covering:
- `split` with simple delimiter, empty result, multi-char delimiter
- `trim` with leading/trailing/both/neither
- `contains` true and false
- `replace` and `replaceAll`
- `toUpperCase` / `toLowerCase`
- `startsWith` / `endsWith`
- `indexOf` found and not found
- `length` on empty and non-empty strings
- `isEmpty` / `isBlank`
- `chars`
- `get` valid index and out-of-bounds (currently null)

## Files to Modify

1. `src/main/kotlin/org/quill/lang/Value.kt` — Add `getStringMethod()` and helper functions
2. `src/main/kotlin/org/quill/ast/VM.kt` — Add `Value.String` case in `GET_FIELD`

## TODO

- **Exceptions:** Add proper exception type and throwing mechanism. Update `get(index)` to throw `StringIndexOutOfBoundsError` instead of returning `null` on out-of-bounds.
- Other string methods: `substring`, `padStart`, `padEnd`, `repeat`, `reverse` (lower priority)
