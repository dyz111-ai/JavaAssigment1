package com.github.dyz111ai.javaassigment1.actions

import com.github.dyz111ai.javaassigment1.git.CommitMessageService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.project.Project
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection

class GenerateCommitMessageAction : AnAction("生成提交信息（AI）") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val service = CommitMessageService()
            val text = service.generateCommitMessage(project)
            val analysis = service.generateDiffAnalysis(project)

            ApplicationManager.getApplication().invokeLater {
                // 优先写入提交面板的 Commit Message 输入框
                val commitMessageControl = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
                if (commitMessageControl != null) {
                    commitMessageControl.setCommitMessage(text)
                }

                // 展示分析对话框（只读），帮助用户理解改动
                DiffAnalysisDialog(project, analysis).show()

                // 若无法获取提交控件，同时提供复制回退
                val controlNull = (commitMessageControl == null)
                if (controlNull) showResultDialog(project, text)
            }
        }, "生成提交信息", false, project)
    }

    private fun showResultDialog(project: Project, message: String) {
        val choice = Messages.showDialog(
            project,
            message,
            "AI生成的提交信息",
            arrayOf("复制到剪贴板", "关闭"),
            0,
            null
        )
        if (choice == 0) {
            CopyPasteManager.getInstance().setContents(StringSelection(message))
            Messages.showInfoMessage(project, "已复制到剪贴板", "完成")
        }
    }
}


