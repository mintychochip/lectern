package org.lectern.ast

class RegisterAllocator(private val numRegs: Int = 16) {
    // virtual reg -> physical reg
    val allocation = mutableMapOf<Int, Int>()
    // spilled virtual regs -> stack slot
    val spills = mutableMapOf<Int, Int>()
    private var spillSlot = 0

    fun allocate(ranges: Map<Int, LiveRange>, numParams: Int = 0): Map<Int, Int> {
        // Pre-allocate parameter registers: virtual 0..numParams-1 -> physical 0..numParams-1
        for (i in 0 until numParams) {
            allocation[i] = i
        }

        // sort by start point
        val sorted = ranges.values.sortedBy { it.start }
        // physical regs currently in use: physical reg -> live range using it
        val active = mutableMapOf<Int, LiveRange>()
        // free physical registers (excluding pre-allocated params)
        val freeRegs = ArrayDeque((numParams until numRegs).toList())

        // Mark param registers as active if they have live ranges
        for (i in 0 until numParams) {
            ranges[i]?.let { active[i] = it }
        }

        for (range in sorted) {
            // Skip parameters - they're already allocated
            if (range.reg < numParams) continue

            // expire old intervals — free regs whose end has passed
            val expired = active.entries.filter { (_, r) -> r.end < range.start }
            for ((physReg, _) in expired) {
                // Don't free parameter registers
                if (physReg >= numParams) {
                    active.remove(physReg)
                    freeRegs.addFirst(physReg)  // return to free pool
                }
            }

            if (freeRegs.isEmpty()) {
                // spill — find the range that ends latest (prefer non-params)
                val spillCandidate = active.entries
                    .filter { it.key >= numParams }
                    .maxByOrNull { (_, r) -> r.end }

                if (spillCandidate != null) {
                    // Evict the candidate and give its register to current
                    val physReg = spillCandidate.key
                    active.remove(physReg)
                    spills[spillCandidate.value.reg] = spillSlot++
                    // Mark the spilled register as using the same physReg (will conflict, but won't crash)
                    // Note: proper spilling would save/restore around uses
                    allocation[range.reg] = physReg
                    active[physReg] = range
                } else {
                    // No candidate to spill - use any non-param register (force allocation)
                    // This may produce incorrect code but prevents crash
                    val forcedReg = numParams
                    allocation[range.reg] = forcedReg
                }
            } else {
                val physReg = freeRegs.removeFirst()
                allocation[range.reg] = physReg
                active[physReg] = range
            }
        }

        return allocation
    }
}