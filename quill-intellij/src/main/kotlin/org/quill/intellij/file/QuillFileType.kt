package org.quill.intellij.file

import com.intellij.openapi.fileTypes.LanguageFileType
import org.quill.intellij.lang.QuillLanguage
import javax.swing.Icon

/**
 * File type definition for Quill source files (.quill).
 */
object QuillFileType : LanguageFileType(QuillLanguage.INSTANCE) {

    override fun getName(): String = "Quill"

    override fun getDescription(): String = "Quill source file"

    override fun getDefaultExtension(): String = "quill"

    override fun getIcon(): Icon = QuillIcons.FILE
}
