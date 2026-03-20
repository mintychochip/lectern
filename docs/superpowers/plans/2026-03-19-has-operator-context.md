# `has` Operator — Inheritance Decision Context

**Date:** 2026-03-19

## Decision: Own-fields only (no inheritance walk)

The `has` operator checks only fields that have been set directly on the instance. It does **not** walk the prototype chain.

```
class Animal { init() { this.name = "dog" } }
class Dog extends Animal {}

let d = Dog()
d has "name"  // false — Dog's init never set this field
```

This is a deliberate simplification.

---

## Why not prototype chain?

### The problem
Inheritance for field checks requires knowing, at runtime, what fields a *class* declares — before any instance sets them. The current `ClassDescriptor` only stores `methods`, not declared field names:

```kotlin
data class ClassDescriptor(
    val name: String,
    val superClass: ClassDescriptor?,  // only for method lookup
    val methods: Map<String, Value>,    // no field names here
    val readOnly: Boolean = false
)
```

Fields are entirely instance-level in the current design. `GET_FIELD` only checks `instance.fields` (per-instance map), not any class-level declaration.

### What it would take to add inheritance
To make `has` walk the prototype chain, you'd need to either:

**Option A:** Track declared fields on `ClassDescriptor`
```kotlin
data class ClassDescriptor(
    val name: String,
    val superClass: ClassDescriptor?,
    val methods: Map<String, Value>,
    val fields: Set<String>,  // ADD THIS — declared field names
    val readOnly: Boolean = false
)
```
Then in `has`: walk `clazz.superClass` chain checking `declaredFields`. This requires updating `BUILD_CLASS` IR and `AstLowerer` to populate this set when lowering class declarations.

**Option B:** Store default field values on `ClassDescriptor`
```kotlin
data class ClassDescriptor(
    val name: String,
    val superClass: ClassDescriptor?,
    val methods: Map<String, Value>,
    val fieldDefaults: Map<String, Value>,  // ADD THIS
    val readOnly: Boolean = false
)
```
Then `has` checks `instance.fields` first, then falls back to `clazz.fieldDefaults`. Similar implementation cost to Option A.

### Tradeoffs
| Approach | Cost | Notes |
|----------|------|-------|
| Own-fields only (current) | Low | No `ClassDescriptor` changes needed |
| Option A: `fields: Set<String>` | Medium | Need to collect declared fields during AST lowering |
| Option B: `fieldDefaults` | Medium | More data to track, similar complexity |

---

## When to revisit

If this limitation becomes a problem in practice, the upgrade path is clear: add a `fields: Set<String>` to `ClassDescriptor`, populate it during class lowering, and update `HAS` dispatch to walk `superClass.fields`.

Until then, the own-fields-only approach is consistent with how `GET_FIELD` works and keeps the implementation focused.
