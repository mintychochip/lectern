package org.aincraft

import org.aincraft.ast.AstLowerer
import org.aincraft.ast.ConstantFolder
import org.aincraft.ast.LivenessAnalyzer
import org.aincraft.ast.RegisterAllocator
import org.aincraft.lang.IrCompiler
import org.aincraft.lang.IrInstr
import org.aincraft.lang.Parser
import org.aincraft.lang.VM
import org.aincraft.lang.Value
import org.aincraft.lang.tokenize
import java.io.File

fun main(args: Array<String>) {
    val filename = if (args.isNotEmpty()) args[0] else "test.ain"
    val source = File(filename).readText()
    TODO("default parameters")
    println("=== Tokenizing ===")
    val tokens = tokenize(source)
    print(tokens)
    println("Tokens: ${tokens.size}")

    println("\n=== Parsing ===")
    val parser = Parser(tokens)
    val statements = parser.parse()
    println("Statements: ${statements.size}")
    for (stmt in statements) {
        println("  $stmt")
    }
    val folder = ConstantFolder()
    val folded = statements.map { folder.foldStmt(it) }
    println("Folded Statements: ${folded.size}")
    for (stmt in folded) {
        println("  $stmt")
    }
    val result = AstLowerer().lower(folded)
    val ranges = LivenessAnalyzer().analyze(result.instrs)
    val allocation = RegisterAllocator().allocate(ranges)
    val rewritten = rewrite(result.instrs, allocation)
    val chunk = IrCompiler().compile(AstLowerer.LoweredResult(rewritten, result.constants))
    val vm = VM()
    vm.globals["b"] = Value.Boolean(true);
    vm.globals["print"] = Value.NativeFunction { args ->
        println(args.joinToString(" ") { it.toString() })
        Value.Null
    }
    vm.execute(chunk)
}

fun rewrite(instrs: List<IrInstr>, allocation: Map<Int, Int>): List<IrInstr> {
    fun r(reg: Int) = allocation[reg] ?: error("v$reg not allocated — needs spill handling")
    return instrs.map { instr ->
        when (instr) {
            is IrInstr.LoadConst -> instr.copy(dst = r(instr.dst))
            is IrInstr.LoadNull -> instr.copy(dst = r(instr.dst))
            is IrInstr.LoadTrue -> instr.copy(dst = r(instr.dst))
            is IrInstr.LoadFalse -> instr.copy(dst = r(instr.dst))
            is IrInstr.LoadGlobal -> instr.copy(dst = r(instr.dst))
            is IrInstr.StoreGlobal -> instr.copy(src = r(instr.src))
            is IrInstr.Move -> instr.copy(dst = r(instr.dst), src = r(instr.src))
            is IrInstr.BinaryOp -> instr.copy(dst = r(instr.dst), src1 = r(instr.src1), src2 = r(instr.src2))
            is IrInstr.UnaryOp -> instr.copy(dst = r(instr.dst), src = r(instr.src))
            is IrInstr.Call -> instr.copy(dst = r(instr.dst), func = r(instr.func), args = instr.args.map { r(it) })
            is IrInstr.NewArray -> instr.copy(dst = r(instr.dst), elements = instr.elements.map { r(it) })
            is IrInstr.GetIndex -> instr.copy(dst = r(instr.dst), obj = r(instr.obj), index = r(instr.index))
            is IrInstr.SetIndex -> instr.copy(obj = r(instr.obj), index = r(instr.index), src = r(instr.src))
            is IrInstr.GetField -> instr.copy(dst = r(instr.dst), obj = r(instr.obj))
            is IrInstr.SetField -> instr.copy(obj = r(instr.obj), src = r(instr.src))
            is IrInstr.NewInstance -> instr.copy(dst = r(instr.dst), classReg = r(instr.classReg), args = instr.args.map { r(it) })
            is IrInstr.IsType -> instr.copy(dst = r(instr.dst), src = r(instr.src))
            is IrInstr.LoadClass -> instr.copy(dst = r(instr.dst))
            is IrInstr.Return -> instr.copy(src = r(instr.src))
            is IrInstr.JumpIfFalse -> instr.copy(src = r(instr.src))
            is IrInstr.LoadFunc -> instr.copy(dst = r(instr.dst))
            else -> instr
        }
    }
}
