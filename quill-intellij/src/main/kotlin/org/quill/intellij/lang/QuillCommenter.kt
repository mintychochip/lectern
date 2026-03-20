package org.quill.intellij.lang

import com.intellij.lang.Commenter

/**
 * Comment handler for the Quill language.
 */
class QuillCommenter : Commenter {

    override fun getLineCommentPrefix(): String = "//"

    override fun getBlockCommentPrefix(): String = "/*"

    override fun getBlockCommentSuffix(): String = "*/"

    override fun getCommentedBlockCommentPrefix(): String? = "/*"

    override fun getCommentedBlockCommentSuffix(): String? = "*/"
}
