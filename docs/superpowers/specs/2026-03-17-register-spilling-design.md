# Register Spilling Design

**Date:** 2026-03-17
**Status:** Approved

## Problem

quill's register allocator uses linear scan over 16 physical registers. When more than 16 virtual registers are simultaneously live in a function, the allocator evicts ("spills") some virtuals but never generates save/restore code. The `rewrite()` pass in `Main.kt` throws a hard error if a virtual is unallocated. This means high register-pressure functions either crash at compile time or silently produce wrong code.

## Goal

Full correctness: spilled registers save/restore around every use, with a clean `CompileError` as fallback if the pressure is truly unsolvable (all 16 physical regs live simultaneously at a single instruction).

## Design

### Spill storage model

Spill slots mirror the real-machine frame-pointer pattern. The compiler pre-calculates the number of spill slots `N` needed for a function. At frame creation the VM allocates an array of size `N` in the `CallFrame`. Spills are addressed by fixed slot index (not push/pop), matching `mov [rbp - slot*8]` on x86. This is safe across branches and loops because slot indices are static.

Slot index is stored in the `imm` field of the encoded instruction (12 bits → max 4095 spill slots; practically unbounded since at most 16 slots are ever needed).

---

### Section 1: New data types

**`IR.kt`** — two new instructions:

```kotlin
data class Spill(val slot: Int, val src: Int) : IrInstr()    // spills[slot] = regs[src]
data class Unspill(val dst: Int, val slot: Int) : IrInstr()  // regs[dst] = spills[slot]
```

**`OpCode.kt`** — two new opcodes:

```kotlin
SPILL(0x26),    // encoding: imm=slot, src1=reg
UNSPILL(0x27),  // encoding: dst=reg, imm=slot
```

**`Chunk.kt`** — add `var spillSlotCount: Int = 0`. Must be `var` because `IrCompiler` constructs `Chunk` first and sets this field after compilation. Used by the VM to size the spill array at frame creation.

---

### Section 2: RegisterAllocator changes

The allocator currently exposes `spills` as a side-effect field on the instance, which is lost when callers construct `RegisterAllocator()` inline and discard the instance. Instead, `allocate()` returns an `AllocResult` that bundles all outputs:

```kotlin
data class AllocResult(
    val allocation: Map<Int, Int>,  // virtual → physical (non-spilled virtuals)
    val spills: Map<Int, Int>,      // virtual → spill slot index
    val spillSlotCount: Int
)
```

The `spills: mutableMapOf<Int, Int>()` instance field and `spillSlot` counter are removed; their values are accumulated locally inside `allocate()` and returned via `AllocResult`.

The allocator itself only decides _what_ to spill. Spill instruction insertion is delegated to a new `SpillInserter` pass.

---

### Section 3: SpillInserter pass

A new class `SpillInserter` takes:
- the IR instruction list
- the `AllocResult`
- the `LiveRange` map from `LivenessAnalyzer`

It produces a rewritten instruction list where all spilled virtuals are replaced with physical registers and `Spill`/`Unspill` instructions are injected.

**Computing per-instruction live physical registers:**
`LivenessAnalyzer` returns `Map<Int, LiveRange>` (virtual → `LiveRange(start, end)`). `SpillInserter` must invert this to get the set of live physical registers at instruction index `i`:

```
livePhysAt(i) = { allocation[v] | v in ranges, ranges[v].start <= i <= ranges[v].end, v in allocation }
```

This inversion is computed once upfront before scanning instructions.

**Instruction rewriting:**
For each instruction at index `i`, maintain a `claimedTemps: MutableSet<Int>` initialized to empty at the start of that instruction's rewrite. For each spilled operand (src or dst), select:

```
tempReg = first register in (0..15) - livePhysAt(i) - claimedTemps
```

Add `tempReg` to `claimedTemps` before processing the next operand of the same instruction. This prevents two spilled operands in the same instruction from being assigned the same temp register (e.g., a `BinaryOp` with both `src1` and `src2` spilled).

- For each **src** register that is a spilled virtual `v` (i.e., `v` is in `spills`):
  - Select `tempReg` as above; add to `claimedTemps`
  - Insert `Unspill(tempReg, spills[v])` immediately before this instruction
  - Replace the src with `tempReg`
- For each **dst** register that is a spilled virtual `v`:
  - Select `tempReg` as above; add to `claimedTemps`
  - Replace the dst with `tempReg`
  - Insert `Spill(spills[v], tempReg)` immediately after this instruction

If `(0..15) - livePhysAt(i) - claimedTemps` is empty at any point, throw:

```
CompileError("Function exceeds register pressure: all 16 registers live at instruction $i")
```

After `SpillInserter` runs, no spilled virtuals remain in the IR. The existing `rewrite()` and `rewriteRegisters()` passes only see allocated virtuals; the `error("v$reg not allocated")` throw becomes a true invariant violation.

---

### Section 4: VM changes

**`CallFrame`** — `CallFrame` is a `data class` with a primary constructor. Kotlin does not allow a default parameter to reference another constructor parameter. The `spills` array is therefore added as a regular (non-default) field initialised in an `init` block, or `CallFrame` is changed to a plain `class` with an explicit constructor body:

```kotlin
data class CallFrame(
    val chunk: Chunk,
    var ip: Int = 0,
    val regs: Array<Value?> = arrayOfNulls(16),
    var returnDst: Int = 0,
    val argBuffer: ArrayDeque<Value> = ArrayDeque()
) {
    val spills: Array<Value?> = arrayOfNulls(chunk.spillSlotCount)
}
```

**VM dispatch** — two new cases in the main `when (opcode)` block:

```kotlin
OpCode.SPILL   -> frame.spills[imm] = frame.regs[src1]
OpCode.UNSPILL -> frame.regs[dst]   = frame.spills[imm]!!
```

**`executeDefaultChunk()`** — the VM has a second, reduced opcode interpreter for evaluating default parameter values. It also needs `SPILL`/`UNSPILL` cases with identical implementations, or it will throw `"Unsupported opcode in default value"` if a default-value expression is compiled with spills (unlikely but possible with complex defaults).

---

### Section 5: IrCompiler changes

`IrCompiler` contains three internal compilation sub-pipelines that each call `RegisterAllocator().allocate()` directly and use a private `rewriteRegisters()` helper (a duplicate of `Main.kt`'s `rewrite()`):

1. **`LoadFunc`** — compiles function bodies (lines ~67–103)
2. **`LoadClass` methods** — compiles method bodies (lines ~131–143)
3. **Default value expressions** — compiles default parameter chunks (lines ~82–96)

All three must be updated:
- Change `RegisterAllocator().allocate(...)` calls to consume `AllocResult`
- Run `SpillInserter` on the result before calling `rewriteRegisters()`
- Set `chunk.spillSlotCount` from `AllocResult.spillSlotCount`

`rewriteRegisters()` itself must also handle `IrInstr.Spill` and `IrInstr.Unspill` — they use physical registers by the time they reach this function, so the rewrite is a no-op passthrough (add them to the `else -> instr` branch, or handle explicitly as identity).

**New compilation cases** in the main IR→bytecode path:

```kotlin
is IrInstr.Spill   -> emit(encode(OpCode.SPILL,   src1 = instr.src, imm = instr.slot))
is IrInstr.Unspill -> emit(encode(OpCode.UNSPILL, dst  = instr.dst, imm = instr.slot))
```

---

### Section 6: Updated pipeline

**`Main.kt`** top-level pipeline:

```
AstLowerer
  → LivenessAnalyzer                    (returns Map<Int, LiveRange>)
  → RegisterAllocator                   (returns AllocResult)
  → SpillInserter                       (injects Spill/Unspill, replaces spilled virtuals with physical regs)
  → rewrite()                           (maps remaining virtual regs → physical regs)
  → IrCompiler                          (emits SPILL/UNSPILL opcodes, sets spillSlotCount on Chunk)
  → VM                                  (CallFrame has spills array, dispatches SPILL/UNSPILL)
```

**`IrCompiler` nested pipelines** (LoadFunc, LoadClass, defaults) follow the same sequence internally.

---

## Testing

New file: `src/test/kotlin/org/quill/ast/RegisterSpillTest.kt`

1. **Basic spill** — function with >16 simultaneously live virtuals compiles and produces correct output.
2. **Spill across a branch** — spilled virtual survives an `if`/`else`, correctly reloaded on both paths.
3. **Pressure error** — function with all 16 registers live at one instruction produces a clean `CompileError`, not a crash or silent wrong output.

---

## Files changed

| File | Change |
|------|--------|
| `lang/IR.kt` | Add `Spill`, `Unspill` instructions |
| `lang/OpCode.kt` | Add `SPILL(0x26)`, `UNSPILL(0x27)` |
| `lang/Chunk.kt` | Add `var spillSlotCount: Int = 0` |
| `ast/RegisterAllocator.kt` | Return `AllocResult`; remove side-effect `spills` field |
| `ast/SpillInserter.kt` | New class — inverts liveness, injects Spill/Unspill, selects temp regs |
| `ast/IrCompiler.kt` | Update all three internal sub-pipelines (LoadFunc, LoadClass, defaults) to use AllocResult + SpillInserter; add Spill/Unspill to `rewriteRegisters()`; compile Spill/Unspill to opcodes; set `spillSlotCount` |
| `ast/VM.kt` | Add `spills` array to `CallFrame` via `init` block; dispatch `SPILL`/`UNSPILL` in main loop and `executeDefaultChunk()` |
| `Main.kt` | Wire `SpillInserter` between `RegisterAllocator` and `rewrite()` |
| `test/.../RegisterSpillTest.kt` | New test file |
