package org.quill.intellij.highlighter

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.quill.intellij.lexer.QuillLexer
import org.quill.intellij.lexer.QuillTokens

/**
 * Syntax highlighter for the Quill language.
 */
class QuillSyntaxHighlighter : SyntaxHighlighter {

    companion object {
        // Colors
        val KEYWORD = createTextAttributesKey("LECTERN_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val STRING = createTextAttributesKey("LECTERN_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = createTextAttributesKey("LECTERN_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val BOOLEAN = createTextAttributesKey("LECTERN_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD)
        val NULL = createTextAttributesKey("LECTERN_NULL", DefaultLanguageHighlighterColors.KEYWORD)
        val COMMENT = createTextAttributesKey("LECTERN_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val OPERATOR = createTextAttributesKey("LECTERN_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACKET = createTextAttributesKey("LECTERN_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
        val PAREN = createTextAttributesKey("LECTERN_PAREN", DefaultLanguageHighlighterColors.PARENTHESES)
        val BRACE = createTextAttributesKey("LECTERN_BRACE", DefaultLanguageHighlighterColors.BRACES)
        val IDENTIFIER = createTextAttributesKey("LECTERN_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER)
        val BAD_CHARACTER = createTextAttributesKey("LECTERN_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val KEYWORD_TOKENS = setOf(
            QuillTokens.BOOL, QuillTokens.INT, QuillTokens.FLOAT, QuillTokens.DOUBLE, QuillTokens.STRING,
            QuillTokens.TRUE, QuillTokens.FALSE, QuillTokens.NULL,
            QuillTokens.LET, QuillTokens.CONST,
            QuillTokens.IF, QuillTokens.ELSE, QuillTokens.WHILE, QuillTokens.FOR, QuillTokens.IN,
            QuillTokens.FN, QuillTokens.RETURN,
            QuillTokens.AND, QuillTokens.OR, QuillTokens.NOT,
            QuillTokens.BREAK, QuillTokens.NEXT,
            QuillTokens.ENUM, QuillTokens.CLASS, QuillTokens.EXTENDS,
            QuillTokens.IMPORT, QuillTokens.FROM, QuillTokens.IS, QuillTokens.THIS
        )

        private val OPERATOR_TOKENS = setOf(
            QuillTokens.PLUS, QuillTokens.MINUS, QuillTokens.STAR, QuillTokens.SLASH, QuillTokens.PERCENT,
            QuillTokens.PLUS_PLUS, QuillTokens.MINUS_MINUS, QuillTokens.STAR_STAR,
            QuillTokens.EQ_EQ, QuillTokens.BANG_EQ, QuillTokens.LT, QuillTokens.GT, QuillTokens.LT_EQ, QuillTokens.GT_EQ,
            QuillTokens.EQ, QuillTokens.PLUS_EQ, QuillTokens.MINUS_EQ, QuillTokens.STAR_EQ, QuillTokens.SLASH_EQ, QuillTokens.PERCENT_EQ,
            QuillTokens.ARROW, QuillTokens.BANG, QuillTokens.DOT_DOT, QuillTokens.DOT, QuillTokens.COLON, QuillTokens.COMMA, QuillTokens.SEMICOLON
        )
    }

    override fun getHighlightingLexer(): Lexer = QuillLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            in KEYWORD_TOKENS -> arrayOf(KEYWORD)
            QuillTokens.STRING_LITERAL -> arrayOf(STRING)
            QuillTokens.INTEGER_LITERAL, QuillTokens.FLOAT_LITERAL, QuillTokens.DOUBLE_LITERAL -> arrayOf(NUMBER)
            QuillTokens.LINE_COMMENT, QuillTokens.BLOCK_COMMENT -> arrayOf(COMMENT)
            in OPERATOR_TOKENS -> arrayOf(OPERATOR)
            QuillTokens.LPAREN, QuillTokens.RPAREN -> arrayOf(PAREN)
            QuillTokens.LBRACE, QuillTokens.RBRACE -> arrayOf(BRACE)
            QuillTokens.LBRACKET, QuillTokens.RBRACKET -> arrayOf(BRACKET)
            QuillTokens.BAD_CHARACTER -> arrayOf(BAD_CHARACTER)
            else -> emptyArray()
        }
    }
}

/**
 * Factory for creating Quill syntax highlighters.
 */
class QuillSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return QuillSyntaxHighlighter()
    }
}
