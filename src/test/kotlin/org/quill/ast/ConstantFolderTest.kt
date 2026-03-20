package org.quill.ast

import org.quill.lang.Expr
import org.quill.lang.Param
import org.quill.lang.Stmt
import org.quill.lang.Token
import org.quill.lang.TokenType
import org.quill.lang.Value
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConstantFolderTest {

    private val folder = ConstantFolder()

    // -------------------------------------------------------------------------
    // Token helpers
    // -------------------------------------------------------------------------

    private fun tok(type: TokenType, lexeme: String) = Token(type, lexeme, 1, 1)
    private fun identTok(name: String) = Token(TokenType.IDENTIFIER, name, 1, 1)
    private fun letTok() = tok(TokenType.KW_LET, "let")

    private fun plusTok()  = tok(TokenType.PLUS,  "+")
    private fun minusTok() = tok(TokenType.MINUS, "-")
    private fun starTok()  = tok(TokenType.STAR,  "*")
    private fun slashTok() = tok(TokenType.SLASH, "/")
    private fun eqEqTok()  = tok(TokenType.EQ_EQ, "==")
    private fun ltTok()    = tok(TokenType.LT, "<")

    // -------------------------------------------------------------------------
    // Expr helpers
    // -------------------------------------------------------------------------

    private fun intLit(n: Int)    = Expr.LiteralExpr(Value.Int(n))
    private fun floatLit(f: Float)  = Expr.LiteralExpr(Value.Float(f))
    private fun doubleLit(d: Double) = Expr.LiteralExpr(Value.Double(d))
    private fun strLit(s: String) = Expr.LiteralExpr(Value.String(s))
    private fun varExpr(name: String) = Expr.VariableExpr(identTok(name))

    private fun binary(left: Expr, op: Token, right: Expr) = Expr.BinaryExpr(left, op, right)

    // -------------------------------------------------------------------------
    // 1. Int + Int -> LiteralExpr(Int)
    // -------------------------------------------------------------------------

    @Test
    fun `fold int plus int returns Int literal`() {
        val expr = binary(intLit(2), plusTok(), intLit(3))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(5), result.literal)
    }

    // -------------------------------------------------------------------------
    // 2. Int - Int
    // -------------------------------------------------------------------------

    @Test
    fun `fold int minus int returns Int literal`() {
        val expr = binary(intLit(10), minusTok(), intLit(4))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(6), result.literal)
    }

    // -------------------------------------------------------------------------
    // 3. Int * Int
    // -------------------------------------------------------------------------

    @Test
    fun `fold int star int returns Int literal`() {
        val expr = binary(intLit(3), starTok(), intLit(7))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(21), result.literal)
    }

    // -------------------------------------------------------------------------
    // 4. Int / Int (integer division via double then toInt)
    // -------------------------------------------------------------------------

    @Test
    fun `fold int slash int returns Int literal via double truncation`() {
        val expr = binary(intLit(7), slashTok(), intLit(2))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        // 7.0 / 2.0 = 3.5 -> toInt() = 3
        assertEquals(Value.Int(3), result.literal)
    }

    @Test
    fun `fold int slash int exact division`() {
        val expr = binary(intLit(8), slashTok(), intLit(4))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(2), result.literal)
    }

    // -------------------------------------------------------------------------
    // 5. Double + Double -> LiteralExpr(Double)
    // -------------------------------------------------------------------------

    @Test
    fun `fold double plus double returns Double literal`() {
        val expr = binary(doubleLit(1.5), plusTok(), doubleLit(2.5))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(4.0), result.literal)
    }

    // -------------------------------------------------------------------------
    // 6. Float + Float -> LiteralExpr(Float)
    // -------------------------------------------------------------------------

    @Test
    fun `fold float plus float returns Float literal`() {
        val expr = binary(floatLit(1.5f), plusTok(), floatLit(2.5f))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Float(4.0f), result.literal)
    }

    // -------------------------------------------------------------------------
    // 7. Mixed Int + Double -> Double
    // -------------------------------------------------------------------------

    @Test
    fun `fold int plus double returns Double literal`() {
        val expr = binary(intLit(2), plusTok(), doubleLit(3.0))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(5.0), result.literal)
    }

    @Test
    fun `fold double plus int returns Double literal`() {
        val expr = binary(doubleLit(1.0), plusTok(), intLit(4))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(5.0), result.literal)
    }

    // -------------------------------------------------------------------------
    // 8. Mixed Int + Float -> Float
    // -------------------------------------------------------------------------

    @Test
    fun `fold int plus float returns Float literal`() {
        val expr = binary(intLit(2), plusTok(), floatLit(3.0f))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Float(5.0f), result.literal)
    }

    @Test
    fun `fold float plus int returns Float literal`() {
        val expr = binary(floatLit(1.0f), plusTok(), intLit(4))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Float(5.0f), result.literal)
    }

    // -------------------------------------------------------------------------
    // 9. Non-foldable: variable + literal -> BinaryExpr unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `fold variable plus literal returns BinaryExpr unchanged`() {
        val left = varExpr("x")
        val expr = binary(left, plusTok(), intLit(1))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
        assertIs<Expr.VariableExpr>(result.left)
        assertIs<Expr.LiteralExpr>(result.right)
        assertEquals(TokenType.PLUS, result.op.type)
    }

    @Test
    fun `fold literal plus variable returns BinaryExpr unchanged`() {
        val expr = binary(intLit(1), plusTok(), varExpr("y"))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
    }

    // -------------------------------------------------------------------------
    // 10. GroupExpr containing a literal -> unwraps to literal
    // -------------------------------------------------------------------------

    @Test
    fun `fold group of int literal unwraps to literal`() {
        val expr = Expr.GroupExpr(intLit(5))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(5), result.literal)
    }

    @Test
    fun `fold group of double literal unwraps to literal`() {
        val expr = Expr.GroupExpr(doubleLit(3.14))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(3.14), result.literal)
    }

    // -------------------------------------------------------------------------
    // 11. Non-foldable group: (x) -> GroupExpr unchanged
    // -------------------------------------------------------------------------

    @Test
    fun `fold group of variable stays as GroupExpr`() {
        val expr = Expr.GroupExpr(varExpr("x"))
        val result = folder.fold(expr)
        assertIs<Expr.GroupExpr>(result)
        assertIs<Expr.VariableExpr>(result.expr)
    }

    // -------------------------------------------------------------------------
    // 12. Nested: (2 + 3) * 4 -> LiteralExpr(Int(20))
    // -------------------------------------------------------------------------

    @Test
    fun `fold nested group binary reduces to single int literal`() {
        // (2 + 3) * 4
        val inner = Expr.GroupExpr(binary(intLit(2), plusTok(), intLit(3)))
        val expr = binary(inner, starTok(), intLit(4))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(20), result.literal)
    }

    @Test
    fun `fold deeply nested int arithmetic reduces fully`() {
        // (1 + 2) + (3 + 4) -> 10
        val left  = Expr.GroupExpr(binary(intLit(1), plusTok(), intLit(2)))
        val right = Expr.GroupExpr(binary(intLit(3), plusTok(), intLit(4)))
        val expr  = binary(left, plusTok(), right)
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(10), result.literal)
    }

    // -------------------------------------------------------------------------
    // 13. foldStmt on VarStmt with foldable init
    // -------------------------------------------------------------------------

    @Test
    fun `foldStmt VarStmt folds foldable initializer`() {
        val init = binary(intLit(3), starTok(), intLit(4))
        val stmt = Stmt.VarStmt(letTok(), identTok("x"), init)
        val result = folder.foldStmt(stmt) as Stmt.VarStmt
        val folded = result.value
        assertIs<Expr.LiteralExpr>(folded)
        assertEquals(Value.Int(12), folded.literal)
    }

    @Test
    fun `foldStmt VarStmt with null init stays null`() {
        val stmt = Stmt.VarStmt(letTok(), identTok("x"), null)
        val result = folder.foldStmt(stmt) as Stmt.VarStmt
        assertEquals(null, result.value)
    }

    @Test
    fun `foldStmt VarStmt with non-foldable init leaves BinaryExpr`() {
        val init = binary(varExpr("a"), plusTok(), intLit(1))
        val stmt = Stmt.VarStmt(letTok(), identTok("x"), init)
        val result = folder.foldStmt(stmt) as Stmt.VarStmt
        assertIs<Expr.BinaryExpr>(result.value)
    }

    // -------------------------------------------------------------------------
    // 14. foldStmt on ExprStmt
    // -------------------------------------------------------------------------

    @Test
    fun `foldStmt ExprStmt folds inner expression`() {
        val expr = binary(intLit(6), slashTok(), intLit(2))
        val stmt = Stmt.ExprStmt(expr)
        val result = folder.foldStmt(stmt) as Stmt.ExprStmt
        assertIs<Expr.LiteralExpr>(result.expr)
        assertEquals(Value.Int(3), (result.expr as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt ExprStmt with variable expression leaves it unchanged`() {
        val stmt = Stmt.ExprStmt(varExpr("z"))
        val result = folder.foldStmt(stmt) as Stmt.ExprStmt
        assertIs<Expr.VariableExpr>(result.expr)
    }

    // -------------------------------------------------------------------------
    // 15. foldStmt on ReturnStmt
    // -------------------------------------------------------------------------

    @Test
    fun `foldStmt ReturnStmt folds return value`() {
        val expr = binary(intLit(2), plusTok(), intLit(8))
        val stmt = Stmt.ReturnStmt(expr)
        val result = folder.foldStmt(stmt) as Stmt.ReturnStmt
        assertIs<Expr.LiteralExpr>(result.value)
        assertEquals(Value.Int(10), (result.value as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt ReturnStmt with null value stays null`() {
        val stmt = Stmt.ReturnStmt(null)
        val result = folder.foldStmt(stmt) as Stmt.ReturnStmt
        assertEquals(null, result.value)
    }

    // -------------------------------------------------------------------------
    // 16. foldStmt on IfStmt with foldable condition
    // -------------------------------------------------------------------------

    @Test
    fun `foldStmt IfStmt folds condition expression`() {
        // if (1 + 1) { return 0 }
        val condition = binary(intLit(1), plusTok(), intLit(1))
        val body = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(intLit(0))))
        val stmt = Stmt.IfStmt(condition, body, null)
        val result = folder.foldStmt(stmt) as Stmt.IfStmt
        assertIs<Expr.LiteralExpr>(result.condition)
        assertEquals(Value.Int(2), (result.condition as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt IfStmt folds expressions inside then body`() {
        val condition = varExpr("flag")
        val bodyExpr = binary(intLit(3), starTok(), intLit(3))
        val body = Stmt.BlockStmt(listOf(Stmt.ExprStmt(bodyExpr)))
        val stmt = Stmt.IfStmt(condition, body, null)
        val result = folder.foldStmt(stmt) as Stmt.IfStmt
        val innerStmt = result.then.stmts[0] as Stmt.ExprStmt
        assertIs<Expr.LiteralExpr>(innerStmt.expr)
        assertEquals(Value.Int(9), (innerStmt.expr as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt IfStmt with else branch folds both branches`() {
        val condition = varExpr("flag")
        val thenBody = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(binary(intLit(1), plusTok(), intLit(1)))))
        val elseBody = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(binary(intLit(2), starTok(), intLit(3)))))
        val stmt = Stmt.IfStmt(condition, thenBody, Stmt.ElseBranch.Else(elseBody))
        val result = folder.foldStmt(stmt) as Stmt.IfStmt

        val thenReturn = result.then.stmts[0] as Stmt.ReturnStmt
        assertIs<Expr.LiteralExpr>(thenReturn.value)
        assertEquals(Value.Int(2), (thenReturn.value as Expr.LiteralExpr).literal)

        val elseClause = result.elseBranch as Stmt.ElseBranch.Else
        val elseReturn = elseClause.block.stmts[0] as Stmt.ReturnStmt
        assertIs<Expr.LiteralExpr>(elseReturn.value)
        assertEquals(Value.Int(6), (elseReturn.value as Expr.LiteralExpr).literal)
    }

    // -------------------------------------------------------------------------
    // 17. String + String -> NOT folded (not supported)
    // -------------------------------------------------------------------------

    @Test
    fun `fold string plus string returns BinaryExpr unchanged`() {
        val expr = binary(strLit("hello"), plusTok(), strLit(" world"))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
        // Both operands are still literals, but the op result is not a numeric fold
        assertIs<Expr.LiteralExpr>(result.left)
        assertIs<Expr.LiteralExpr>(result.right)
    }

    @Test
    fun `fold string minus string returns BinaryExpr unchanged`() {
        val expr = binary(strLit("a"), minusTok(), strLit("b"))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
    }

    // -------------------------------------------------------------------------
    // 18. Comparison ops (==, <) -> NOT folded (not supported)
    // -------------------------------------------------------------------------

    @Test
    fun `fold int equals int returns BinaryExpr unchanged`() {
        val expr = binary(intLit(3), eqEqTok(), intLit(3))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
    }

    @Test
    fun `fold int less than int returns BinaryExpr unchanged`() {
        val expr = binary(intLit(1), ltTok(), intLit(5))
        val result = folder.fold(expr)
        assertIs<Expr.BinaryExpr>(result)
    }

    // -------------------------------------------------------------------------
    // Additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun `fold plain literal is identity`() {
        val lit = intLit(42)
        val result = folder.fold(lit)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(42), result.literal)
    }

    @Test
    fun `fold plain variable is identity`() {
        val v = varExpr("myVar")
        val result = folder.fold(v)
        assertIs<Expr.VariableExpr>(result)
        assertEquals("myVar", result.name.lexeme)
    }

    @Test
    fun `fold int minus int with negative result`() {
        val expr = binary(intLit(3), minusTok(), intLit(10))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(-7), result.literal)
    }

    @Test
    fun `foldStmt WhileStmt folds condition and body`() {
        val condition = binary(intLit(1), plusTok(), intLit(0))
        val bodyExpr = binary(intLit(4), starTok(), intLit(5))
        val body = Stmt.BlockStmt(listOf(Stmt.ExprStmt(bodyExpr)))
        val stmt = Stmt.WhileStmt(condition, body)
        val result = folder.foldStmt(stmt) as Stmt.WhileStmt

        assertIs<Expr.LiteralExpr>(result.condition)
        assertEquals(Value.Int(1), (result.condition as Expr.LiteralExpr).literal)

        val innerStmt = result.body.stmts[0] as Stmt.ExprStmt
        assertIs<Expr.LiteralExpr>(innerStmt.expr)
        assertEquals(Value.Int(20), (innerStmt.expr as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt BlockStmt folds all contained statements`() {
        val s1 = Stmt.ExprStmt(binary(intLit(1), plusTok(), intLit(2)))
        val s2 = Stmt.ReturnStmt(binary(intLit(3), starTok(), intLit(3)))
        val block = Stmt.BlockStmt(listOf(s1, s2))
        val result = folder.foldStmt(block) as Stmt.BlockStmt

        val r1 = result.stmts[0] as Stmt.ExprStmt
        assertIs<Expr.LiteralExpr>(r1.expr)
        assertEquals(Value.Int(3), (r1.expr as Expr.LiteralExpr).literal)

        val r2 = result.stmts[1] as Stmt.ReturnStmt
        assertIs<Expr.LiteralExpr>(r2.value)
        assertEquals(Value.Int(9), (r2.value as Expr.LiteralExpr).literal)
    }

    @Test
    fun `foldStmt FuncStmt folds body`() {
        val bodyExpr = binary(intLit(6), slashTok(), intLit(3))
        val body = Stmt.BlockStmt(listOf(Stmt.ReturnStmt(bodyExpr)))
        val stmt = Stmt.FuncStmt(
            name = identTok("myFn"),
            params = emptyList(),
            returnType = null,
            body = body
        )
        val result = folder.foldStmt(stmt) as Stmt.FuncStmt
        val returnStmt = result.body.stmts[0] as Stmt.ReturnStmt
        assertIs<Expr.LiteralExpr>(returnStmt.value)
        assertEquals(Value.Int(2), (returnStmt.value as Expr.LiteralExpr).literal)
    }

    @Test
    fun `fold group wrapping foldable binary unwraps to folded literal`() {
        // (10 - 5) -> LiteralExpr(Int(5))
        val inner = binary(intLit(10), minusTok(), intLit(5))
        val expr = Expr.GroupExpr(inner)
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Int(5), result.literal)
    }

    @Test
    fun `fold double minus double returns Double literal`() {
        val expr = binary(doubleLit(5.0), minusTok(), doubleLit(1.5))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(3.5), result.literal)
    }

    @Test
    fun `fold float star float returns Float literal`() {
        val expr = binary(floatLit(2.0f), starTok(), floatLit(3.0f))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Float(6.0f), result.literal)
    }

    @Test
    fun `fold double slash double returns Double literal`() {
        val expr = binary(doubleLit(9.0), slashTok(), doubleLit(4.0))
        val result = folder.fold(expr)
        assertIs<Expr.LiteralExpr>(result)
        assertEquals(Value.Double(2.25), result.literal)
    }

    @Test
    fun `foldStmt on unhandled stmt type returns it unchanged`() {
        // BreakStmt is not matched by any branch and falls through to else
        val stmt: Stmt = Stmt.BreakStmt
        val result = folder.foldStmt(stmt)
        assertTrue(result === Stmt.BreakStmt)
    }
}
