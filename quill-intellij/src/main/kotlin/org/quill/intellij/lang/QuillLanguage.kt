package org.quill.intellij.lang

import com.intellij.lang.Language

/**
 * The Quill programming language definition.
 */
class QuillLanguage private constructor() : Language("Quill") {

    companion object {
        @JvmStatic
        val INSTANCE = QuillLanguage()
    }

    override fun isCaseSensitive(): Boolean = true

    override fun getDisplayName(): String = "Quill"

    override fun getMimeTypes(): Array<String> = arrayOf("text/x-quill")
}
