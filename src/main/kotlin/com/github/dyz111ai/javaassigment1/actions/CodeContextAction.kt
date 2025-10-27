package com.github.dyz111ai.javaassigment1.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.github.dyz111ai.javaassigment1.chat.ChatPanel

class CodeContextAction : AnAction("Ask AI Teaching Assistant about Selected Code") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val selectedText = editor?.selectionModel?.selectedText

        if (selectedText.isNullOrBlank()) {
            Messages.showInfoMessage(project, "Please select some code to ask about", "No Code Selected")
            return
        }

        // 激活并显示聊天工具窗口
        val toolWindowManager = ToolWindowManager.getInstance(project)
        val chatToolWindow = toolWindowManager.getToolWindow("AI Teaching Assistant")

        if (chatToolWindow != null) {
            chatToolWindow.activate(null)

            // 获取聊天面板组件
            val chatPanel = findChatPanel(chatToolWindow)
            if (chatPanel != null) {
                // 使用公共方法插入代码
                chatPanel.insertCodeToInput(selectedText)
            } else {
                showChatWindowNotFound(project)
            }
        } else {
            showChatWindowNotFound(project)
        }
    }

    private fun findChatPanel(toolWindow: com.intellij.openapi.wm.ToolWindow): ChatPanel? {
        val contentManager = toolWindow.contentManager
        if (contentManager.contentCount > 0) {
            val content = contentManager.getContent(0)
            return content?.component as? ChatPanel
        }
        return null
    }

    private fun showChatWindowNotFound(project: com.intellij.openapi.project.Project) {
        Messages.showInfoMessage(
            project,
            "Please open the AI Teaching Assistant window first (View → Tool Windows → AI Teaching Assistant)",
            "Chat Window Not Found"
        )
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        e.presentation.isEnabledAndVisible = hasSelection

        // 可选：更新文本显示选中的代码行数
        val selectedText = editor?.selectionModel?.selectedText
        if (selectedText != null) {
            val lineCount = selectedText.lines().size
            e.presentation.text = "Ask AI about Selected Code ($lineCount lines)"
        } else {
            e.presentation.text = "Ask AI Teaching Assistant about Selected Code"
        }
    }
}