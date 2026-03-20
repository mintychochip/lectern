package org.quill.intellij.lang

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import org.quill.intellij.lexer.QuillTokens

/**
 * Brace matcher for the Quill language.
 */
class QuillBraceMatcher : PairedBraceMatcher {

    companion object {
        private val PAIRS = arrayOf(
            BracePair(QuillTokens.LPAREN, QuillTokens.RPAREN, false),
            BracePair(QuillTokens.LBRACE, QuillTokens.RBRACE, true),
            BracePair(QuillTokens.LBRACKET, QuillTokens.RBRACKET, false)
        )
    }

    override fun getPairs(): Array<BracePair> = PAIRS

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}
