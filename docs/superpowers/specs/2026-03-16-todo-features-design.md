# Lectern TODO Features Design Spec

**Date:** 2026-03-16
**Scope:** Implement 7 unfinished language features: Ternary, Map, Lambda, Enum, Table, Config, Import
**Approach:** Desugar into existing IR/VM constructs wherever possible

## Major Refactor: Unify Collections as Classes

Before implementing new features, unify `Value.List`, `Value.Map`, and `Value.Range` (plus `Value.Iterator`) into `Value.Instance` backed by built-in classes.

### Changes

- **Remove:** `Value.List`, `Value.Map`, `Value.Range`, `Value.Iterator`
- **Add built-in classes** registered as globals at VM startup:
  - `Array` — backed by `MutableList<Value>` internally, exposes `get(index)`, `set(index, value)`, `size()`, `push(value)`, `iter()`, `hasNext()`, `next()`
  - `Map` — backed by `MutableMap<Value, Value>` internally, exposes `get(key)`, `set(key, value)`, `size()`, `keys()`, `values()`, `delete(key)`
  - `Range` — backed by start/end ints, exposes `iter()`, `hasNext()`, `next()`
- **Remove opcodes:** `NEW_ARRAY`, `NEW_MAP` (if exists), `RANGE`
- **Remove IR instructions:** `NewArray`
- Array/Map/Range creation goes through `NewInstance` like any other class
- `GET_INDEX` / `SET_INDEX` remain but dispatch through the instance's `get`/`set` methods

---

## Feature 1: Ternary Expressions

**Syntax:** `condition ? thenExpr : elseExpr`

### Lexer
- Add `QUESTION` token type for `?`

### Parser
- After parsing a complete expression, check for `?`
- If found: parse then-branch expression, consume `:` (COLON), parse else-branch expression
- Precedence: lower than all other operators except assignment
- Produces `Expr.TernaryExpr(condition, thenBranch, elseBranch)`

### AstLowerer — Desugar to if/jump
```
condReg = <condition>
JumpIfFalse condReg -> elseLabel
dst = <thenExpr>
Jump -> endLabel
elseLabel:
dst = <elseExpr>
endLabel:
```

### New IR/Opcodes: None

---

## Feature 2: Map Literals

**Syntax:** `{ key: value, key2: value2 }`

### Lexer
- No changes — `{`, `}`, `:`, `,` all exist

### Parser
- Disambiguate from block statement: in expression position, if `{` is followed by `expr :`, parse as map
- Parse comma-separated `key : value` pairs
- Produces `Expr.MapExpr(entries: List<Pair<Expr, Expr>>)`

### AstLowerer — Desugar to NewInstance + method calls
```
// { "a": 1, "b": 2 }
// becomes:
mapReg = NewInstance(Map, [])
Call(_, map.set, ["a", 1])
Call(_, map.set, ["b", 2])
```

### New IR/Opcodes: None — uses NewInstance and Call

---

## Feature 3: Lambda Expressions

**Syntax:** `(params) -> { body }`

### Lexer
- No changes — `ARROW` (`->`) already exists

### Parser
- When seeing `(`, attempt to parse as param list
- If followed by `->`, parse as lambda; otherwise backtrack to grouped/call expression
- Requires `{` body `}` (braces always required)
- Produces `Expr.LambdaExpr(params: List<Param>, body: Stmt.BlockStmt)`

### AstLowerer — Desugar to anonymous LoadFunc
```
// (x, y) -> { return x + y }
// becomes:
// fn __lambda_0(x, y) { return x + y }
```
- Create fresh AstLowerer, set up params, lower body
- Emit `LoadFunc(dst, "__lambda_N", arity, instrs, constants, defaultValues)`
- No `StoreGlobal` — lambda stays in register only

### New IR/Opcodes: None

---

## Feature 4: Enums

**Syntax:** `enum Color { RED, GREEN, BLUE }` (already parsed)

### Lexer/Parser
- No changes — already fully parsed as `Stmt.EnumStmt(name, values)`

### AstLowerer — Desugar to class + instances
```
// enum Color { RED, GREEN, BLUE }
// becomes:
classReg = LoadClass("Color", null, {})
// Color.RED = instance with { name: "RED", ordinal: 0 }
enumValReg = NewInstance(classReg, [])
SetField(enumValReg, "name", "RED")
SetField(enumValReg, "ordinal", 0)
SetField(classReg, "RED", enumValReg)
// repeat for GREEN (ordinal 1), BLUE (ordinal 2)
StoreGlobal("Color", classReg)
```

### Runtime behavior
- `Color.RED.name` → `"RED"`
- `Color.RED.ordinal` → `0`
- `Color.RED == Color.GREEN` → `false` (instance identity)
- `print(Color.RED)` → `"RED"` (toString via name field)

### New IR/Opcodes: None

---

## Feature 5: Table (replaces Record)

**Syntax:**
```
table Users {
    key id: int
    name: string
    email: string
    age: int = 0
}
```

### Lexer
- Add `KW_TABLE` keyword
- Add `KW_KEY` keyword

### Parser
- New `parseTable()` function
- Parses field declarations: optional `key` modifier, name `:` type, optional `= defaultValue`
- **New AST node:** `Stmt.TableStmt(name: Token, fields: List<TableField>)`
- **New data class:** `TableField(name: Token, type: Token, isKey: Boolean, defaultValue: Expr?)`
- Remove `Stmt.RecordStmt` from AST

### AstLowerer
- Emits a class with SQLite-backed storage
- Creates built-in methods: `delete(key)`
- Bracket access via `GET_INDEX` / `SET_INDEX` for reads and writes

### VM/Runtime
- On first use, creates SQLite table with the defined schema
- `Users[1]` → SELECT WHERE key = ?
- `Users[1] = { name: "Bob", ... }` → INSERT OR REPLACE with defaults for omitted fields
- `Users.delete(1)` → DELETE WHERE key = ?
- SQLite file: auto-named relative to script

### Dependencies
- `org.xerial:sqlite-jdbc` added to Gradle

### New IR/Opcodes: None — uses class/instance infrastructure

---

## Feature 6: Config

**Syntax:**
```
config Settings {
    name: string = "default"
    port: int = 8080
}
```

### Lexer
- Add `KW_CONFIG` keyword

### Parser
- New `parseConfig()` function
- Parses: `config <name> { field declarations }`
- Field: name `:` type, optional `= defaultValue`
- **Revised AST node:** `Stmt.ConfigStmt(name: Token, fields: List<ConfigField>)`
- **New data class:** `ConfigField(name: Token, type: Token, defaultValue: Expr?)`

### File resolution
- Name converted to kebab-case + `.yml`
- `Settings` → `settings.yml`
- `DatabaseConfig` → `database-config.yml`
- Resolved relative to the script file

### AstLowerer
- Desugars to class instance with fields populated from YAML at initialization
- Missing field + no default = runtime error
- Read-only at runtime (SetField on config instances throws error)

### Dependencies
- SnakeYAML (or similar) added to Gradle

### New IR/Opcodes: None

---

## Feature 7: Imports

**Syntax:**
```
import utils              // namespaced: utils.doSomething()
import spawn from arena   // direct: spawn()
```

### Lexer/Parser
- No changes — already fully parsed as `ImportStmt` and `ImportFromStmt`

### AstLowerer + VM/Runtime

**`import utils`:**
1. Resolve `utils.lec` relative to current script
2. Lex, parse, lower, compile the module into its own chunk
3. Execute the chunk
4. Capture module's globals into a namespace instance
5. `StoreGlobal("utils", namespaceInstance)`
6. `utils.doSomething()` works via `GetField` on the namespace

**`import spawn from arena`:**
1. Same resolution and execution as above
2. Extract only requested symbols from module globals
3. `StoreGlobal("spawn", value)` — injected directly into caller's scope

### Module caching
- Each file loaded and executed once
- Subsequent imports return cached globals

### Circular imports
- Detect and throw a clear error

### New IR/Opcodes: None

---

## Build Order

1. **Collection refactor** — unify Value.List/Map/Range into classes (prerequisite for Map)
2. **Ternary** — standalone, smallest change
3. **Map** — depends on collection refactor
4. **Lambda** — standalone
5. **Enum** — standalone
6. **Table** — needs SQLite dependency, new AST
7. **Config** — needs YAML dependency, new AST
8. **Import** — needs module resolution, touches pipeline

## Files Affected

- `Token.kt` — new token types (QUESTION, KW_TABLE, KW_KEY, KW_CONFIG)
- `Lexer.kt` — lex `?`, new keywords
- `AST.kt` — revise ConfigStmt, replace RecordStmt with TableStmt, add TableField/ConfigField
- `Parser.kt` — parseTable(), parseConfig(), parseTernary(), parseMap(), parseLambda()
- `AstLowerer.kt` — implement all 7 TODO branches
- `Value.kt` — remove List, Map, Range, Iterator
- `VM.kt` — register built-in Array/Map/Range classes, refactor collection handling
- `OpCode.kt` — remove NEW_ARRAY, RANGE opcodes
- `IrInstr.kt` (IR.kt) — remove NewArray instruction
- `IrCompiler.kt` — update for removed opcodes
- `Chunk.kt` — no changes expected
- `build.gradle.kts` — add sqlite-jdbc, snakeyaml dependencies
