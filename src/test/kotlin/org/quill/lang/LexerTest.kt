package org.quill.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LexerTest {

    private fun tokenize(source: String): List<Token> = org.quill.lang.tokenize(source)

    private fun types(source: String): List<TokenType> = tokenize(source).map { it.type }

    // -------------------------------------------------------------------------
    // Helper: strip the trailing EOF for brevity in many assertions
    // -------------------------------------------------------------------------
    private fun typesNoEof(source: String): List<TokenType> =
        types(source).dropLast(1)

    // =========================================================================
    // 1. Empty source
    // =========================================================================

    @Test
    fun `empty source produces only EOF`() {
        val tokens = tokenize("")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }

    @Test
    fun `whitespace only produces only EOF`() {
        val tokens = tokenize("   \t   ")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }

    // =========================================================================
    // 2. Integer literals
    // =========================================================================

    @Test
    fun `integer literal produces KW_INT`() {
        val tokens = tokenize("42")
        assertEquals(TokenType.KW_INT, tokens[0].type)
        assertEquals("42", tokens[0].lexeme)
    }

    @Test
    fun `zero is KW_INT`() {
        val tokens = tokenize("0")
        assertEquals(TokenType.KW_INT, tokens[0].type)
        assertEquals("0", tokens[0].lexeme)
    }

    @Test
    fun `large integer literal`() {
        val tokens = tokenize("1000000")
        assertEquals(TokenType.KW_INT, tokens[0].type)
        assertEquals("1000000", tokens[0].lexeme)
    }

    // =========================================================================
    // 3. Double/Float literals
    // =========================================================================

    @Test
    fun `double literal produces KW_DOUBLE`() {
        val tokens = tokenize("3.14")
        assertEquals(TokenType.KW_DOUBLE, tokens[0].type)
        assertEquals("3.14", tokens[0].lexeme)
    }

    @Test
    fun `double literal zero point zero`() {
        val tokens = tokenize("0.0")
        assertEquals(TokenType.KW_DOUBLE, tokens[0].type)
        assertEquals("0.0", tokens[0].lexeme)
    }

    @Test
    fun `double literal with many decimal places`() {
        val tokens = tokenize("2.71828")
        assertEquals(TokenType.KW_DOUBLE, tokens[0].type)
        assertEquals("2.71828", tokens[0].lexeme)
    }

    // =========================================================================
    // 4. String literals
    // =========================================================================

    @Test
    fun `simple string literal includes surrounding quotes`() {
        val tokens = tokenize("\"hello\"")
        assertEquals(TokenType.KW_STRING, tokens[0].type)
        assertEquals("\"hello\"", tokens[0].lexeme)
    }

    @Test
    fun `empty string literal`() {
        val tokens = tokenize("\"\"")
        assertEquals(TokenType.KW_STRING, tokens[0].type)
        assertEquals("\"\"", tokens[0].lexeme)
    }

    @Test
    fun `string with spaces`() {
        val tokens = tokenize("\"hello world\"")
        assertEquals(TokenType.KW_STRING, tokens[0].type)
        assertEquals("\"hello world\"", tokens[0].lexeme)
    }

    // =========================================================================
    // 5. Keywords
    // =========================================================================

    @Test
    fun `keyword let`() {
        assertEquals(TokenType.KW_LET, tokenize("let")[0].type)
    }

    @Test
    fun `keyword const`() {
        assertEquals(TokenType.KW_CONST, tokenize("const")[0].type)
    }

    @Test
    fun `keyword if`() {
        assertEquals(TokenType.KW_IF, tokenize("if")[0].type)
    }

    @Test
    fun `keyword else`() {
        assertEquals(TokenType.KW_ELSE, tokenize("else")[0].type)
    }

    @Test
    fun `keyword while`() {
        assertEquals(TokenType.KW_WHILE, tokenize("while")[0].type)
    }

    @Test
    fun `keyword for`() {
        assertEquals(TokenType.KW_FOR, tokenize("for")[0].type)
    }

    @Test
    fun `keyword in`() {
        assertEquals(TokenType.KW_IN, tokenize("in")[0].type)
    }

    @Test
    fun `keyword fn`() {
        assertEquals(TokenType.KW_FN, tokenize("fn")[0].type)
    }

    @Test
    fun `keyword return`() {
        assertEquals(TokenType.KW_RETURN, tokenize("return")[0].type)
    }

    @Test
    fun `keyword and`() {
        assertEquals(TokenType.KW_AND, tokenize("and")[0].type)
    }

    @Test
    fun `keyword or`() {
        assertEquals(TokenType.KW_OR, tokenize("or")[0].type)
    }

    @Test
    fun `keyword not`() {
        assertEquals(TokenType.KW_NOT, tokenize("not")[0].type)
    }

    @Test
    fun `keyword null`() {
        assertEquals(TokenType.KW_NULL, tokenize("null")[0].type)
    }

    @Test
    fun `keyword break`() {
        assertEquals(TokenType.KW_BREAK, tokenize("break")[0].type)
    }

    @Test
    fun `keyword next`() {
        assertEquals(TokenType.KW_NEXT, tokenize("next")[0].type)
    }

    @Test
    fun `keyword enum`() {
        assertEquals(TokenType.KW_ENUM, tokenize("enum")[0].type)
    }

    @Test
    fun `keyword class`() {
        assertEquals(TokenType.KW_CLASS, tokenize("class")[0].type)
    }

    @Test
    fun `keyword extends is not in lexer keyword map`() {
        // Known limitation: 'extends' is missing from the Lexer keyword map
        assertEquals(TokenType.IDENTIFIER, tokenize("extends")[0].type)
    }

    @Test
    fun `keyword import`() {
        assertEquals(TokenType.KW_IMPORT, tokenize("import")[0].type)
    }

    @Test
    fun `keyword from`() {
        assertEquals(TokenType.KW_FROM, tokenize("from")[0].type)
    }

    @Test
    fun `keyword is`() {
        assertEquals(TokenType.KW_IS, tokenize("is")[0].type)
    }

    @Test
    fun `keyword true`() {
        assertEquals(TokenType.KW_TRUE, tokenize("true")[0].type)
    }

    @Test
    fun `keyword false`() {
        assertEquals(TokenType.KW_FALSE, tokenize("false")[0].type)
    }

    @Test
    fun `keyword bool type`() {
        assertEquals(TokenType.KW_BOOL, tokenize("bool")[0].type)
    }

    @Test
    fun `keyword int type`() {
        // The KW_INT token type is reused for integer type annotation
        // We verify that "int" as a keyword does not produce an IDENTIFIER
        val t = tokenize("int")[0]
        assertTrue(t.type != TokenType.IDENTIFIER, "Expected 'int' to be a keyword, got IDENTIFIER")
    }

    @Test
    fun `keyword float type`() {
        val t = tokenize("float")[0]
        assertEquals(TokenType.KW_FLOAT, t.type)
    }

    @Test
    fun `keyword double type`() {
        // "double" as a type keyword (separate from a numeric literal)
        val t = tokenize("double")[0]
        assertEquals(TokenType.KW_DOUBLE, t.type)
    }

    @Test
    fun `keyword string type`() {
        val t = tokenize("string")[0]
        assertEquals(TokenType.KW_STRING, t.type)
    }

    // =========================================================================
    // 6. Identifiers
    // =========================================================================

    @Test
    fun `simple identifier`() {
        val tokens = tokenize("myVar")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("myVar", tokens[0].lexeme)
    }

    @Test
    fun `identifier with underscore`() {
        val tokens = tokenize("my_var")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("my_var", tokens[0].lexeme)
    }

    @Test
    fun `identifier starting with underscore`() {
        val tokens = tokenize("_private")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("_private", tokens[0].lexeme)
    }

    @Test
    fun `identifier with digits`() {
        val tokens = tokenize("var1")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("var1", tokens[0].lexeme)
    }

    @Test
    fun `identifier that starts with keyword prefix`() {
        // "letter" starts with "let" but should be an identifier
        val tokens = tokenize("letter")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
        assertEquals("letter", tokens[0].lexeme)
    }

    @Test
    fun `identifier iffy not keyword if`() {
        val tokens = tokenize("iffy")
        assertEquals(TokenType.IDENTIFIER, tokens[0].type)
    }

    // =========================================================================
    // 7. Operators
    // =========================================================================

    @Test
    fun `plus operator`() {
        assertEquals(TokenType.PLUS, typesNoEof("+")[0])
    }

    @Test
    fun `minus operator`() {
        assertEquals(TokenType.MINUS, typesNoEof("-")[0])
    }

    @Test
    fun `star operator`() {
        assertEquals(TokenType.STAR, typesNoEof("*")[0])
    }

    @Test
    fun `slash operator`() {
        assertEquals(TokenType.SLASH, typesNoEof("/")[0])
    }

    @Test
    fun `percent operator`() {
        assertEquals(TokenType.PERCENT, typesNoEof("%")[0])
    }

    @Test
    fun `increment operator`() {
        assertEquals(TokenType.INCREMENT, typesNoEof("++")[0])
    }

    @Test
    fun `decrement operator`() {
        assertEquals(TokenType.DECREMENT, typesNoEof("--")[0])
    }

    @Test
    fun `power operator`() {
        assertEquals(TokenType.POW, typesNoEof("**")[0])
    }

    @Test
    fun `equals equals operator`() {
        assertEquals(TokenType.EQ_EQ, typesNoEof("==")[0])
    }

    @Test
    fun `bang equals operator`() {
        assertEquals(TokenType.BANG_EQ, typesNoEof("!=")[0])
    }

    @Test
    fun `less than operator`() {
        assertEquals(TokenType.LT, typesNoEof("<")[0])
    }

    @Test
    fun `greater than operator`() {
        assertEquals(TokenType.GT, typesNoEof(">")[0])
    }

    @Test
    fun `less than or equal operator`() {
        assertEquals(TokenType.LTE, typesNoEof("<=")[0])
    }

    @Test
    fun `greater than or equal operator`() {
        assertEquals(TokenType.GTE, typesNoEof(">=")[0])
    }

    @Test
    fun `assign operator`() {
        assertEquals(TokenType.ASSIGN, typesNoEof("=")[0])
    }

    @Test
    fun `arrow operator`() {
        assertEquals(TokenType.ARROW, typesNoEof("->")[0])
    }

    @Test
    fun `add equals operator`() {
        assertEquals(TokenType.ADD_EQUALS, typesNoEof("+=")[0])
    }

    @Test
    fun `sub equals operator`() {
        assertEquals(TokenType.SUB_EQUALS, typesNoEof("-=")[0])
    }

    @Test
    fun `mul equals operator`() {
        assertEquals(TokenType.MUL_EQUALS, typesNoEof("*=")[0])
    }

    @Test
    fun `div equals operator`() {
        assertEquals(TokenType.DIV_EQUALS, typesNoEof("/=")[0])
    }

    @Test
    fun `mod equals operator`() {
        assertEquals(TokenType.MOD_EQUALS, typesNoEof("%=")[0])
    }

    @Test
    fun `star vs power disambiguation`() {
        val types = typesNoEof("* **")
        assertEquals(TokenType.STAR, types[0])
        assertEquals(TokenType.POW, types[1])
    }

    @Test
    fun `plus vs increment disambiguation`() {
        val types = typesNoEof("+ ++")
        assertEquals(TokenType.PLUS, types[0])
        assertEquals(TokenType.INCREMENT, types[1])
    }

    @Test
    fun `minus vs decrement vs arrow disambiguation`() {
        val types = typesNoEof("- -- ->")
        assertEquals(TokenType.MINUS, types[0])
        assertEquals(TokenType.DECREMENT, types[1])
        assertEquals(TokenType.ARROW, types[2])
    }

    @Test
    fun `assign vs eq_eq disambiguation`() {
        val types = typesNoEof("= ==")
        assertEquals(TokenType.ASSIGN, types[0])
        assertEquals(TokenType.EQ_EQ, types[1])
    }

    @Test
    fun `bang vs bang_eq disambiguation`() {
        val types = typesNoEof("! !=")
        assertEquals(TokenType.BANG, types[0])
        assertEquals(TokenType.BANG_EQ, types[1])
    }

    @Test
    fun `lt vs lte disambiguation`() {
        val types = typesNoEof("< <=")
        assertEquals(TokenType.LT, types[0])
        assertEquals(TokenType.LTE, types[1])
    }

    @Test
    fun `gt vs gte disambiguation`() {
        val types = typesNoEof("> >=")
        assertEquals(TokenType.GT, types[0])
        assertEquals(TokenType.GTE, types[1])
    }

    // =========================================================================
    // 8. Punctuation
    // =========================================================================

    @Test
    fun `left brace`() {
        assertEquals(TokenType.L_BRACE, typesNoEof("{")[0])
    }

    @Test
    fun `right brace`() {
        assertEquals(TokenType.R_BRACE, typesNoEof("}")[0])
    }

    @Test
    fun `left paren`() {
        assertEquals(TokenType.L_PAREN, typesNoEof("(")[0])
    }

    @Test
    fun `right paren`() {
        assertEquals(TokenType.R_PAREN, typesNoEof(")")[0])
    }

    @Test
    fun `left square`() {
        assertEquals(TokenType.L_SQUARE, typesNoEof("[")[0])
    }

    @Test
    fun `right square`() {
        assertEquals(TokenType.R_SQUARE, typesNoEof("]")[0])
    }

    @Test
    fun `bang punctuation`() {
        assertEquals(TokenType.BANG, typesNoEof("!")[0])
    }

    @Test
    fun `comma`() {
        assertEquals(TokenType.COMMA, typesNoEof(",")[0])
    }

    @Test
    fun `dot`() {
        assertEquals(TokenType.DOT, typesNoEof(".")[0])
    }

    @Test
    fun `dot dot range`() {
        assertEquals(TokenType.DOT_DOT, typesNoEof("..")[0])
    }

    @Test
    fun `dot vs dot dot disambiguation`() {
        val types = typesNoEof(". ..")
        assertEquals(TokenType.DOT, types[0])
        assertEquals(TokenType.DOT_DOT, types[1])
    }

    @Test
    fun `colon`() {
        assertEquals(TokenType.COLON, typesNoEof(":")[0])
    }

    @Test
    fun `semicolon explicit`() {
        assertEquals(TokenType.SEMICOLON, typesNoEof(";")[0])
    }

    // =========================================================================
    // 9. Automatic semicolon insertion (ASI)
    // =========================================================================

    @Test
    fun `ASI after identifier on newline`() {
        val types = typesNoEof("foo\nbar")
        // foo ; bar
        assertEquals(TokenType.IDENTIFIER, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
    }

    @Test
    fun `ASI after KW_INT literal on newline`() {
        val types = typesNoEof("42\n1")
        assertEquals(TokenType.KW_INT, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_INT, types[2])
    }

    @Test
    fun `ASI after KW_DOUBLE literal on newline`() {
        val types = typesNoEof("3.14\n2.0")
        assertEquals(TokenType.KW_DOUBLE, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_DOUBLE, types[2])
    }

    @Test
    fun `ASI after KW_STRING literal on newline`() {
        val types = typesNoEof("\"hi\"\n\"there\"")
        assertEquals(TokenType.KW_STRING, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_STRING, types[2])
    }

    @Test
    fun `ASI after true on newline`() {
        val types = typesNoEof("true\nfalse")
        assertEquals(TokenType.KW_TRUE, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_FALSE, types[2])
    }

    @Test
    fun `ASI after false on newline`() {
        val types = typesNoEof("false\ntrue")
        assertEquals(TokenType.KW_FALSE, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_TRUE, types[2])
    }

    @Test
    fun `ASI after null on newline`() {
        val types = typesNoEof("null\nnull")
        assertEquals(TokenType.KW_NULL, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.KW_NULL, types[2])
    }

    @Test
    fun `ASI after R_PAREN on newline`() {
        val types = typesNoEof(")\nfoo")
        assertEquals(TokenType.R_PAREN, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
    }

    @Test
    fun `ASI after R_SQUARE on newline`() {
        val types = typesNoEof("]\nfoo")
        assertEquals(TokenType.R_SQUARE, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
    }

    @Test
    fun `ASI after break on newline`() {
        val types = typesNoEof("break\nfoo")
        assertEquals(TokenType.KW_BREAK, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
    }

    @Test
    fun `ASI after next on newline`() {
        val types = typesNoEof("next\nfoo")
        assertEquals(TokenType.KW_NEXT, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
    }

    @Test
    fun `no ASI after R_BRACE on newline`() {
        val types = typesNoEof("}\nfoo")
        // R_BRACE should NOT trigger ASI
        assertTrue(
            types[1] != TokenType.SEMICOLON,
            "Expected no ASI after R_BRACE, but got SEMICOLON at index 1: $types"
        )
    }

    @Test
    fun `no ASI after operator on newline`() {
        val types = typesNoEof("+\nfoo")
        // PLUS should NOT trigger ASI
        assertEquals(TokenType.PLUS, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertTrue(types.none { it == TokenType.SEMICOLON }, "Expected no ASI after PLUS, but found SEMICOLON in: $types")
    }

    @Test
    fun `no ASI after L_PAREN on newline`() {
        val types = typesNoEof("(\nfoo")
        assertEquals(TokenType.L_PAREN, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertTrue(types.none { it == TokenType.SEMICOLON }, "Expected no ASI after L_PAREN")
    }

    @Test
    fun `no ASI after L_SQUARE on newline`() {
        val types = typesNoEof("[\nfoo")
        assertEquals(TokenType.L_SQUARE, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertTrue(types.none { it == TokenType.SEMICOLON }, "Expected no ASI after L_SQUARE")
    }

    @Test
    fun `no ASI after L_BRACE on newline`() {
        val types = typesNoEof("{\nfoo")
        assertEquals(TokenType.L_BRACE, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertTrue(types.none { it == TokenType.SEMICOLON }, "Expected no ASI after L_BRACE")
    }

    @Test
    fun `no ASI on multiple blank lines`() {
        // identifier followed by several newlines then another identifier
        // should produce exactly one semicolon, not multiple
        val types = typesNoEof("foo\n\n\nbar")
        assertEquals(TokenType.IDENTIFIER, types[0])
        assertEquals(TokenType.SEMICOLON, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
        assertEquals(3, types.size)
    }

    @Test
    fun `ASI not inserted at EOF for empty source`() {
        val tokens = tokenize("")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }

    @Test
    fun `ASI at end of source after identifier`() {
        // Many languages insert ASI at EOF; verify behavior
        // If the last token is identifier, there may or may not be ASI — just confirm EOF is last
        val tokens = tokenize("foo")
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    // =========================================================================
    // 10. Comments
    // =========================================================================

    @Test
    fun `line comment is skipped`() {
        val types = typesNoEof("// this is a comment")
        assertTrue(types.isEmpty(), "Expected no tokens from a comment line, got: $types")
    }

    @Test
    fun `line comment after token`() {
        val types = typesNoEof("foo // comment")
        assertEquals(TokenType.IDENTIFIER, types[0])
        assertEquals(1, types.size)
    }

    @Test
    fun `code after comment on next line is lexed`() {
        val types = typesNoEof("// comment\nfoo")
        assertEquals(TokenType.IDENTIFIER, types.last())
    }

    @Test
    fun `multiple comment lines`() {
        val types = typesNoEof("// line 1\n// line 2\nfoo")
        // Only identifier should be present (possibly with SEMICOLON, but no comment tokens)
        assertTrue(types.none { it == TokenType.SLASH }, "Comment slashes should not appear as tokens")
        assertTrue(types.contains(TokenType.IDENTIFIER))
    }

    // =========================================================================
    // 11. String interpolation — single interpolation
    // =========================================================================

    @Test
    fun `string interpolation produces correct token sequence`() {
        // "hello ${name}!" -> KW_STRING("hello "), INTERPOLATION_START, IDENTIFIER(name), INTERPOLATION_END, KW_STRING("!")
        // The exact split depends on the lexer, but we verify the critical tokens appear in order
        val types = typesNoEof("\"hello \${name}!\"")
        val startIdx = types.indexOf(TokenType.INTERPOLATION_START)
        val endIdx = types.indexOf(TokenType.INTERPOLATION_END)

        assertTrue(startIdx >= 0, "Expected INTERPOLATION_START in: $types")
        assertTrue(endIdx >= 0, "Expected INTERPOLATION_END in: $types")
        assertTrue(startIdx < endIdx, "INTERPOLATION_START must come before INTERPOLATION_END")

        // The token immediately inside should be the identifier
        val identIdx = types.indexOf(TokenType.IDENTIFIER)
        assertTrue(identIdx in (startIdx + 1) until endIdx, "Expected IDENTIFIER between interpolation markers")
    }

    @Test
    fun `string before interpolation is KW_STRING`() {
        val tokens = tokenize("\"hello \${name}!\"")
        assertEquals(TokenType.KW_STRING, tokens[0].type)
    }

    @Test
    fun `interpolation identifier lexeme is correct`() {
        val tokens = tokenize("\"hi \${world}!\"")
        val ident = tokens.first { it.type == TokenType.IDENTIFIER }
        assertEquals("world", ident.lexeme)
    }

    @Test
    fun `string after interpolation is KW_STRING`() {
        val tokens = tokenize("\"hello \${name}!\"")
        val endIdx = tokens.indexOfFirst { it.type == TokenType.INTERPOLATION_END }
        assertTrue(endIdx >= 0)
        // After INTERPOLATION_END there should be the remainder string
        val afterEnd = tokens.drop(endIdx + 1).firstOrNull { it.type != TokenType.EOF }
        assertEquals(TokenType.KW_STRING, afterEnd?.type, "Expected KW_STRING after INTERPOLATION_END, got ${afterEnd?.type}")
    }

    @Test
    fun `interpolation with expression - integer inside`() {
        val types = typesNoEof("\"\${42}\"")
        val startIdx = types.indexOf(TokenType.INTERPOLATION_START)
        val endIdx = types.indexOf(TokenType.INTERPOLATION_END)
        assertTrue(startIdx >= 0)
        assertTrue(endIdx > startIdx)
        val between = types.subList(startIdx + 1, endIdx)
        assertTrue(between.contains(TokenType.KW_INT), "Expected KW_INT inside interpolation, got $between")
    }

    // =========================================================================
    // 12. Multiple interpolations in one string
    // =========================================================================

    @Test
    fun `multiple interpolations produce multiple start and end markers`() {
        val types = typesNoEof("\"\${a} and \${b}\"")
        val startCount = types.count { it == TokenType.INTERPOLATION_START }
        val endCount = types.count { it == TokenType.INTERPOLATION_END }
        assertEquals(2, startCount, "Expected 2 INTERPOLATION_START tokens, got $startCount in $types")
        assertEquals(2, endCount, "Expected 2 INTERPOLATION_END tokens, got $endCount in $types")
    }

    @Test
    fun `multiple interpolations identifiers are both present`() {
        val tokens = tokenize("\"\${foo} and \${bar}\"")
        val idents = tokens.filter { it.type == TokenType.IDENTIFIER }.map { it.lexeme }
        assertTrue(idents.contains("foo"), "Expected 'foo' identifier in $idents")
        assertTrue(idents.contains("bar"), "Expected 'bar' identifier in $idents")
    }

    @Test
    fun `adjacent interpolations`() {
        val types = typesNoEof("\"\${a}\${b}\"")
        val startCount = types.count { it == TokenType.INTERPOLATION_START }
        val endCount = types.count { it == TokenType.INTERPOLATION_END }
        assertEquals(2, startCount)
        assertEquals(2, endCount)
    }

    // =========================================================================
    // 13. Line and column tracking
    // =========================================================================

    @Test
    fun `first token is on line 1`() {
        val tokens = tokenize("foo")
        assertEquals(1, tokens[0].line)
    }

    @Test
    fun `token after newline is on line 2`() {
        val tokens = tokenize("foo\nbar")
        val barToken = tokens.first { it.lexeme == "bar" }
        assertEquals(2, barToken.line)
    }

    @Test
    fun `column starts at 0`() {
        val tokens = tokenize("foo")
        assertEquals(0, tokens[0].column)
    }

    @Test
    fun `column advances correctly on same line`() {
        val tokens = tokenize("a b")
        val aToken = tokens.first { it.lexeme == "a" }
        val bToken = tokens.first { it.lexeme == "b" }
        assertTrue(bToken.column > aToken.column, "Expected 'b' column > 'a' column")
    }

    @Test
    fun `column resets after newline`() {
        val tokens = tokenize("foo\nbar")
        val barToken = tokens.first { it.lexeme == "bar" }
        assertEquals(0, barToken.column)
    }

    // =========================================================================
    // 14. Lexeme correctness for operators
    // =========================================================================

    @Test
    fun `lexeme for increment is plus plus`() {
        val token = tokenize("++")[0]
        assertEquals("++", token.lexeme)
    }

    @Test
    fun `lexeme for arrow is dash gt`() {
        val token = tokenize("->")[0]
        assertEquals("->", token.lexeme)
    }

    @Test
    fun `lexeme for power is star star`() {
        val token = tokenize("**")[0]
        assertEquals("**", token.lexeme)
    }

    @Test
    fun `lexeme for dot dot is two dots`() {
        val token = tokenize("..")[0]
        assertEquals("..", token.lexeme)
    }

    // =========================================================================
    // 15. EOF token
    // =========================================================================

    @Test
    fun `EOF is always last token`() {
        val tokens = tokenize("let x = 5")
        assertEquals(TokenType.EOF, tokens.last().type)
    }

    @Test
    fun `only one EOF token`() {
        val tokens = tokenize("let x = 5")
        assertEquals(1, tokens.count { it.type == TokenType.EOF })
    }

    // =========================================================================
    // 16. Compound / integration expressions
    // =========================================================================

    @Test
    fun `variable declaration tokenizes correctly`() {
        val types = typesNoEof("let x = 42")
        assertEquals(
            listOf(TokenType.KW_LET, TokenType.IDENTIFIER, TokenType.ASSIGN, TokenType.KW_INT),
            types
        )
    }

    @Test
    fun `function declaration header tokenizes correctly`() {
        val types = typesNoEof("fn add(a, b)")
        assertEquals(TokenType.KW_FN, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1]) // add
        assertEquals(TokenType.L_PAREN, types[2])
        assertEquals(TokenType.IDENTIFIER, types[3]) // a
        assertEquals(TokenType.COMMA, types[4])
        assertEquals(TokenType.IDENTIFIER, types[5]) // b
        assertEquals(TokenType.R_PAREN, types[6])
    }

    @Test
    fun `if else structure tokenizes correctly`() {
        val types = typesNoEof("if x { } else { }")
        assertEquals(TokenType.KW_IF, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertEquals(TokenType.L_BRACE, types[2])
        assertEquals(TokenType.R_BRACE, types[3])
        assertEquals(TokenType.KW_ELSE, types[4])
        assertEquals(TokenType.L_BRACE, types[5])
        assertEquals(TokenType.R_BRACE, types[6])
    }

    @Test
    fun `for in range tokenizes correctly`() {
        val types = typesNoEof("for i in 0..10")
        assertEquals(TokenType.KW_FOR, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertEquals(TokenType.KW_IN, types[2])
        assertEquals(TokenType.KW_INT, types[3])
        assertEquals(TokenType.DOT_DOT, types[4])
        assertEquals(TokenType.KW_INT, types[5])
    }

    @Test
    fun `return statement tokenizes correctly`() {
        val types = typesNoEof("return x + 1")
        assertEquals(TokenType.KW_RETURN, types[0])
        assertEquals(TokenType.IDENTIFIER, types[1])
        assertEquals(TokenType.PLUS, types[2])
        assertEquals(TokenType.KW_INT, types[3])
    }

    @Test
    fun `boolean operators tokenize correctly`() {
        val types = typesNoEof("a and b or not c")
        assertEquals(TokenType.IDENTIFIER, types[0])
        assertEquals(TokenType.KW_AND, types[1])
        assertEquals(TokenType.IDENTIFIER, types[2])
        assertEquals(TokenType.KW_OR, types[3])
        assertEquals(TokenType.KW_NOT, types[4])
        assertEquals(TokenType.IDENTIFIER, types[5])
    }

    @Test
    fun `array access tokenizes correctly`() {
        val types = typesNoEof("arr[0]")
        assertEquals(TokenType.IDENTIFIER, types[0])
        assertEquals(TokenType.L_SQUARE, types[1])
        assertEquals(TokenType.KW_INT, types[2])
        assertEquals(TokenType.R_SQUARE, types[3])
    }

    @Test
    fun `compound assignment operators`() {
        val types = typesNoEof("x += 1 x -= 2 x *= 3 x /= 4 x %= 5")
        assertTrue(types.contains(TokenType.ADD_EQUALS))
        assertTrue(types.contains(TokenType.SUB_EQUALS))
        assertTrue(types.contains(TokenType.MUL_EQUALS))
        assertTrue(types.contains(TokenType.DIV_EQUALS))
        assertTrue(types.contains(TokenType.MOD_EQUALS))
    }

    @Test
    fun `multi-line program with ASI`() {
        val source = """
            let x = 5
            let y = 10
            x + y
        """.trimIndent()
        val types = typesNoEof(source)
        // Each line should end with a SEMICOLON before the next let/identifier
        val semicolonCount = types.count { it == TokenType.SEMICOLON }
        assertTrue(semicolonCount >= 2, "Expected at least 2 semicolons from ASI, got $semicolonCount in $types")
    }

    @Test
    fun `whitespace between tokens is ignored`() {
        val types1 = typesNoEof("a+b")
        val types2 = typesNoEof("a  +  b")
        assertEquals(types1, types2)
    }

    @Test
    fun `tabs are treated as whitespace`() {
        val types = typesNoEof("let\tx\t=\t1")
        assertEquals(
            listOf(TokenType.KW_LET, TokenType.IDENTIFIER, TokenType.ASSIGN, TokenType.KW_INT),
            types
        )
    }
}
