package org.quill.intellij.templates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import org.quill.intellij.lang.QuillLanguage

/**
 * Live template context for the Quill language.
 */
class QuillContext : TemplateContextType("LECTERN", "Quill") {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file.language == QuillLanguage.INSTANCE
    }
}
