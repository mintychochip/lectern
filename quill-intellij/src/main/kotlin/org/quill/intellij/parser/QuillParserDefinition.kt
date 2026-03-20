package org.quill.intellij.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.ICompositeElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.quill.intellij.lang.QuillLanguage
import org.quill.intellij.lexer.QuillLexer
import org.quill.intellij.lexer.QuillTokens
import org.quill.intellij.psi.QuillFile

/**
 * Parser definition for the Quill language.
 */
class QuillParserDefinition : ParserDefinition {

    companion object {
        val FILE = IFileElementType(QuillLanguage.INSTANCE)

        val WHITESPACES = TokenSet.create(QuillTokens.WHITE_SPACE)
        val COMMENTS = TokenSet.create(QuillTokens.LINE_COMMENT, QuillTokens.BLOCK_COMMENT)
        val STRING_LITERALS = TokenSet.create(QuillTokens.STRING_LITERAL)
    }

    override fun createLexer(project: Project?): Lexer = QuillLexer()

    override fun createParser(project: Project?): PsiParser = QuillParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = STRING_LITERALS

    override fun createElement(node: ASTNode): PsiElement {
        val type = node.elementType
        return if (type is ICompositeElementType) {
            type.createCompositeNode().psi
        } else {
            LeafPsiElement(type, node.text)
        }
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = QuillFile(viewProvider)
}
