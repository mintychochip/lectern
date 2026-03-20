package org.quill.intellij.structureView

import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.quill.intellij.file.QuillFileType
import org.quill.intellij.file.QuillIcons
import javax.swing.Icon

/**
 * Structure view element for Quill PSI elements.
 */
class QuillStructureViewElement(private val element: PsiElement) : StructureViewTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) {
        if (element is NavigationItem) {
            element.navigate(requestFocus)
        }
    }

    override fun canNavigate(): Boolean = element is NavigationItem && element.canNavigate()

    override fun canNavigateToSource(): Boolean = element is NavigationItem && element.canNavigateToSource()

    override fun getPresentation(): ItemPresentation {
        return object : ItemPresentation {
            override fun getPresentableText(): String {
                return when (element) {
                    is PsiFile -> element.name
                    else -> element.text.take(50)
                }
            }

            override fun getIcon(unused: Boolean): Icon? {
                return when (element) {
                    is PsiFile -> QuillFileType.icon
                    else -> null
                }
            }

            override fun getLocationString(): String = ""
        }
    }

    override fun getChildren(): Array<TreeElement> = emptyArray()
}
