package org.quill.intellij.psi

import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ICompositeElementType
import org.quill.intellij.lang.QuillLanguage

/**
 * Element type for composite PSI elements in the Quill language.
 */
class QuillElementType(debugName: String) : IElementType(debugName, QuillLanguage.INSTANCE), ICompositeElementType {

    override fun createCompositeNode(): ASTNode {
        return QuillCompositeElement(this)
    }
}

/**
 * Composite PSI element for Quill.
 */
class QuillCompositeElement(type: IElementType) : CompositePsiElement(type) {
    override fun toString(): String = "Quill:${elementType}"
}

/**
 * PSI element types for Quill.
 */
object QuillElementTypes {
    @JvmField val BLOCK = QuillElementType("BLOCK")
}
