# `has` Field Check Operator — Design Spec

**Status:** Approved
**Date:** 2026-03-19

---

## Overview

Add a `has` keyword to Lectern that checks whether an object or map contains a named field. Returns `true`/`false`.

**Syntax:** `expr has expr`

The right-hand side is any expression that evaluates to a string — typically a string literal like `"fieldName"`, but a variable holding a string also works.

---

## Semantics

- Returns `true` if the object/map has the named field (own fields only), `false` otherwise
- Works on: objects (class instances) and maps
- Does NOT work on arrays — arrays use numeric indices and this is out of scope
- The field name expression is evaluated at runtime (must resolve to a string)
- **No inheritance walk** — only fields set directly on the instance are checked. This is a deliberate limitation to avoid requiring `ClassDescriptor` to track declared field names. See `docs/superpowers/plans/2026-03-19-has-operator-context.md` for rationale and future path.

---

## Pipeline Changes

### Lexer — `src/main/kotlin/org/lectern/lang/Lexer.kt`

Add `KW_HAS` to `TokenType` enum in `Token.kt` and handle the `"has"` keyword in the lexer.

### Parser — `src/main/kotlin/org/lectern/lang/Parser.kt`

New prefix expression with precedence **45** (tighter than `==`/`!=` at 40, looser than `.` at 90):

```
hasExpr -> "has" expression expression
```

- Left operand: the object or map to check
- Right operand: any expression that evaluates to a string (typically a string literal)

Precedence level 45 sits between `EQ_EQ`/`BANG_EQ` (40) and `LT`/`GT`/`LTE`/`GTE` (50). This means `obj has "field" == true` parses as `(obj has "field") == true`.

### AST — `src/main/kotlin/org/lectern/lang/AST.kt`

New expression node:

```kotlin
data class HasExpr(
    val target: Expr,      // object or map being checked
    val field: Expr       // expression that evaluates to the field name string
) : Expr()
```

### AstLowerer — `src/main/kotlin/org/lectern/ast/AstLowerer.kt`

Lowers `HasExpr` to a new IR instruction:

```kotlin
is Expr.HasExpr -> {
    val objReg = lowerExpr(expr.target, freshReg())
    val fieldReg = lowerExpr(expr.field, freshReg())
    emit(IrInstr.HasCheck(dst, objReg, fieldReg))
    dst
}
```

The `fieldReg` register holds the string value of the field name to check.

### IR — `src/main/kotlin/org/lectern/lang/IR.kt`

New `IrInstr` subclass:

```kotlin
data class HasCheck(
    val dst: Int,           // destination register (boolean result)
    val obj: Int,           // register holding the object/map
    val field: Int          // register holding the field name string
) : IrInstr()
```

### OpCode — `src/main/kotlin/org/lectern/lang/OpCode.kt`

New opcode (after `POW(0x28)`):

```kotlin
HAS(0x29)    // dst = obj.has(field) — true if field exists
```

### IrCompiler — `src/main/kotlin/org/lectern/ast/IrCompiler.kt`

Compiles `IrInstr.HasCheck` to `HAS` bytecode.

### LivenessAnalyzer — `src/main/kotlin/org/lectern/ast/LivenessAnalyzer.kt`

Add case for `IrInstr.HasCheck`:
- Uses: `obj` register
- Defines: `dst` register

### VM — `src/main/kotlin/org/lectern/ast/VM.kt`

`HAS` opcode dispatch:

1. **If object is `Value.Instance`**:
   - Get `fieldName` string from `frame.regs[fieldReg]` via `valueToString()`
   - Check `instance.fields.containsKey(fieldName)` — own fields only (fields are stored per-instance, not on `ClassDescriptor`)
   - Return `true` if found, `false` otherwise
2. **If object is `Value.Instance` with `clazz == MapClass`** (map):
   - Get `fieldName` string from `frame.regs[fieldReg]` via `valueToString()`
   - Get entries via `instance.fields["__entries"] as? Value.InternalMap`
   - Check `entries.entries.containsKey(Value.String(fieldName))`
   - Return `true`/`false`
3. **If object is `Value.Instance` with `clazz == ArrayClass`** (array): return `false`
4. **Otherwise**: return `false`

**Note:** Only own-instance fields are checked. There is no inheritance walk for field lookup — fields are stored per-instance in `instance.fields`, not on `ClassDescriptor`. This is consistent with how `GET_FIELD` works.

---

## Examples

```kotlin
class Animal {
    init() { this.name = "dog" }
}

class Dog extends Animal {
    init() { this.breed = "poodle" }
}

let d = Dog()
d has "name"    // false (Animal's init set this.name, not Dog's — own fields only)
d has "breed"   // true  (Dog's init set this.breed)
d has "age"     // false (not set on this instance)

let m = { "a": 1, "b": 2 }
m has "a"       // true
m has "c"       // false

let key = "a"
m has key       // true  (dynamic check via variable)
```

---

## Files to Modify

| File | Change |
|------|--------|
| `src/main/kotlin/org/lectern/lang/Token.kt` | Add `KW_HAS` |
| `src/main/kotlin/org/lectern/lang/Lexer.kt` | Handle `"has"` keyword |
| `src/main/kotlin/org/lectern/lang/AST.kt` | Add `HasExpr` node |
| `src/main/kotlin/org/lectern/lang/Parser.kt` | Parse `has` expression |
| `src/main/kotlin/org/lectern/lang/IR.kt` | Add `HasCheck` IR instruction |
| `src/main/kotlin/org/lectern/lang/OpCode.kt` | Add `HAS` opcode |
| `src/main/kotlin/org/lectern/ast/AstLowerer.kt` | Lower `HasExpr` |
| `src/main/kotlin/org/lectern/ast/IrCompiler.kt` | Compile `HasCheck` |
| `src/main/kotlin/org/lectern/ast/LivenessAnalyzer.kt` | Handle `HasCheck` |
| `src/main/kotlin/org/lectern/ast/VM.kt` | Dispatch `HAS` opcode |

## Test Cases

```kotlin
// Basic own field
class Foo { init() { this.x = 1 } }
let f = Foo()
f has "x"    // true
f has "y"    // false

// Subclass: each instance has only its own fields
class Animal { init() { this.name = "animal" } }
class Cat extends Animal { init() { this.meow = "purr" } }
let c = Cat()
c has "name"      // false (Animal's init, not Cat's — own fields only)
c has "meow"     // true (Cat's init set this.meow)
c has "bark"     // false (not set)

// Map
let m = { "key": 42 }
m has "key"       // true
m has "other"     // false

// Dynamic via variable
let field = "key"
m has field       // true

// Negative case (non-object)
let x = 42
x has "foo"       // false (integers don't have fields)
```

---

## Open Issues

None.
