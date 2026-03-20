# Optimization Passes — Induction Variable Recognition + Cross-Block GVN

> **Status:** Approved

## Overview

Two new optimization passes for the Quill compiler's optimization pipeline:

1. **Induction Variable Recognition (IVR)** — recognizes `for x in a..b` patterns and replaces the expensive iterator protocol (`.iter()`, `.hasNext()`, `.next()`) with direct arithmetic
2. **GVN Across Blocks** — extends the existing intra-block GVN to propagate value equivalence across basic blocks using domination information

Both passes target loop-heavy Quill scripts where the iterator desugaring overhead is measurable.

---

## 1. Induction Variable Recognition Pass

### Motivation

Quill's `for x in a..b` desugars to an iterator protocol:

```
let __iter = (a..b).iter()     // GetField + Call
while (__iter.hasNext()) {      // GetField + Call + JumpIfFalse
    let x = __iter.next()       // GetField + Call (VIRTUAL, expensive)
    body
}
```

Each `.next()` call is a virtual method invocation — significant overhead inside tight loops. When the range is known and the step is constant (integer increment by 1), this can be replaced with:

```
let x = a
while (x <= b) {
    body
    x = x + 1                   // just ADD
}
```

### Detection Strategy

The pass operates on IR form, analyzing CFG natural loops. It recognizes the following pattern emitted by `lowerForRange` in `AstLowerer`:

1. `GetField(iterReg, iterableReg, "iter")` + `Call(iterReg, iterReg, [])` — get iterator
2. `Label(topLabel)` — loop header
3. `GetField(condReg, iterReg, "hasNext")` + `Call(condReg, condReg, [])` — condition
4. `JumpIfFalse(condReg, endLabel)` — exit check
5. `GetField(valueReg, iterReg, "next")` + `Call(valueReg, valueReg, [])` — get next value
6. Jump back to `topLabel`

This is the Range iterator pattern. The pass replaces it with:

1. Load initial value `a`
2. `Label(topLabel)`
3. Compare `x <= b` (JUMP if false to end)
4. body (with x live)
5. `x = x + 1` (or `x = x - 1` for descending ranges)
6. Jump to `topLabel`

### Transformation Rules

| Pattern | Replacement |
|---------|-------------|
| `for x in a..b` where a ≤ b | `let x = a` + `while (x <= b) { body; x = x + 1 }` |
| `for x in b..a` where b > a | `let x = b` + `while (x >= b) { body; x = x - 1 }` |
| `for x in a..b step s` | Same structure with `x = x + s` |

The pass must also:
- Update `break` and `next` label targets accordingly
- Remove the now-unused iterator registers from the IR
- Handle nested loops (each loop normalized independently)

### Placement in Pipeline

**IR-level pass** — runs as part of the standard IR optimization pipeline (`opt/passes/`), before SSA conversion. This makes sense because the pattern is most visible before SSA form collapses the structure.

Alternative: run as an SSA pass post-conversion, which could handle phi functions at loop headers. For v1, IR-level is simpler and sufficient.

### Files

- New: `src/main/kotlin/org/quill/opt/passes/InductionVariablePass.kt`

### Safety Conditions

The transformation is safe when:
1. The range is a `Range` literal (`a..b`) — the built-in Range class semantics are known
2. The step is constant (1 or -1 for now)
3. The loop variable is not captured by a closure (Quill doesn't support closures yet, so this is always safe)
4. The loop variable is not passed by reference to an inner function

### Open Issues for v2+

- **Descending ranges** (`for x in 10..0`) — step direction needs verification
- **Custom iterators** — currently out of scope; iterator protocol optimization only for known built-in Range
- **Step other than 1** — `for x in 0..10 step 2` would need the `step` keyword implemented first

---

## 2. GVN Across Blocks

### Motivation

The current `SsaGlobalValueNumberingPass` is intra-block only — it processes each basic block independently. This misses redundancy that spans block boundaries:

```
Block 0:                         Block 1:
  r0 = LoadImm #1                   r3 = r0 + r0    # redundant with r1
  r1 = r0 + r0
  Jump -> Block 1
```

Both `r1` and `r3` compute the same expression, but intra-block GVN can't see across blocks.

### Approach

Extends the existing `SsaGlobalValueNumberingPass` to propagate value equivalence across blocks using domination.

**Key insight:** If block A dominates block B, then every path from entry to B passes through A. So any value defined in A is available in B (unless a side effect in an intermediate block invalidates it).

### Algorithm

1. **Process blocks in domination order** — visit dominated blocks after their dominators
2. **Maintain a global value table** — maps `ExprHash` → canonical `SsaValue`, shared across all processed blocks
3. **Propagate from dominator** — when entering a block, inherit the value table from its immediate dominator
4. **Invalidate on side effects** — conservative: any side-effecting instruction in a block invalidates matching hashes for all blocks it dominates
5. **Add local definitions** — after processing each block, add its definitions to the table for dominated blocks

### Handling Merge Points

When a block has multiple predecessors (a join point), the value table from the immediate dominator may not accurately reflect all reaching definitions. The conservative approach:

- At a join point (block with 2+ predecessors), invalidate all hashes that might be affected by different paths
- Specifically: for any `Move` we would insert, verify that the canonical value dominates all paths to the current block
- If not (e.g., different predecessors define different values for the same expression), don't substitute

This means GVN across blocks is most effective on diamond-free CFGs (which many Quill-compiled loops are).

### Interaction with Existing GVN

The intra-block pass remains as-is. The cross-block version is a separate, newer pass that:
- Runs after intra-block GVN has already cleaned up within-block redundancy
- Uses a shared global table rather than per-block tables
- Must be conservative at control flow merges to avoid incorrect substitutions

### Files

- Modify: `src/main/kotlin/org/quill/ssa/passes/SsaGlobalValueNumberingPass.kt` — add cross-block logic as an option (default off for safety)
- Or: create `src/main/kotlin/org/quill/ssa/passes/SsaCrossBlockGvnPass.kt` as a separate pass

**Decision:** Create a separate `SsaCrossBlockGvnPass` to avoid risk to the existing stable pass. The pipeline can include both.

### Safety Conditions

GVN across blocks is safe when:
1. The expression has no side effects
2. No aliasing can occur between the canonical definition and the use site
3. All paths from entry to the use site pass through the block defining the canonical value (guaranteed by domination)

Conservative invalidation (side effect → clear matching hashes) is correct but may miss optimization opportunities.

---

## Pipeline Integration

### Proposed Pass Order

```
pre-SSA passes:
  - ConstantFoldingPass
  - CopyPropagationPass
  - StrengthReductionPass
  - InductionVariablePass       # NEW: normalize Range loops
  - DeadCodeEliminationPass

SSA passes:
  - SsaConstantPropagationPass
  - SsaDeadCodeEliminationPass
  - SsaGlobalValueNumberingPass   # intra-block
  - SsaCrossBlockGvnPass          # NEW: cross-block GVN

post-SSA passes:
  - DeadCodeEliminationPass
```

The existing `optimizedSsaRoundTrip` in `IrCompiler.kt` wires in the SSA passes. `InductionVariablePass` is an IR-level pass and runs before SSA conversion.

### Wiring

For `InductionVariablePass`:
- Add to `OptimizationPipeline.kt` as a pre-SSA pass
- `OptimizationPipeline.optimizeWithSsa` signature may need adjustment to accept IR-level passes separately

For `SsaCrossBlockGvnPass`:
- Add to the SSA pass list in `IrCompiler.optimizedSsaRoundTrip`
- Off by default; enable via a flag or as part of a separate optimization level

---

## Testing Strategy

### Induction Variable Pass

```kotlin
@Test
fun testForRangeInductionVariableReplacement() {
    // for i in 0..10 should not call .next() or .hasNext()
    val output = compileAndRun(
        """
        var sum = 0
        for i in 0..10 {
            sum = sum + i
        }
        print(sum)
        """.trimIndent()
    )
    assertEquals(listOf("55"), output)
}
```

Test cases:
- Ascending range `0..10`
- Descending range `10..0`
- Empty range (no iterations)
- Nested loops
- Loop with `break` and `next`

### GVN Across Blocks

```kotlin
@Test
fun testGvnAcrossBlocks() {
    val output = compileAndRun(
        """
        let x = 5 + 3
        if true {
            let y = 5 + 3
            print(y)
        }
        print(x)
        """.trimIndent()
    )
    // Both should print 8; second 5+3 should be GVN'd
    assertEquals(listOf("8", "8"), output)
}
```

Test cases:
- Diamond CFG (both paths define same expression)
- Loop body (redundant computation inside loop)
- Multiple predecessors with different definitions (should NOT substitute)

---

## Files to Create/Modify

### New Files
- `src/main/kotlin/org/quill/opt/passes/InductionVariablePass.kt`
- `src/main/kotlin/org/quill/ssa/passes/SsaCrossBlockGvnPass.kt`
- `src/test/kotlin/org/quill/opt/OptimizationPassesTest.kt` (extend existing)

### Modified Files
- `src/main/kotlin/org/quill/opt/OptimizationPipeline.kt` — add IR pass support
- `src/main/kotlin/org/quill/lang/IrCompiler.kt` — add `SsaCrossBlockGvnPass` to pipeline

---

## Open Questions

1. **Induction variable pass: Range vs. arbitrary iterator?** For v1, only handle `a..b` (Range literal). Arbitrary iterator optimization (e.g., recognizing custom `.iter()` classes) requires class analysis and is out of scope.

2. **GVN merge point correctness?** The conservative approach (invalidate at merge) may miss opportunities. Benchmark after implementation to see if it's worth adding path-sensitive tracking.

3. **Induction variable pass: what if body modifies loop variable?** The body may contain `x = x + 5`. The normalization must handle this correctly — the `x = x + 1` at the end of each iteration must account for modifications within the body. This requires careful analysis of whether the body reassigns the loop variable.
