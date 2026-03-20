package org.quill.intellij.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import org.quill.intellij.lexer.QuillTokens
import org.quill.intellij.psi.QuillElementTypes

/**
 * Minimal, safe parser for Quill that avoids creating error markers.
 * Just groups tokens loosely without strict validation.
 */
class QuillParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()

        while (!builder.eof()) {
            parseAny(builder)
        }

        marker.done(root)
        return builder.treeBuilt
    }

    private fun parseAny(builder: PsiBuilder) {
        val token = builder.tokenType ?: return

        when (token) {
            // Comments - just skip
            QuillTokens.LINE_COMMENT, QuillTokens.BLOCK_COMMENT -> {
                builder.advanceLexer()
            }

            // Whitespace - just skip
            QuillTokens.WHITE_SPACE -> {
                builder.advanceLexer()
            }

            // Block - parse contents
            QuillTokens.LBRACE -> parseBracedBlock(builder)

            // Paren group
            QuillTokens.LPAREN -> parseParenGroup(builder)

            // Bracket group
            QuillTokens.LBRACKET -> parseBracketGroup(builder)

            // Everything else - just consume
            else -> builder.advanceLexer()
        }
    }

    private fun parseBracedBlock(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // {

        var depth = 1
        while (!builder.eof() && depth > 0) {
            when (builder.tokenType) {
                QuillTokens.LBRACE -> { builder.advanceLexer(); depth++ }
                QuillTokens.RBRACE -> { builder.advanceLexer(); depth-- }
                else -> builder.advanceLexer()
            }
        }

        marker.done(QuillElementTypes.BLOCK)
    }

    private fun parseParenGroup(builder: PsiBuilder) {
        builder.advanceLexer() // (

        var depth = 1
        while (!builder.eof() && depth > 0) {
            when (builder.tokenType) {
                QuillTokens.LPAREN -> { builder.advanceLexer(); depth++ }
                QuillTokens.RPAREN -> { builder.advanceLexer(); depth-- }
                else -> builder.advanceLexer()
            }
        }
    }

    private fun parseBracketGroup(builder: PsiBuilder) {
        builder.advanceLexer() // [

        var depth = 1
        while (!builder.eof() && depth > 0) {
            when (builder.tokenType) {
                QuillTokens.LBRACKET -> { builder.advanceLexer(); depth++ }
                QuillTokens.RBRACKET -> { builder.advanceLexer(); depth-- }
                else -> builder.advanceLexer()
            }
        }
    }
}
