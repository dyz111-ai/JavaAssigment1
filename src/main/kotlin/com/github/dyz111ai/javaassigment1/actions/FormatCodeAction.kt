package com.github.dyz111ai.javaassigment1.actions

import com.intellij.codeInsight.actions.ReformatCodeProcessor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.project.DumbAware

class FormatCodeAction : AnAction("规范代码"), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        if (!psiFile.isWritable) {
            Messages.showInfoMessage(project, "当前文件不可写（只读或二进制文件）。", "规范代码")
            return
        }

        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) {
            Messages.showInfoMessage(project, "请先选中一段代码，再右键选择“规范代码”。", "规范代码")
            return
        }

        val range = TextRange(selectionModel.selectionStart, selectionModel.selectionEnd)
        ReformatCodeProcessor(project, psiFile, range, false).run()
    }

    override fun update(e: AnActionEvent) {
        // 临时全部设为 true，帮助排查菜单项是否出现。
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
    }
}


