# quill Language Architecture

## Overview

quill is a compiled scripting language written in Kotlin that targets a register-based bytecode VM. It supports classes, first-class functions, string interpolation, default parameters, and iterator-based for loops. The compiler has a PaperMC dependency for embedding as a Minecraft scripting engine.

## Compilation Pipeline

```
Source (.quill)
    |
    v
 [Lexer]           tokenize()         Token stream
    |
    v
 [Parser]          parse()            AST (Stmt/Expr nodes)
    |
    v
 [ConstantFolder]  fold()             Optimized AST
    |
    v
 [AstLowerer]     lower()            IR instructions + constants
    |
    v
 [LivenessAnalyzer] analyze()         Live ranges per virtual register
    |
    v
 [RegisterAllocator] allocate()       Virtual -> physical register map
    |
    v
 [IrCompiler]     compile()           Chunk (packed 32-bit bytecode)
    |
    v
 [VM]             execute()           Program output
```

## Directory Structure

```
src/main/kotlin/org/quill/
  Main.kt                        Entry point, wires pipeline together
  lang/
    Token.kt                     TokenType enum + Token data class
    Lexer.kt                     Tokenizer with ASI and string interpolation
    Parser.kt                    Pratt parser, AST construction
    AST.kt                       Expr/Stmt sealed class hierarchies
    IR.kt                        Intermediate representation instructions
    OpCode.kt                    Bytecode opcode enum (54 opcodes)
    Value.kt                     Runtime value types (Int, String, Instance, etc.)
    Chunk.kt                     Bytecode container (code, constants, functions, classes)
    Register.kt                  Physical register enum (R0-R15)
  ast/
    AstLowerer.kt                AST -> IR lowering with virtual register allocation
    IrCompiler.kt                IR -> bytecode compilation (two-pass: labels then emit)
    VM.kt                        Register-based VM execution engine
    ConstantFolder.kt            Pre-lowering constant expression folding
    LivenessAnalyzer.kt          Compute live ranges for register allocation
    RegisterAllocator.kt         Linear scan allocation to 16 physical registers
    BasicBlock.kt                Basic block representation for CFG
    ControlFlowGraph.kt          CFG construction, dominators, loop detection
  ssa/
    SsaValue.kt                  Versioned SSA registers (r0.0, r0.1, ...)
    SsaInstr.kt                  SSA-form instructions with phi functions
    SsaBlock.kt                  SSA basic blocks
    SsaFunction.kt               SSA function container
    SsaBuilder.kt                IR -> SSA conversion (Cytron algorithm)
    SsaRenamer.kt                Variable renaming pass
    SsaDeconstructor.kt          SSA -> IR back-conversion
    DominanceFrontier.kt         Dominance frontier for phi placement
    SsaOptPass.kt                Base interface for SSA optimization passes
    passes/
      SsaConstantPropagationPass.kt
      SsaGlobalValueNumberingPass.kt  GVN within a single block
      SsaCrossBlockGvnPass.kt    GVN across blocks using domination
      SsaDeadCodeEliminationPass.kt
  opt/
    OptPass.kt                   Base interface for IR optimization passes
    OptimizationPipeline.kt      Pass orchestration (fixed-point iteration)
    passes/
      ConstantFoldingPass.kt     IR-level constant folding
      InductionVariablePass.kt   Range iterator -> arithmetic normalization
      StrengthReductionPass.kt   Algebraic simplification (x*2 -> x+x, etc.)
      DeadCodeEliminationPass.kt Unreachable block + unused def removal
      CopyPropagationPass.kt     Redundant MOVE elimination
      LoopInvariantCodeMotionPass.kt  Hoist invariant code out of loops
      BranchOptimizationPass.kt  Optimize conditional branches
  domain/
    Script.kt                    Empty placeholder

quill-intellij/                IntelliJ plugin (syntax highlighting, completion, formatting)
quill-vscode/                  VS Code extension
docs/                            Docusaurus documentation site
```

## Key Design Decisions

### Register-Based VM
The VM uses 16 physical registers (R0-R15) per call frame rather than a stack. Instructions are packed into 32-bit words:

```
| bits 0-7  | bits 8-11  | bits 12-15 | bits 16-19 | bits 20-31 |
| opcode    | dst (4-bit)| src1(4-bit)| src2(4-bit)| immediate  |
```

### Virtual Register Model
The AstLowerer allocates unlimited virtual registers. LivenessAnalyzer computes live ranges, then RegisterAllocator maps them to 16 physical registers via linear scan.

### Automatic Semicolon Insertion (ASI)
The lexer inserts semicolons at newlines when the previous token can end a statement (identifiers, literals, `)`, `]`, `break`, `next`, `return`).

### String Interpolation Desugaring
`"Hello ${name}!"` is desugared during parsing into `"Hello " + name + "!"` using synthetic `+` tokens.

### For-Loop Desugaring
`for x in collection { body }` desugars to:
```
let __iter = collection.iter()
while (__iter.hasNext()) {
    let x = __iter.next()
    body
}
```
The built-in `Range` class provides the `iter()` / `hasNext()` / `next()` protocol.

### Default Parameters
Default values are lowered as separate IR expression chunks stored in `functionDefaults`. At call time, if arguments are missing, the VM evaluates the default chunk in the caller's context.

### Class System
- Classes support single inheritance via `extends`
- Methods receive an implicit `self` parameter at index 0
- Field access on instances returns `BoundMethod` for methods
- Constructors are `init` methods called automatically by `NEW_INSTANCE`

### SSA Infrastructure
Full SSA construction (Cytron et al.) is implemented with phi placement, variable renaming, and deconstruction back to IR. An optimization pipeline can run IR passes, convert to SSA for SSA-specific passes, then convert back. **This infrastructure is built but not wired into the main compilation pipeline.**

## Language Features

### Working
- Primitive types: int, float, double, string, bool, null
- `let` and `const` variable declarations with compile-time reassignment enforcement
- Binary/unary operators, compound assignments (`+=`, `-=`, etc.)
- Logical operators (`and`, `or`) with short-circuit evaluation
- Unary `not` operator for boolean negation
- `**` power operator
- `++`/`--` prefix operators
- `if`/`else`, `while`, `for..in`, `break`, `next` (continue)
- Functions with default parameters and return values
- Classes with methods, inheritance, constructors
- Array literals, indexing (`[]`), field access (`.`)
- String interpolation (`"${expr}"`) with escape sequences (`\n`, `\t`, `\\`, `\"`)
- `is` type checking operator
- Range expressions (`a..b`)
- Built-in `print()` function
- Optimization pipeline (ConstantFolding, InductionVariable, Pre-SSA → SSA → GVN, constant propagation, dead code elimination, copy propagation, strength reduction, loop invariant code motion, branch optimization)

### Parsed But Not Lowered / Stub-Only

| Feature | Status | Notes |
|---------|--------|-------|
| Imports | Stub | Lowered to a marker string; no actual module loading |
| Config blocks | Stub | Lowered to a marker string; no runtime effect |
| Table declarations | Stub | Lowered to a marker string; no runtime effect |

## Known Limitations

1. **Error recovery** - Parser stops at first error rather than recovering to report multiple errors
2. **SSA not integrated as primary pipeline** - SSA is used for the round-trip optimization pass, but not as the primary IR form throughout
3. **16 register limit** - Register spilling (SPILL/UNSPILL opcodes) handles overflow for most cases but very high-pressure functions may still fail
4. **Import/Config/Table system** - These are parsed but only stub-lowered; no actual module loading or config runtime

## Tests

```
src/test/kotlin/org/quill/
  ast/ControlFlowGraphTest.kt    CFG construction tests
  ssa/SsaTest.kt                 SSA building and pass tests
  opt/OptimizationPassesTest.kt  IR optimization pass tests
```

## Tooling

- **quill-intellij/** - IntelliJ IDEA plugin with syntax highlighting, code completion, brace matching, commenter, structure view, live templates, and formatting
- **quill-vscode/** - VS Code extension for quill
- **docs/** - Docusaurus documentation website
