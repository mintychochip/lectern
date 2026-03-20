# Standard Library: Set and Tuple — Design

## Context

Lectern currently has `Map` (hash-based, mutable) and `Array` (list, mutable) as built-in collection types. The HashMap-backed Map is fully implemented. This design adds:

- **Set** — unordered collection of unique values, mutable
- **Tuple** — ordered, immutable sequence

## Syntax

| Type | Literal Syntax | Factory Syntax |
|------|---------------|----------------|
| Set  | `{1, 2, 3}`   | `Set(1, 2, 3)` |
| Tuple | `(1, 2, 3)`  | `Tuple(1, 2, 3)` |

### Parsing Notes

- `{...}` set literals: requires lexer/parser support. `{expr, expr, ...}` with one or more elements parses as a set literal. Empty `{}` is parsed as a block statement, not a set literal. Single-element `{expr}` is valid — `{x}` is a set with one element, distinct from `{ x }` block-with-expression-statement.
- `(...)` tuple literals: requires parser support; ambiguous with grouping parentheses — resolved by requiring 2+ elements or a trailing comma `(1,)` at parse-time. `(expr)` without comma is grouping, not a tuple. `()` (zero elements) is the empty tuple literal. `(expr,)` (trailing comma) is a single-element tuple.

## Implementation

### 1. Storage Wrappers (`Value.kt`)

```kotlin
/** Wrapper for internal MutableSet storage (used by Set class) */
data class InternalSet(val entries: MutableSet<Value> = mutableSetOf()) : Value() {
    override fun toString() = entries.joinToString(", ", "{", "}")
}

/** Wrapper for internal List storage (used by Tuple class) — immutable */
data class InternalTuple(val items: List<Value>) : Value() {
    override fun toString() = items.joinToString(", ", "(", ")")
}
```

Both parallel existing `InternalList` / `InternalMap` patterns.

### 2. SetClass (`Value.kt`, `Builtins` object)

```
methods:
  init(self, ...constructorArgs) →
      self.fields["__entries"] = InternalSet()
      for each arg in constructorArgs: entries.add(arg)
      return Null
  add(item) → entries.add(item); return Null
  has(item) → return Boolean(entries.contains(item))
  remove(item) → entries.remove(item); return Null
  size      → return Int(entries.size)
  clear     → entries.clear(); return Null
  delete(item) → alias for remove(item)
  iter      → return SetIteratorClass instance (see Section 8)

internal storage:
  self.fields["__entries"] = InternalSet (MutableSet<Value>)
```

Note: `NEW_INSTANCE` passes constructor args to `init` via the arg buffer. `init(self, ...args)` receives `args[0] = self`, `args[1..] = constructor arguments`. For `Set()`, `constructorArgs` is empty.

Note: set iteration order is undefined but must be deterministic within a single `iter()` call.

### 3. TupleClass (`Value.kt`, `Builtins` object)

```
methods:
  init(self, ...constructorArgs) →
      self.fields["__tuple"] = InternalTuple(constructorArgs.toList())
      return Null
  size      → return Int((self.fields["__tuple"] as InternalTuple).items.size)
  get(index) → return items[index] (0-based); returns Value.Null on out-of-bounds
  has(item) → return Boolean(items.contains(item))
  iter      → return ArrayIteratorClass instance over items (reuse ArrayIteratorClass)
```

Tuple is immutable — **no `set`, `add`, `remove`, `clear` methods**.

**Internal storage:** `self.fields["__tuple"] = InternalTuple(items)` — the items list is stored inside the `InternalTuple` wrapper, not spread across individual fields.

```
factory constructor: Tuple(arg1, arg2, ...)
  → LoadClass("Tuple")
  → NewInstance with args [arg1Reg, arg2Reg, ...]
  → NEW_INSTANCE drains arg buffer and passes to init(self, arg1, arg2, ...)
  → init() constructs InternalTuple(args) and stores in self.fields["__tuple"]
```

Note: `NEW_INSTANCE` passes constructor args to `init` via the arg buffer. `init(self, ...args)` receives `args[0] = self`, `args[1..] = constructor arguments`. For `Tuple()`, `constructorArgs` is empty.

**Empty tuple `()`**: Valid. `Tuple()` with zero arguments is the empty tuple literal. Single element `(a,)` requires trailing comma; `(a)` without a trailing comma is grouping, not a tuple.

### 4. VM Globals (`VM.kt`)

```kotlin
globals = mutableMapOf(
    "Set" to Value.Class(Builtins.SetClass),
    "Tuple" to Value.Class(Builtins.TupleClass),
    ...
)
```

### 5. Parser Changes

**Set literal `{expr, expr, ...}`:**
- New `SetExpr` AST node: `data class SetExpr(val elements: List<Expr>)`
- Lowered as factory call: `Set(expr1, expr2, ...)` — no new IR instruction needed
- Disallow empty `{}` as set literal (parsed as block statement instead)

**Tuple literal `(expr, expr, ...)`:**
- New `TupleExpr` AST node: `data class TupleExpr(val elements: List<Expr>)`
- Enforce immutability at construction — `InternalTuple` is immutable
- Single-element tuple syntax `(expr,)` supported via trailing comma
- `(expr)` without comma remains grouping parentheses

### 6. Lowering (`AstLowerer.kt`)

```kotlin
is Expr.SetExpr -> {
    // Desugar to Set(element1, element2, ...)
    val elementRegs = expr.elements.map { lowerExpr(it, freshReg()) }
    val setClassReg = freshReg()
    emit(IrInstr.LoadGlobal(setClassReg, "Set"))
    val dstReg = freshReg()
    emit(IrInstr.NewInstance(dstReg, setClassReg, elementRegs))
    dstReg
}
is Expr.TupleExpr -> {
    // Desugar to Tuple(element1, element2, ...)
    val elementRegs = expr.elements.map { lowerExpr(it, freshReg()) }
    val tupleClassReg = freshReg()
    emit(IrInstr.LoadGlobal(tupleClassReg, "Tuple"))
    val dstReg = freshReg()
    emit(IrInstr.NewInstance(dstReg, tupleClassReg, elementRegs))
    dstReg
}
```

Both reuse existing `IrInstr.NewInstance` — no new IR instructions needed.

### 7. Tuple Indexing

Support `tuple[0]`, `tuple[1]`, etc. via the existing `IndexExpr` lowering in `AstLowerer.kt`. The `GetIndex` IR instruction is reused — no new instruction needed.

The `IndexExpr` lowering in `AstLowerer.kt` already emits `IrInstr.GetIndex`. Extend the VM's `executeGetIndex` method (which pattern-matches on `obj`) to handle `InternalTuple`:

```kotlin
// In VM.executeGetIndex, extend the when clause on obj:
is Value.InternalTuple -> {
    val idx = args[1]
    when (idx) {
        is Value.Int -> obj.items.getOrElse(idx.value) { Value.Null }
        else -> Value.Null
    }
}
```

The `GetIndex` instruction already handles `Value.InternalList` and `Value.InternalMap` in the same way — `InternalTuple` is added as a new branch in that same `when` block.

**Out-of-bounds behavior**: `getOrElse` returns `Value.Null` for out-of-range indices (consistent with `Array.get` which also returns `Value.Null` on out-of-bounds via `items.getOrElse(idx) { Value.Null }`).

### 8. Iterator Protocol

Both Set and Tuple use the iterator protocol (`iter()` → `hasNext()` → `next()`) already established by Array.

**Tuple** reuses `ArrayIteratorClass` directly because `InternalTuple` is immutable — the items list never changes, so no snapshot is needed and `ConcurrentModificationException` is impossible.

**Set** requires its own `SetIteratorClass` because `MutableSet` is mutated by `add`/`remove`/`clear` during iteration. The `iter()` method must snapshot the set's entries into a `List<Value>` at iteration start:

```
SetIteratorClass:
  fields:
    __entries (InternalSet)    — held in self.fields["__entries"]
    __items (InternalList)    — snapshot stored as InternalList(self.fields["__entries"].entries.toList())
    current (Int)              — stored in self.fields["current"]
  hasNext → current < (self.fields["__items"] as Value.InternalList).items.size
  next    → val items = (self.fields["__items"] as Value.InternalList).items
            val cur = (self.fields["current"] as Value.Int).value
            self.fields["current"] = Value.Int(cur + 1)
            items[cur]
```

The `__items` snapshot is created once in `SetIteratorClass.iter()` by calling `entries.toList()` on the `InternalSet` and wrapping in `InternalList` — matching how `ArrayIteratorClass` stores its items. This ensures `for x in set { set.add(something) }` does not cause `ConcurrentModificationException`, and that iteration order is stable within a single `iter()` call even if the set is concurrently modified.

## Error Handling

Follows existing Lectern patterns (no new error mechanisms):

| Operation | Invalid Input | Behavior |
|-----------|--------------|----------|
| `Tuple.get(i)` | out-of-bounds index (`i < 0` or `i >= size`) | returns `Value.Null` (consistent with `Array.get`) |
| `Tuple.get(i)` | non-integer index | returns `Value.Null` |
| `Tuple[bad_index]` | non-integer index expression | handled at VM level, returns `Value.Null` |
| `Set.remove(i)` | item not present | `MutableSet.remove` returns false; no error thrown |
| `Set.delete(i)` | item not present | alias for `remove`, same silent behavior |
| `null` in Set | `null` as element | allowed; `MutableSet.contains(null)` works correctly |
| `null` in Tuple | `null` as element | allowed; `InternalTuple` stores any `Value` |

No type errors are raised at runtime for these cases — Lectern's type system is dynamic at runtime.

## What Is Not In Scope

- Set operations `union`, `intersection`, `difference` as built-in methods — can be added later as Lectern stdlib functions
- Ordered sets or linked sets
- Nested tuples / heterogeneous tuples (all types in a tuple use the same `Value` representation)
- `for x in set` with undefined order acknowledged (iteration order is JVM's hashmap order)

## Additional Notes

**For loop integration**: `for x in expr { body }` already desugars to `let __iter = expr.iter(); while(__iter.hasNext()) { let x = __iter.next(); body }` in `lowerForRange` (`AstLowerer.kt`, line 169). No changes needed — Set and Tuple only need to implement `iter()`, `hasNext()`, and `next()`.

**Negative indexing**: Not supported. `tuple[-1]` returns `Value.Null`, consistent with Array behavior.

**Equality (`==`)**: Works via `Value.equals` (Kotlin data class equality). `Value.Int`, `Value.String`, etc. use value equality. `Value.Instance` uses reference equality unless the class overrides it. No special handling needed for Set or Tuple.

## Testing

- `SetClass` unit tests: add, has, remove, size, clear, duplicate deduplication, iteration
- `TupleClass` unit tests: get, size, has, iteration, immutability (no set/add/remove/clear)
- Integration tests: `{1, 2, 3}.has(2)`, `for x in (1, 2, 3)`, `let t = (1, 2); t[0]`
- Parser tests: `{a, b}`, `(a, b)`, `(a,)`, empty `{}` as block, `(a)` as grouping
