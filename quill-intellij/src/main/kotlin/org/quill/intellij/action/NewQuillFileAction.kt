package org.quill.intellij.action

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import org.quill.intellij.file.QuillFileType

/**
 * Action for creating new Quill files.
 */
class NewQuillFileAction : AnAction("Quill File", "Create a new Quill file", QuillFileType.icon) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val directory = e.getData(CommonDataKeys.PSI_ELEMENT) as? PsiDirectory ?: return

        val name = Messages.showInputDialog(
            project,
            "Enter file name:",
            "New Quill File",
            QuillFileType.icon
        ) ?: return

        val fileName = if (name.endsWith(".quill")) name else "$name.quill"
        val template = FileTemplateManager.getInstance(project).getInternalTemplate("Quill File")

        try {
            FileTemplateUtil.createFromTemplate(template, fileName, null, directory)
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "Failed to create file: ${ex.message}", "Error")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.PSI_ELEMENT) is PsiDirectory
    }
}
