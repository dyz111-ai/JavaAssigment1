package com.github.dyz111ai.javaassigment1.chat

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel

class ChatToolWindow : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = ChatPanel(project)
        val content = ContentFactory.getInstance().createContent(chatPanel, "AI Teaching Assistant", false)
        toolWindow.contentManager.addContent(content)
    }
}

class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val chatService = ChatService()
    private val chatArea = JTextArea().apply {
        isEditable = false
        background = JBColor.WHITE
        border = JBUI.Borders.empty(10)
        lineWrap = true
        wrapStyleWord = true
    }
    private val inputField = JTextField()
    private val sendButton = JButton("Send").apply {
        addActionListener { sendMessage() }
    }
    private val scrollPane = JScrollPane(chatArea).apply {
        preferredSize = Dimension(400, 300)
    }
    // 添加当前文件状态标签
    private val currentFileLabel = JBLabel("current file: none").apply {
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(2, 10)
        toolTipText = "The code file currently being edited"
    }
    init {
        // 顶部面板 - 当前文件状态
        val statusPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            background = JBColor.PanelBackground
            val refreshButton = JButton("🔄").apply {
                addActionListener { updateCurrentFileStatus() }
                toolTipText = "refresh"
                border = JBUI.Borders.empty(2)
            }

            add(refreshButton, BorderLayout.WEST)
            add(currentFileLabel, BorderLayout.EAST)
        }
        // 顶部面板 - 文件上传
        val uploadPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(JLabel("Course Materials:"), BorderLayout.WEST)
            add(JButton("Upload Documents").apply {
                addActionListener { uploadDocuments() }
            }, BorderLayout.CENTER)
        }

        // 输入面板
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        val bottomPanel = JPanel(BorderLayout()).apply {
            add(uploadPanel, BorderLayout.NORTH)   // 上传面板在上方
            add(statusPanel, BorderLayout.CENTER)       // 状态文件在中间
            add(inputPanel, BorderLayout.SOUTH)    // 输入面板在下方
        }
        // 添加到主面板

        add(scrollPane, BorderLayout.CENTER)        // 聊天区域在中间
        add(bottomPanel, BorderLayout.SOUTH)        // 底部容器在下方（包含上传和输入）

        // 设置输入框回车键监听
        inputField.addActionListener { sendMessage() }

        appendMessage("AI", "Hello! I'm your Java Enterprise Course TA. How can I help you today?")
    }

    private fun sendMessage() {
        val question = inputField.text.trim()
        if (question.isNotEmpty()) {
            appendMessage("You", question)
            inputField.text = ""
            // 发送前更新文件状态
            updateCurrentFileStatus()
            // 在后台处理问题
            Thread {
                try {
                    val answer = chatService.askQuestion(question, project)
                    SwingUtilities.invokeLater {
                        appendMessage("AI", answer)
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        appendMessage("AI", "Error: ${e.message}")
                    }
                }
            }.start()
        }
    }

    private fun appendMessage(sender: String, message: String) {
        chatArea.append("$sender: $message\n\n")
        chatArea.caretPosition = chatArea.document.length
    }

    private fun uploadDocuments() {
        val fileChooser = JFileChooser().apply {
            setFileSelectionMode(JFileChooser.FILES_ONLY)
            isMultiSelectionEnabled = true
        }

        // 使用 FileNameExtensionFilter（推荐，更简单）
        fileChooser.addChoosableFileFilter(javax.swing.filechooser.FileNameExtensionFilter(
            "Course Documents (PDF, Word, Text, PowerPoint)",
            "pdf", "docx", "txt", "ppt", "pptx", "doc"
        ))

        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val files = fileChooser.selectedFiles
            if (files.isNotEmpty()) {
                Thread {
                    try {
                        chatService.processDocuments(files.toList())
                        SwingUtilities.invokeLater {
                            appendMessage("System", "✅ Successfully processed ${files.size} document(s)")
                            files.forEach { file ->
                                appendMessage("System", "   📄 ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        SwingUtilities.invokeLater {
                            appendMessage("System", "❌ Error processing documents: ${e.message}")
                        }
                    }
                }.start()
            }
        }
    }

    // 在 ChatPanel 类中添加这些公共方法
    fun setInputText(text: String) {
        inputField.text = text
        inputField.requestFocusInWindow()
        inputField.selectAll()
    }

    fun insertCodeToInput(selectedCode: String) {
        val currentText = inputField.text
        val codeBlock = "\n\nRegarding this code:\n```java\n$selectedCode\n```\n"

        if (currentText.isBlank()) {
            inputField.text = "Can you explain this code?$codeBlock"
        } else {
            inputField.text = "$currentText$codeBlock"
        }

        inputField.requestFocusInWindow()
        inputField.caretPosition = inputField.text.length
    }

    fun getChatArea(): JTextArea = chatArea

    // 添加项目访问器
    fun getProject(): Project = project

    /**
     * 更新当前文件状态显示
     */
    private fun updateCurrentFileStatus() {
        val currentFile = getCurrentFile()
        if (currentFile != null) {
            currentFileLabel.text = "current file: $currentFile"
            currentFileLabel.foreground = JBColor.BLUE

        } else {
            currentFileLabel.text = "current file:none"
            currentFileLabel.foreground = JBColor.GRAY

        }
    }

    /**
     * 获取当前编辑的文件名
     */
    private fun getCurrentFile(): String? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFiles = fileEditorManager.selectedFiles
        return selectedFiles.firstOrNull()?.name
    }
}