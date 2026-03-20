package org.quill.intellij.formatter

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import org.quill.intellij.lang.QuillLanguage
import org.quill.intellij.lexer.QuillTokens
import org.quill.intellij.psi.QuillElementTypes

/**
 * Formatting model builder for the Quill language.
 */
class QuillFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val element = formattingContext.psiElement
        val settings = formattingContext.codeStyleSettings

        val rootBlock = QuillBlock(
            node = element.node,
            indent = Indent.getNoneIndent(),
            settings = settings
        )

        return FormattingModelProvider.createFormattingModelForPsiFile(
            element.containingFile,
            rootBlock,
            settings
        )
    }

    override fun getRangeAffectingIndent(file: PsiFile?, offset: Int, elementAtOffset: ASTNode?): TextRange? = null
}

/**
 * Block implementation for Quill formatting.
 */
class QuillBlock(
    private val node: ASTNode,
    private val indent: Indent?,
    private val settings: CodeStyleSettings
) : ASTBlock {

    private var mySubBlocks: List<Block>? = null

    override fun getNode(): ASTNode = node

    override fun getTextRange(): TextRange = node.textRange

    override fun getSubBlocks(): List<Block> {
        if (mySubBlocks == null) {
            mySubBlocks = buildSubBlocks()
        }
        return mySubBlocks!!
    }

    private fun buildSubBlocks(): List<Block> {
        val blocks = mutableListOf<Block>()
        var child = node.firstChildNode
        while (child != null) {
            if (child.textRange.length > 0) {
                val childIndent = calculateIndent(child)
                blocks.add(QuillBlock(child, childIndent, settings))
            }
            child = child.treeNext
        }
        return blocks
    }

    private fun calculateIndent(child: ASTNode): Indent? {
        val parent = child.treeParent ?: return Indent.getNoneIndent()
        return when {
            parent.elementType == QuillElementTypes.BLOCK -> Indent.getNormalIndent()
            else -> Indent.getNoneIndent()
        }
    }

    override fun getWrap(): Wrap? = null

    override fun getIndent(): Indent? = indent

    override fun getAlignment(): Alignment? = null

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        return Spacing.createSpacing(0, 1, 0, false, 0)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(Indent.getNormalIndent(), null)
    }

    override fun isIncomplete(): Boolean = false

    override fun isLeaf(): Boolean = node.firstChildNode == null
}
