package org.quill.intellij.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import org.quill.intellij.file.QuillFileType
import org.quill.intellij.lang.QuillLanguage

/**
 * PSI file representation for Quill source files.
 */
class QuillFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, QuillLanguage.INSTANCE) {

    override fun getFileType(): FileType = QuillFileType

    override fun toString(): String = "Quill File"
}
