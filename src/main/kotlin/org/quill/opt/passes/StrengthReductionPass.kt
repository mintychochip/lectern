package org.quill.opt.passes

import org.quill.ast.ControlFlowGraph
import org.quill.lang.IrInstr
import org.quill.lang.TokenType
import org.quill.lang.Value
import org.quill.opt.OptPass
import org.quill.opt.OptResult

/**
 * Strength Reduction and Instruction Combination pass.
 *
 * Replaces expensive operations with cheaper equivalents.
 * This pass works on IR (non-SSA) form by tracking which registers
 * currently hold constant values.
 *
 * Optimizations:
 * - x * 2  -> x + x       (multiplication by 2 is cheaper as addition)
 * - x * 1  -> x           (identity)
 * - x / 1  -> x           (identity)
 * - x % 1  -> 0           (constant)
 * - x + 0  -> x           (identity)
 * - x - 0  -> x           (identity)
 * - x - x  -> 0           (constant)
 * - x ^ 0  -> x           (identity)
 * - x ^ x  -> 0           (constant)
 * - x & 0  -> 0           (constant)
 * - x & -1 -> x           (identity)
 * - x | 0  -> x           (identity)
 */
class StrengthReductionPass : OptPass {
    override val name = "StrengthReduction"

    override fun run(
        instrs: List<IrInstr>,
        cfg: ControlFlowGraph,
        constants: List<Value>
    ): OptResult {
        val newConstants = constants.toMutableList()
        val newInstrs = mutableListOf<IrInstr>()
        var changed = false

        // Track which registers hold constant values
        val regConstants = mutableMapOf<Int, Value>()

        fun addConstant(value: Value): Int {
            val idx = newConstants.indexOf(value)
            return if (idx >= 0) idx else {
                newConstants.add(value)
                newConstants.lastIndex
            }
        }

        for (instr in instrs) {
            val optimized = tryReduce(instr, regConstants, newConstants, ::addConstant)
            if (optimized != null && optimized !== instr) {
                newInstrs.add(optimized)
                changed = true
                // Update tracking for the optimized instruction's destination
                updateTracking(optimized, regConstants, newConstants)
            } else {
                newInstrs.add(instr)
                updateTracking(instr, regConstants, newConstants)
            }
        }

        return OptResult(newInstrs, newConstants, changed)
    }

    private fun updateTracking(
        instr: IrInstr,
        regConstants: MutableMap<Int, Value>,
        constants: List<Value>
    ) {
        when (instr) {
            is IrInstr.LoadImm -> {
                if (instr.index < constants.size) {
                    regConstants[instr.dst] = constants[instr.index]
                } else {
                    regConstants.remove(instr.dst)
                }
            }
            is IrInstr.Move -> {
                val srcConst = regConstants[instr.src]
                if (srcConst != null) {
                    regConstants[instr.dst] = srcConst
                } else {
                    regConstants.remove(instr.dst)
                }
            }
            is IrInstr.BinaryOp -> {
                val leftConst = regConstants[instr.src1]
                val rightConst = regConstants[instr.src2]
                if (leftConst != null && rightConst != null) {
                    // Both operands are constants - will be folded by ConstantFoldingPass
                    regConstants.remove(instr.dst)
                } else {
                    regConstants.remove(instr.dst)
                }
            }
            is IrInstr.UnaryOp -> {
                val srcConst = regConstants[instr.src]
                if (srcConst != null) {
                    regConstants.remove(instr.dst)
                } else {
                    regConstants.remove(instr.dst)
                }
            }
            is IrInstr.LoadGlobal -> regConstants.remove(instr.dst)
            is IrInstr.LoadFunc -> regConstants.remove(instr.dst)
            is IrInstr.LoadClass -> regConstants.remove(instr.dst)
            is IrInstr.Call -> regConstants.remove(instr.dst)
            is IrInstr.NewArray -> regConstants.remove(instr.dst)
            is IrInstr.NewInstance -> regConstants.remove(instr.dst)
            is IrInstr.GetIndex -> regConstants.remove(instr.dst)
            is IrInstr.GetField -> regConstants.remove(instr.dst)
            else -> {
                // For instructions we don't track, remove any constant status
                // No-op
            }
        }
    }

    private fun tryReduce(
        instr: IrInstr,
        regConstants: Map<Int, Value>,
        constants: List<Value>,
        addConstant: (Value) -> Int
    ): IrInstr? {
        return when (instr) {
            is IrInstr.BinaryOp -> reduceBinaryOp(instr, regConstants, constants, addConstant)
            else -> null
        }
    }

    private fun reduceBinaryOp(
        instr: IrInstr.BinaryOp,
        regConstants: Map<Int, Value>,
        constants: List<Value>,
        addConstant: (Value) -> Int
    ): IrInstr? {
        val leftConst = regConstants[instr.src1]
        val rightConst = regConstants[instr.src2]

        return when (instr.op) {
            // Multiplication optimizations
            TokenType.STAR -> {
                // x * 2 -> x + x (cheaper than multiplication)
                if (isTwo(rightConst)) {
                    return IrInstr.BinaryOp(instr.dst, TokenType.PLUS, instr.src1, instr.src1)
                }
                if (isTwo(leftConst)) {
                    return IrInstr.BinaryOp(instr.dst, TokenType.PLUS, instr.src2, instr.src2)
                }
                // x * 1 -> x
                if (isOne(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isOne(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                // x * 0 -> 0 (keep operand for potential side effects, DCE will remove)
                if (isZero(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isZero(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                null
            }

            // Division optimizations
            TokenType.SLASH -> {
                // x / 1 -> x
                if (isOne(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                null
            }

            // Remainder optimizations
            TokenType.PERCENT -> {
                // x % 1 -> 0
                if (isOne(rightConst)) {
                    val zeroIdx = addConstant(Value.Int(0))
                    return IrInstr.LoadImm(instr.dst, zeroIdx)
                }
                null
            }

            // Addition optimizations
            TokenType.PLUS -> {
                // x + 0 -> x
                if (isZero(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isZero(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                null
            }

            // Subtraction optimizations
            TokenType.MINUS -> {
                // x - 0 -> x
                if (isZero(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                // x - x -> 0
                if (instr.src1 == instr.src2) {
                    val zeroIdx = addConstant(Value.Int(0))
                    return IrInstr.LoadImm(instr.dst, zeroIdx)
                }
                null
            }

            // XOR optimizations
            TokenType.CIRCUMFLEX -> {
                // x ^ 0 -> x
                if (isZero(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isZero(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                // x ^ x -> 0
                if (instr.src1 == instr.src2) {
                    val zeroIdx = addConstant(Value.Int(0))
                    return IrInstr.LoadImm(instr.dst, zeroIdx)
                }
                null
            }

            // AND optimizations
            TokenType.AMPERSAND -> {
                // x & 0 -> 0
                if (isZero(rightConst) || isZero(leftConst)) {
                    val zeroIdx = addConstant(Value.Int(0))
                    return IrInstr.LoadImm(instr.dst, zeroIdx)
                }
                // x & -1 -> x (all bits set)
                if (isMinusOne(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isMinusOne(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                null
            }

            // OR optimizations
            TokenType.PIPE -> {
                // x | 0 -> x
                if (isZero(rightConst)) {
                    return IrInstr.Move(instr.dst, instr.src1)
                }
                if (isZero(leftConst)) {
                    return IrInstr.Move(instr.dst, instr.src2)
                }
                // x | -1 -> -1 (all ones)
                if (isMinusOne(rightConst) || isMinusOne(leftConst)) {
                    val minusOneIdx = addConstant(Value.Int(-1))
                    return IrInstr.LoadImm(instr.dst, minusOneIdx)
                }
                null
            }

            else -> null
        }
    }

    private fun isZero(v: Value?): Boolean = v is Value.Int && v.value == 0
    private fun isOne(v: Value?): Boolean = v is Value.Int && v.value == 1
    private fun isTwo(v: Value?): Boolean = v is Value.Int && v.value == 2
    private fun isMinusOne(v: Value?): Boolean = v is Value.Int && v.value == -1
}
