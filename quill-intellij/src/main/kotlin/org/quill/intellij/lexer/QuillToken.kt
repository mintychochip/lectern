package org.quill.intellij.lexer

import com.intellij.psi.tree.IElementType
import org.quill.intellij.lang.QuillLanguage

/**
 * Token types for the Quill language.
 */
class QuillToken(debugName: String) : IElementType(debugName, QuillLanguage.INSTANCE) {
    override fun toString(): String = "QuillToken." + super.toString()
}

/**
 * Token type constants for Quill language.
 */
object QuillTokens {
    // Type keywords
    @JvmField val BOOL = QuillToken("bool")
    @JvmField val INT = QuillToken("int")
    @JvmField val FLOAT = QuillToken("float")
    @JvmField val DOUBLE = QuillToken("double")
    @JvmField val STRING = QuillToken("string")

    // Boolean/null literals
    @JvmField val TRUE = QuillToken("true")
    @JvmField val FALSE = QuillToken("false")
    @JvmField val NULL = QuillToken("null")

    // Variable keywords
    @JvmField val LET = QuillToken("let")
    @JvmField val CONST = QuillToken("const")

    // Control flow keywords
    @JvmField val IF = QuillToken("if")
    @JvmField val ELSE = QuillToken("else")
    @JvmField val WHILE = QuillToken("while")
    @JvmField val FOR = QuillToken("for")
    @JvmField val IN = QuillToken("in")

    // Function keywords
    @JvmField val FN = QuillToken("fn")
    @JvmField val RETURN = QuillToken("return")

    // Logical keywords
    @JvmField val AND = QuillToken("and")
    @JvmField val OR = QuillToken("or")
    @JvmField val NOT = QuillToken("not")

    // Loop control
    @JvmField val BREAK = QuillToken("break")
    @JvmField val NEXT = QuillToken("next")

    // Type definitions
    @JvmField val ENUM = QuillToken("enum")
    @JvmField val CLASS = QuillToken("class")
    @JvmField val EXTENDS = QuillToken("extends")

    // Module keywords
    @JvmField val IMPORT = QuillToken("import")
    @JvmField val FROM = QuillToken("from")

    // Other keywords
    @JvmField val IS = QuillToken("is")
    @JvmField val THIS = QuillToken("this")

    // Literals
    @JvmField val INTEGER_LITERAL = QuillToken("INTEGER")
    @JvmField val FLOAT_LITERAL = QuillToken("FLOAT")
    @JvmField val DOUBLE_LITERAL = QuillToken("DOUBLE")
    @JvmField val STRING_LITERAL = QuillToken("STRING")
    @JvmField val IDENTIFIER = QuillToken("IDENTIFIER")

    // Operators
    @JvmField val PLUS = QuillToken("+")
    @JvmField val MINUS = QuillToken("-")
    @JvmField val STAR = QuillToken("*")
    @JvmField val SLASH = QuillToken("/")
    @JvmField val PERCENT = QuillToken("%")
    @JvmField val PLUS_PLUS = QuillToken("++")
    @JvmField val MINUS_MINUS = QuillToken("--")
    @JvmField val STAR_STAR = QuillToken("**")
    @JvmField val EQ_EQ = QuillToken("==")
    @JvmField val BANG_EQ = QuillToken("!=")
    @JvmField val LT = QuillToken("<")
    @JvmField val GT = QuillToken(">")
    @JvmField val LT_EQ = QuillToken("<=")
    @JvmField val GT_EQ = QuillToken(">=")
    @JvmField val EQ = QuillToken("=")
    @JvmField val PLUS_EQ = QuillToken("+=")
    @JvmField val MINUS_EQ = QuillToken("-=")
    @JvmField val STAR_EQ = QuillToken("*=")
    @JvmField val SLASH_EQ = QuillToken("/=")
    @JvmField val PERCENT_EQ = QuillToken("%=")
    @JvmField val ARROW = QuillToken("->")
    @JvmField val BANG = QuillToken("!")
    @JvmField val DOT_DOT = QuillToken("..")
    @JvmField val DOT = QuillToken(".")
    @JvmField val COLON = QuillToken(":")
    @JvmField val COMMA = QuillToken(",")
    @JvmField val SEMICOLON = QuillToken(";")

    // Brackets
    @JvmField val LPAREN = QuillToken("(")
    @JvmField val RPAREN = QuillToken(")")
    @JvmField val LBRACE = QuillToken("{")
    @JvmField val RBRACE = QuillToken("}")
    @JvmField val LBRACKET = QuillToken("[")
    @JvmField val RBRACKET = QuillToken("]")

    // Comments
    @JvmField val LINE_COMMENT = QuillToken("LINE_COMMENT")
    @JvmField val BLOCK_COMMENT = QuillToken("BLOCK_COMMENT")

    // Special
    @JvmField val WHITE_SPACE = QuillToken("WHITE_SPACE")
    @JvmField val BAD_CHARACTER = QuillToken("BAD_CHARACTER")
}
