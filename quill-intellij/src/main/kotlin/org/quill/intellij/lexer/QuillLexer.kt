package org.quill.intellij.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Simple, safe lexer for Quill that avoids infinite loops.
 * Only does basic tokenization for syntax highlighting.
 */
class QuillLexer : LexerBase() {

    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var bufferEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.startOffset = startOffset
        this.endOffset = startOffset
        this.tokenType = null
        advance()
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = startOffset

    override fun getTokenEnd(): Int = endOffset

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        if (endOffset >= bufferEnd) {
            tokenType = null
            return
        }

        startOffset = endOffset
        val c = buffer[endOffset]

        when {
            // Whitespace
            c.isWhitespace() -> {
                while (endOffset < bufferEnd && buffer[endOffset].isWhitespace()) {
                    endOffset++
                }
                tokenType = QuillTokens.WHITE_SPACE
            }

            // Line comment
            c == '/' && peek(1) == '/' -> {
                endOffset += 2
                while (endOffset < bufferEnd && buffer[endOffset] != '\n') {
                    endOffset++
                }
                tokenType = QuillTokens.LINE_COMMENT
            }

            // Block comment
            c == '/' && peek(1) == '*' -> {
                endOffset += 2
                while (endOffset < bufferEnd - 1) {
                    if (buffer[endOffset] == '*' && buffer[endOffset + 1] == '/') {
                        endOffset += 2
                        break
                    }
                    endOffset++
                }
                if (endOffset >= bufferEnd - 1) endOffset = bufferEnd
                tokenType = QuillTokens.BLOCK_COMMENT
            }

            // String
            c == '"' -> {
                endOffset++
                while (endOffset < bufferEnd) {
                    when (buffer[endOffset]) {
                        '\\' -> endOffset += 2
                        '"' -> { endOffset++; break }
                        else -> endOffset++
                    }
                    if (endOffset > bufferEnd) endOffset = bufferEnd
                }
                tokenType = QuillTokens.STRING_LITERAL
            }

            // Number
            c.isDigit() -> {
                while (endOffset < bufferEnd && buffer[endOffset].isDigit()) {
                    endOffset++
                }
                if (endOffset < bufferEnd && buffer[endOffset] == '.' &&
                    endOffset + 1 < bufferEnd && buffer[endOffset + 1].isDigit()) {
                    endOffset++
                    while (endOffset < bufferEnd && buffer[endOffset].isDigit()) {
                        endOffset++
                    }
                    if (endOffset < bufferEnd && (buffer[endOffset] == 'd' || buffer[endOffset] == 'D')) {
                        endOffset++
                        tokenType = QuillTokens.DOUBLE_LITERAL
                    } else {
                        tokenType = QuillTokens.FLOAT_LITERAL
                    }
                } else {
                    tokenType = QuillTokens.INTEGER_LITERAL
                }
            }

            // Identifier or keyword
            c.isLetter() || c == '_' -> {
                while (endOffset < bufferEnd && (buffer[endOffset].isLetterOrDigit() || buffer[endOffset] == '_')) {
                    endOffset++
                }
                val text = buffer.subSequence(startOffset, endOffset).toString()
                tokenType = KEYWORDS[text] ?: QuillTokens.IDENTIFIER
            }

            // Multi-char operators
            match("**") -> { endOffset += 2; tokenType = QuillTokens.STAR_STAR }
            match("++") -> { endOffset += 2; tokenType = QuillTokens.PLUS_PLUS }
            match("--") -> { endOffset += 2; tokenType = QuillTokens.MINUS_MINUS }
            match("==") -> { endOffset += 2; tokenType = QuillTokens.EQ_EQ }
            match("!=") -> { endOffset += 2; tokenType = QuillTokens.BANG_EQ }
            match("<=") -> { endOffset += 2; tokenType = QuillTokens.LT_EQ }
            match(">=") -> { endOffset += 2; tokenType = QuillTokens.GT_EQ }
            match("+=") -> { endOffset += 2; tokenType = QuillTokens.PLUS_EQ }
            match("-=") -> { endOffset += 2; tokenType = QuillTokens.MINUS_EQ }
            match("*=") -> { endOffset += 2; tokenType = QuillTokens.STAR_EQ }
            match("/=") -> { endOffset += 2; tokenType = QuillTokens.SLASH_EQ }
            match("%=") -> { endOffset += 2; tokenType = QuillTokens.PERCENT_EQ }
            match("->") -> { endOffset += 2; tokenType = QuillTokens.ARROW }
            match("..") -> { endOffset += 2; tokenType = QuillTokens.DOT_DOT }

            // Single char tokens
            else -> {
                endOffset++
                tokenType = when (c) {
                    '+' -> QuillTokens.PLUS
                    '-' -> QuillTokens.MINUS
                    '*' -> QuillTokens.STAR
                    '/' -> QuillTokens.SLASH
                    '%' -> QuillTokens.PERCENT
                    '<' -> QuillTokens.LT
                    '>' -> QuillTokens.GT
                    '=' -> QuillTokens.EQ
                    '!' -> QuillTokens.BANG
                    '.' -> QuillTokens.DOT
                    ':' -> QuillTokens.COLON
                    ',' -> QuillTokens.COMMA
                    ';' -> QuillTokens.SEMICOLON
                    '(' -> QuillTokens.LPAREN
                    ')' -> QuillTokens.RPAREN
                    '{' -> QuillTokens.LBRACE
                    '}' -> QuillTokens.RBRACE
                    '[' -> QuillTokens.LBRACKET
                    ']' -> QuillTokens.RBRACKET
                    else -> QuillTokens.BAD_CHARACTER
                }
            }
        }
    }

    private fun peek(offset: Int): Char {
        val idx = endOffset + offset
        return if (idx < bufferEnd) buffer[idx] else '\u0000'
    }

    private fun match(s: String): Boolean {
        if (endOffset + s.length > bufferEnd) return false
        for (i in s.indices) {
            if (buffer[endOffset + i] != s[i]) return false
        }
        return true
    }

    companion object {
        private val KEYWORDS = mapOf(
            "bool" to QuillTokens.BOOL,
            "int" to QuillTokens.INT,
            "float" to QuillTokens.FLOAT,
            "double" to QuillTokens.DOUBLE,
            "string" to QuillTokens.STRING,
            "true" to QuillTokens.TRUE,
            "false" to QuillTokens.FALSE,
            "null" to QuillTokens.NULL,
            "let" to QuillTokens.LET,
            "const" to QuillTokens.CONST,
            "if" to QuillTokens.IF,
            "else" to QuillTokens.ELSE,
            "while" to QuillTokens.WHILE,
            "for" to QuillTokens.FOR,
            "in" to QuillTokens.IN,
            "fn" to QuillTokens.FN,
            "return" to QuillTokens.RETURN,
            "and" to QuillTokens.AND,
            "or" to QuillTokens.OR,
            "not" to QuillTokens.NOT,
            "break" to QuillTokens.BREAK,
            "next" to QuillTokens.NEXT,
            "enum" to QuillTokens.ENUM,
            "class" to QuillTokens.CLASS,
            "extends" to QuillTokens.EXTENDS,
            "import" to QuillTokens.IMPORT,
            "from" to QuillTokens.FROM,
            "is" to QuillTokens.IS,
            "this" to QuillTokens.THIS
        )
    }
}
