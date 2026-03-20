package org.quill.intellij.structureView

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.Sorter
import com.intellij.psi.PsiFile

/**
 * Structure view model for Quill files.
 */
class QuillStructureViewModel(psiFile: PsiFile) :
    StructureViewModelBase(psiFile, QuillStructureViewElement(psiFile)),
    StructureViewModel.ElementInfoProvider {

    override fun getSorters(): Array<Sorter> = arrayOf(Sorter.ALPHA_SORTER)

    override fun isAlwaysShowsPlus(structureViewTreeElement: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(structureViewTreeElement: StructureViewTreeElement): Boolean = false
}
