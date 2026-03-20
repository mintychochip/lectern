package org.quill.intellij.lang

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.intellij.psi.PsiElement
import org.quill.intellij.lexer.QuillTokens

/**
 * Quote handler for the Quill language.
 */
class QuillQuoteHandler : SimpleTokenSetQuoteHandler(
    QuillTokens.STRING_LITERAL
) {

    override fun isClosingQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val tokenType = iterator.tokenType
        if (tokenType == QuillTokens.STRING_LITERAL) {
            val start = iterator.start
            val end = iterator.end
            return offset == end - 1 && start < end
        }
        return false
    }

    override fun isOpeningQuote(iterator: HighlighterIterator, offset: Int): Boolean {
        val tokenType = iterator.tokenType
        if (tokenType == QuillTokens.STRING_LITERAL) {
            val start = iterator.start
            return offset == start
        }
        return false
    }

    override fun hasNonClosedLiteral(editor: Editor, iterator: HighlighterIterator, offset: Int): Boolean {
        return true
    }

    override fun isInsideLiteral(iterator: HighlighterIterator): Boolean {
        return iterator.tokenType == QuillTokens.STRING_LITERAL
    }
}
