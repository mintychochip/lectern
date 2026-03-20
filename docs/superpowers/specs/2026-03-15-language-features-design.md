# Language Features Design

**Date:** 2026-03-15
**Status:** Design approved, not yet implemented

## Overview

Implement the remaining TODO language features in the quill compiler/VM in order of increasing complexity: Ternary → Maps → Enums → Lambdas/Closures → Imports → Config.

## Current Pipeline

```
AST → AstLowerer → flat List<IrInstr> → IrCompiler → Chunk → VM
                                       ↓
                              CFG → SSA → opt passes
```

## Standing Requirement: `rewriteRegisters`

In `IrCompiler.kt`, replace the `else -> instr` catch-all in `rewriteRegisters` with:
```kotlin
else -> error("rewriteRegisters: unhandled ${instr::class.simpleName}")
```

Every new `IrInstr` subclass needs an explicit case. Register mappings:

| IR Node | Remap rule |
|---------|-----------|
| `NewMap(dst, pairs)` | remap `dst` and both register fields in every pair |
| `StoreEnumVariant(classReg, name)` | remap `classReg` only |
| `CaptureUpval(dst, src)` | remap both — uses **outer** function's allocation |
| `LoadClosure(dst, ...)` | remap `dst` only — inner `instrs` use a fresh allocator |
| `LoadUpval(dst, upvalueIdx)` | remap `dst` only — **`upvalueIdx` is NOT a register** |
| `StoreUpval(upvalueIdx, src)` | remap `src` only — **`upvalueIdx` is NOT a register** |
| `Import(dst, path)` | remap `dst` only |
| `LoadConfig(dst, schemaIdx, defaultRegs)` | remap `dst` and every non-null entry in `defaultRegs` |

**Critical note on `LoadUpval`/`StoreUpval`:** The field named `upvalueIdx` (use this name, not `idx`, to distinguish from register fields) is an upvalue slot index — an ordinal into `frame.upvalues!!`. It is type `Int` but is **not** a virtual register. It must **never** be passed to `r()` in `rewriteRegisters`. Passing it to `r()` would corrupt all upvalue accesses at runtime with no compile-time error.

---

## Feature 1: Ternary Expressions

**Syntax:** `condition ? thenExpr : elseExpr`

**Associativity:** Right-associative. `a ? b : c ? d : e` parses as `a ? b : (c ? d : e)`.

**Implementation:** Desugaring in `AstLowerer.lowerExpr` for the existing `Expr.TernaryExpr`. No new opcodes.

```
lowerExpr(condition, condReg)
JumpIfFalse condReg → elseLabel
lowerExpr(thenExpr, dst)
Jump → endLabel
[elseLabel]
lowerExpr(elseExpr, dst)
[endLabel]
```

**Lexer:** `COLON` already exists. Add `QUESTION_MARK` to `TokenType` and `'?' -> QUESTION_MARK` to the Lexer's character dispatch.

**Parser — two changes required:**
1. Add `TokenType.QUESTION_MARK to 15` to the `weights` map (between `KW_OR`=20 and `ASSIGN`=10).
2. In the Pratt loop of `parseExpression`, add a `QUESTION_MARK` case:
   - Consume `?`
   - Parse then-branch with `parseExpression(16)` — one level above ternary so it does not absorb the `:`
   - Consume `:` explicitly with `consume(COLON, ...)`
   - Parse else-branch with `parseExpression(15)` — same level produces right-associativity
   - **`return` `Expr.TernaryExpr(left, then, else_)`** — `return` from the function, not `left = ...; continue`

**Files touched:** `Token.kt`, `Lexer.kt`, `Parser.kt`, `AstLowerer.kt`.

---

## Feature 2: Map Literals

**Syntax:** `{ "key": value, ... }` — non-empty only. Empty map `{}` is **forbidden** (consistent with existing `require(entries.isNotEmpty())` on `Expr.MapExpr`).

**Parsing:** Add `L_BRACE` to `parsePrefix`. In `parseExpression`, `advance()` is called before dispatching to `parsePrefix`, so when `parsePrefix` is entered, `previous()` = `L_BRACE` and `cursor` points to the first key token. At this point:
- `peek()` / `checkAhead(0, ...)` = first key token
- `checkAhead(1, COLON)` = the token after the first key

Use `checkAhead(1, COLON)` — if the token immediately after the first token is `COLON`, parse as a map literal. Blocks are only consumed by `parseStmt` and do not reach `parsePrefix`. Valid first-token types: `KW_STRING`, `KW_INT`, `KW_FLOAT`, `KW_DOUBLE`, `IDENTIFIER`.

**Key type semantics:** Exact `Value` equality — no coercion. `Value.String("x")` and `Value.Int(0)` are distinct keys.

**Runtime:** `Value.Map` already exists. No changes to `Value.kt`.

**New pieces:**
- `IrInstr.NewMap(dst: Int, pairs: List<Pair<Int, Int>>)` in `IR.kt`
- `OpCode.BUILD_MAP` — drains (key, value) pairs from arg buffer
- `AstLowerer` arm for `Expr.MapExpr`
- `IrCompiler.compile`: `NewMap` case — emit `PUSH_ARG` pairs then `BUILD_MAP`
- `IrCompiler.rewriteRegisters`: `NewMap` case (see Standing Requirement)
- VM `GET_INDEX`: `Value.Map` branch — exact key equality; return `Value.Null` if absent
- VM `SET_INDEX`: `Value.Map` branch — value register via `frame.regs[imm]` (existing encoding)

**No `GET_FIELD` for maps.** All map access uses `GET_INDEX`.

**Files touched:** `OpCode.kt`, `IR.kt`, `AstLowerer.kt`, `IrCompiler.kt`, `VM.kt`, `Parser.kt`.

---

## Feature 3: Enums

**Syntax:** `enum Direction { North, South, East, West }` — comma-separated, consistent with existing `parseEnum()`.

**`ClassDescriptor` change:** Keep as `data class`. Add `staticFields` as a **class-body property** (not a constructor parameter):
```kotlin
data class ClassDescriptor(val name: String, val superClass: ClassDescriptor?, val methods: Map<String, Value>) {
    val staticFields: MutableMap<String, Value> = mutableMapOf()
}
```

**On `copy()` and fresh maps:** Kotlin generates `copy()` as `Foo(x = this.x, ...)`, which calls the primary constructor. The primary constructor executes all class-body property initializers for each new instance. Therefore `val staticFields = mutableMapOf()` runs fresh for every construction including `copy()` — each instance gets its own empty map. No call site changes are needed.

**Compilation:** `EnumStmt` in `AstLowerer`:
1. `IrInstr.LoadClass(dst, name=enumName, superClass=null, methods=emptyMap())`
2. `IrInstr.StoreGlobal(enumName, dst)`
3. Per variant: `IrInstr.StoreEnumVariant(classReg, variantName)`

**New IR node:** `IrInstr.StoreEnumVariant(classReg: Int, variantName: String)`.

**New opcode `STORE_ENUM_VARIANT`:** `src1=classReg`, `imm=chunk.addString(variantName)`. VM: cast `frame.regs[src1]` to `Value.Class`, create `Value.Instance(descriptor)`, store in `descriptor.staticFields[chunk.strings[imm]]`.

**`GET_FIELD` on `Value.Class`:** Add a `Value.Class` branch in the VM `GET_FIELD` handler, inserted **before** the `else -> error(...)`. Look up `descriptor.staticFields[fieldName]`; throw with a clear message if absent.

**Files touched:** `AstLowerer.kt`, `OpCode.kt`, `IR.kt`, `IrCompiler.kt`, `VM.kt`, `Chunk.kt`, `Parser.kt`.

---

## Feature 4: Lambdas + Closures

**Syntax:** `fn(params) { body }` in expression position. Reference capture.

**Lambdas have fixed arity — no default parameters.** The parser must reject default values on `LambdaExpr` params (parse error if `= expr` appears). No `defaultValues` on `LoadClosure`. No `fillDefaultArgs` for closures.

**Maximum 15 upvalues per closure** — `src1` is 4-bit. If `upvalueCount > 15`, the AstLowerer throws a compile error.

### New Types in `Value.kt`

- `UpvalueCell(var value: Value)` — heap-allocated mutable box
- `Value.UpvalueRef(val cell: UpvalueCell)` — sealed subclass; internal-only, not user-visible
- `Value.Closure(chunk: Chunk, upvalues: List<UpvalueCell>)` — sealed subclass

### New IR Nodes in `IR.kt`

Use the field name `upvalueIdx: Int` (not `idx`) for upvalue slot fields to make the non-register nature explicit:

- `IrInstr.CaptureUpval(dst: Int, src: Int)` — outer function's IR; both are virtual registers
- `IrInstr.LoadClosure(dst: Int, name: String, arity: Int, instrs: List<IrInstr>, constants: List<Value>, upvalueCount: Int)` — distinct from `LoadFunc`; no `defaultValues`
- `IrInstr.LoadUpval(dst: Int, upvalueIdx: Int)` — inside `LoadClosure.instrs`; `upvalueIdx` is a slot index, NOT a register
- `IrInstr.StoreUpval(upvalueIdx: Int, src: Int)` — inside `LoadClosure.instrs`; `upvalueIdx` is a slot index, NOT a register

### Register Space Separation

`CaptureUpval` and `LoadClosure` appear in the outer IR list and use the outer function's virtual register space. `LoadUpval`/`StoreUpval` appear inside `LoadClosure.instrs` and use the lambda body's virtual register space. A fresh `RegisterAllocator` is run on `LoadClosure.instrs` in `IrCompiler.compile`. These two spaces never mix.

### New Opcodes

| Opcode | Encoding | VM Behaviour |
|--------|----------|-------------|
| `CAPTURE_UPVAL` | dst=dst, src1=src | Create `UpvalueCell(frame.regs[src1])`, wrap in `Value.UpvalueRef(cell)`, store in `frame.regs[dst]` |
| `LOAD_UPVAL` | dst=dst, imm=upvalueIdx | `frame.regs[dst] = frame.upvalues!![imm].value` |
| `STORE_UPVAL` | src1=src, imm=upvalueIdx | `frame.upvalues!![imm].value = frame.regs[src1]` |
| `LOAD_CLOSURE` | dst=dst, imm=funcIdx, src1=upvalueCount | Drain `upvalueCount` `Value.UpvalueRef` from `frame.argBuffer`, extract `.cell`, build `Value.Closure(chunk.functions[funcIdx], cells)`, store in `frame.regs[dst]` |

### `IrCompiler.compile`

- `IrInstr.CaptureUpval(dst, src)` → `CAPTURE_UPVAL dst=dst, src1=src`
- `IrInstr.LoadClosure(...)`:
  1. Run `RegisterAllocator` on `instrs` (must handle `LoadUpval`/`StoreUpval` in `LivenessAnalyzer` — `dst` of `LoadUpval` is a def, `src` of `StoreUpval` is a use; `upvalueIdx` fields are NOT registers and must not be treated as defs/uses)
  2. Rewrite inner registers; compile inner `instrs` recursively → inner `Chunk`; add to `chunk.functions` → `funcIdx`
  3. Emit `LOAD_CLOSURE dst=dst, imm=funcIdx, src1=upvalueCount`

### Compilation (AstLowerer)

1. Capture analysis — walk lambda body AST for variables from enclosing scopes
2. Per captured variable: emit `IrInstr.CaptureUpval(cellReg, varReg)`; update `locals` so subsequent outer-scope accesses emit `LoadUpval`/`StoreUpval`
3. Emit `PUSH_ARG cellReg` per cell ref (declaration order)
4. Compile lambda body in child `AstLowerer` where captured vars emit `LoadUpval upvalueIdx` / `StoreUpval upvalueIdx`
5. Throw compile error if `upvalueCount > 15`
6. Emit `IrInstr.LoadClosure(dst, ...)` with `upvalueCount`

### VM

`CallFrame` gains `upvalues: List<UpvalueCell>? = null`. `CALL` on `Value.Closure`:
```kotlin
val newFrame = CallFrame(func.chunk)
newFrame.returnDst = dst
newFrame.upvalues = func.upvalues    // shared cell references (reference capture)
args.forEachIndexed { i, v -> newFrame.regs[i] = v }
frames.addLast(newFrame)
```

**Files touched:** `Value.kt`, `OpCode.kt`, `IR.kt`, `AstLowerer.kt`, `IrCompiler.kt`, `VM.kt`.

---

## Feature 5: Imports

**Syntax:**
```
import "utils"              // binds module as namespace: utils.foo
from "utils" import foo     // pulls single name into scope
```
Single-name only. `from "utils" import foo, bar` is not supported.

### AST Changes (`AST.kt`)

- `ImportStmt(val path: Token)` — `path.type == KW_STRING`
- `ImportFromStmt(val path: Token, val name: Token)` — `path.type == KW_STRING`; `name.type == IDENTIFIER`; remove the `init { require(tokens.isNotEmpty()) }` block

### New IR Node (`IR.kt`)

`IrInstr.Import(dst: Int, path: String)`. In `IrCompiler.compile`: emit `IMPORT dst=instr.dst, imm=chunk.addString(instr.path)`.

### Parser Changes

**`parseStmt`:** Add a `check(KW_FROM) → parseImport()` branch **before the `else` clause**. The existing `check(KW_IMPORT) → parseImport()` branch is retained. Both branches call `parseImport()` using `check()` (not `consume()`), so **the keyword token is not consumed before `parseImport()` is called**.

**`parseImport()` full rewrite** — the old unconditional `consume(KW_IMPORT, ...)` is removed:
```kotlin
fun parseImport(): Stmt {
    if (check(KW_FROM)) {
        consume(KW_FROM, "Expected 'from'")
        val path = consume(KW_STRING, "Expected module path string")
        consume(KW_IMPORT, "Expected 'import'")
        val name = consume(IDENTIFIER, "Expected identifier")
        return ImportFromStmt(path, name)
    } else {
        consume(KW_IMPORT, "Expected 'import'")
        val path = consume(KW_STRING, "Expected module path string")
        return ImportStmt(path)
    }
}
```
`parseStmt` calls `parseImport()` without pre-consuming the keyword. `parseImport()` then consumes whichever keyword is current.

### New Type in `Value.kt`

`Value.Module(exports: Map<String, Value>)` — sealed subclass.

### VM State

```kotlin
private val moduleCache: MutableMap<String, Value.Module> = mutableMapOf()
private val modulesInProgress: MutableSet<String> = mutableSetOf()
```

### Execution Model

`IMPORT dst=dst, imm=pathIdx`:
1. `val path = chunk.strings[imm]`
2. Return `moduleCache[path]` if present
3. Throw if `path in modulesInProgress` (cycle)
4. `modulesInProgress.add(path)`; compile + execute in isolated `VM`; collect globals → `Value.Module`
5. `modulesInProgress.remove(path)`, cache, `frame.regs[dst] = module`

**`GET_FIELD` on `Value.Module`:** Branch before `else -> error(...)` — look up `exports[fieldName]`; throw if absent.

**Files touched:** `AST.kt`, `Value.kt`, `OpCode.kt`, `IR.kt`, `AstLowerer.kt`, `IrCompiler.kt`, `VM.kt`, `Parser.kt`.

---

## Feature 6: Config

**Syntax:**
```
config "settings.yml" {
    host: string = "localhost",
    port: int = 8080,
    debug: bool = false
}
```

### AST Changes (`AST.kt`)

Add:
```kotlin
data class ConfigFieldDecl(val name: Token, val type: Token, val default: Expr?)
```

**Replace** existing `ConfigStmt`:
```kotlin
data class ConfigStmt(val name: Token, val body: List<ConfigFieldDecl>) : Stmt()
```
`name` holds the `KW_STRING` path token.

### Lexer/Parser (all new)

1. `KW_CONFIG` in `TokenType`; `"config"` → `KW_CONFIG` in Lexer keywords map
2. `parseConfig()` — consumes `KW_CONFIG`, `STRING` path, `{`-block of comma-separated `name: type = default` declarations
3. `KW_CONFIG` branch in `parseStmt()`

### `Chunk` Additions (`Chunk.kt`)

```kotlin
data class ConfigFieldInfo(val name: String, val type: String, val hasDefault: Boolean)
data class ConfigSchemaInfo(val path: String, val fields: List<ConfigFieldInfo>)
val configSchemas: MutableList<ConfigSchemaInfo> = mutableListOf()
```

### New IR / Opcode Pieces

**`IrInstr.LoadConfig(dst: Int, schemaIdx: Int, defaultRegs: List<Int?>)`**
- `defaultRegs` has one entry per field in declaration order; `null` means no default, non-null is the virtual register holding the pre-evaluated default
- This avoids using the shared `argBuffer` entirely, preventing contamination if default expressions contain function calls

**`OpCode.LOAD_CONFIG`** — encoding: `dst=dst, imm=schemaIdx`.

**`IrCompiler.rewriteRegisters` for `LoadConfig`:** remap `dst` and every non-null element of `defaultRegs`.

### AstLowerer Lowering for `ConfigStmt`

1. Build `ConfigSchemaInfo` from the field declarations
2. For each field (in declaration order): if `default != null`, lower `default` expr to `freshReg()`, record the register; else record `null`
3. Emit `IrInstr.LoadConfig(dst = freshReg(), schemaIdx, defaultRegs)`
4. For each field: emit `LoadImm(keyReg = freshReg(), addConstant(Value.String(field.name)))`, then `GetIndex(fieldReg = freshReg(), dst, keyReg)`, record `locals[field.name] = fieldReg`

### `IrCompiler.compile` for `IrInstr.LoadConfig`

```kotlin
is IrInstr.LoadConfig -> {
    val schemaIdx = chunk.configSchemas.size
    chunk.configSchemas.add(ConfigSchemaInfo(instr.schemaPath, buildFields(instr)))
    chunk.write(OpCode.LOAD_CONFIG, dst = instr.dst, imm = schemaIdx)
}
```
Note: `schemaPath` must be accessible from `IrInstr.LoadConfig`. Add `schemaPath: String` to the IR node: `IrInstr.LoadConfig(dst, schemaPath, fields, defaultRegs)`.

### VM `LOAD_CONFIG` Handler

1. `val schema = chunk.configSchemas[imm]`
2. For each field `i` in `schema.fields`: if `frame.regs[instr.defaultRegs[i]] != null` (i.e., `hasDefault`), read the default from `frame.regs[defaultReg]`. No argBuffer usage.
3. Read/write config file at `schema.path`. Format: `.yml`/`.yaml` → snakeyaml; `.json` → org.json. Extension determines format.
4. For each field: use file value if present; use default if `hasDefault`; throw runtime error if neither.
5. **Type coercion:** `string` → string only; `int` → integer only (no widening from float); `float`/`double` → integer or float (widening conversion applied). Mismatch → runtime error with field name.
6. `frame.regs[dst] = Value.Map(result)` using `Value.String` keys.

**Note on VM access to `defaultRegs`:** The VM needs the `defaultRegs` list at runtime. Since instruction encoding only carries `dst` and `imm=schemaIdx`, the `defaultRegs` list must be stored alongside the schema in `ConfigSchemaInfo`. Extend:
```kotlin
data class ConfigSchemaInfo(val path: String, val fields: List<ConfigFieldInfo>, val defaultRegMap: List<Int?>)
```
The physical registers (after allocation) are stored here by `IrCompiler`. The VM reads `frame.regs[defaultRegMap[i]]` for each field with a default.

### Behavior

- File absent → create with all default values (error if any field has no default)
- File present → read, parse, type-check
- Fields extracted into locals via `GET_INDEX` instructions emitted by AstLowerer
- Type mismatch → runtime error

### Dependencies

Both as **`implementation`** scope in `build.gradle.kts`:
```kotlin
implementation("org.yaml:snakeyaml:2.2")
implementation("org.json:json:20231013")
```

**Files touched:** `AST.kt`, `Token.kt`, `Lexer.kt`, `Parser.kt`, `Chunk.kt`, `OpCode.kt`, `IR.kt`, `AstLowerer.kt`, `IrCompiler.kt`, `VM.kt`, `build.gradle.kts`.

---

## Implementation Order

1. **Ternary** — `QUESTION_MARK` token + weights entry + parser + lowerer
2. **Maps** — `L_BRACE` in `parsePrefix` + opcode + VM
3. **Enums** — `ClassDescriptor` body property + opcode + lowerer
4. **Lambdas/Closures** — value types + 4 IR nodes + 4 opcodes + capture analysis
5. **Imports** — `Value.Module` + parser rewrite + module cache
6. **Config** — AST change + `KW_CONFIG` + parser + `ConfigSchemaInfo` + file I/O

## Non-Goals

- Structured IR refactor (deferred)
- Type checker (future work)
- Package registry / remote imports
