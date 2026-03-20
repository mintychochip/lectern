package org.quill.intellij.templates

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/**
 * Provides live templates for the Quill language.
 */
class QuillLiveTemplatesProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<String> = emptyArray()

    override fun getHiddenLiveTemplateFiles(): Array<String> = arrayOf("/liveTemplates/Quill")
}
