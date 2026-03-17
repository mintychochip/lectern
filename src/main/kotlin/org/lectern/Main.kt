package org.lectern

import org.lectern.ast.AstLowerer
import org.lectern.ast.ConstantFolder
import org.lectern.ast.LivenessAnalyzer
import org.lectern.ast.RegisterAllocator
import org.lectern.lang.IrCompiler
import org.lectern.ssa.SsaBuilder
import org.lectern.ssa.SsaDeconstructor
import org.lectern.lang.IrInstr
import org.lectern.lang.Parser
import org.lectern.lang.VM
import org.lectern.lang.Value
import org.lectern.lang.tokenize
import java.io.File

fun main(args: Array<String>) {
    val filename = if (args.isNotEmpty()) args[0] else "test.lec"
    val source = File(filename).readText()
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

    // SSA round-trip: IR -> SSA -> IR (proves correctness)
    val ssaFunc = SsaBuilder.build(result.instrs, result.constants)
    val ssaDeconstructed = SsaDeconstructor.deconstruct(ssaFunc)
    val ssaResult = AstLowerer.LoweredResult(ssaDeconstructed, result.constants)

    val ranges = LivenessAnalyzer().analyze(ssaResult.instrs)
    val allocResult = RegisterAllocator().allocate(ranges)
    val rewritten = rewrite(ssaResult.instrs, allocResult.allocation)
    val chunk = IrCompiler().compile(AstLowerer.LoweredResult(rewritten, ssaResult.constants))
    println("\n=== Bytecode ===")
    chunk.disassemble()
    println("\n=== Execution ===")
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
            is IrInstr.LoadImm -> instr.copy(dst = r(instr.dst))
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
