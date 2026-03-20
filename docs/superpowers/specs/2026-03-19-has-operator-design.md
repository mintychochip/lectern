# `has` Field Check Operator — Design Spec

**Status:** Approved
**Date:** 2026-03-19

---

## Overview

Add a `has` keyword to Lectern that checks whether an object or map contains a named field. Returns `true`/`false`.

**Syntax:** `expr has stringLiteral`

---

## Semantics

- Returns `true` if the object/map has the named field (own **or inherited** via prototype chain), `false` otherwise
- Works on: objects (class instances) and maps
- Does NOT work on arrays — arrays use numeric indices and this is out of scope
- The field name must be a string literal (`"fieldName"`), enabling dynamic checks via variables

---

## Pipeline Changes

### Lexer — `src/main/kotlin/org/lectern/lang/Lexer.kt`

Add `KW_HAS` to `TokenType` enum in `Token.kt` and handle the `"has"` keyword in the lexer.

### Parser — `src/main/kotlin/org/lectern/lang/Parser.kt`

New prefix expression with precedence lower than member access (`DOT`):

```
hasExpr -> "has" expression stringLiteral
```

- Left operand: the object or map to check
- Right operand: a string literal naming the field

Precedence: should bind tighter than `==`/`!=` but looser than member access. Think of it as `has(obj, "field")` in spirit.

### AST — `src/main/kotlin/org/lectern/lang/AST.kt`

New expression node:

```kotlin
data class HasExpr(
    val target: Expr,      // object or map being checked
    val field: String     // field name as a String
) : Expr()
```

### AstLowerer — `src/main/kotlin/org/lectern/ast/AstLowerer.kt`

Lowers `HasExpr` to a new IR instruction:

```kotlin
is Expr.HasExpr -> {
    val objReg = lowerExpr(expr.target, freshReg())
    emit(IrInstr.HasCheck(dst, objReg, expr.field))
    dst
}
```

### IR — `src/main/kotlin/org/lectern/lang/IR.kt`

New `IrInstr` subclass:

```kotlin
data class HasCheck(
    val dst: Int,           // destination register (boolean result)
    val obj: Int,           // register holding the object/map
    val field: String       // field name to check
) : IrInstr()
```

### OpCode — `src/main/kotlin/org/lectern/lang/OpCode.kt`

New opcode:

```kotlin
HAS(0x??)    // dst = obj.has(field) — true if field exists
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
   - Check `instance.fields.containsKey(fieldName)`
   - If not found and `instance.klass.superClass != null`, walk the superClass chain
   - Return `true` if found, `false` otherwise
2. **If object is `Value.Map`**:
   - Check `map.containsKey(fieldName)` — maps use string keys
   - Return `true`/`false`
3. **Otherwise**: return `false`

---

## Examples

```kotlin
class Animal {
    init() { this.name = "dog" }
}

class Dog extends Animal {}

let d = Dog()
d has "name"    // true  (inherited via prototype chain)
d has "breed"   // false (not present)

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

// Inherited field
class Animal { init() { this.name = "animal" } }
class Cat extends Animal {}
let c = Cat()
c has "name"      // true (inherited)
c has "meow"      // false

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
