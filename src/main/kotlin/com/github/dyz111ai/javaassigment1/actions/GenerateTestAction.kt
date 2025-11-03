package com.github.dyz111ai.javaassigment1.actions

import com.github.dyz111ai.javaassigment1.llm.LLMService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

class GenerateTestAction : AnAction("Generate Test Code") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val caretOffset = editor.caretModel.offset
        val element = psiFile.findElementAt(caretOffset) ?: return
        val psiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)

        if (psiClass == null) {
            Messages.showErrorDialog(project, "Please select a Java class to generate tests.", "No Class Found")
            return
        }

        GenerateTestChatDialog(project, psiClass).show()
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor != null
    }
}

/**
 * 类似 ChatGPT 风格的交互窗口
 */
class GenerateTestChatDialog(private val project: Project, private val psiClass: PsiClass) :
    DialogWrapper(project, true) {

    private val chatPanel = JPanel()
    private val inputField = JTextField()
    private val sendButton = JButton("Send")

    init {
        title = "Chat with LLM - Generate Test for ${psiClass.name}"
        init()
        // 禁止按回车关闭对话框
        setOKActionEnabled(false)
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(10, 10))
        root.border = EmptyBorder(10, 10, 10, 10)

        // 聊天内容面板
        chatPanel.layout = BoxLayout(chatPanel, BoxLayout.Y_AXIS)
        chatPanel.background = Color(30, 30, 30)

        val scrollPane = JScrollPane(chatPanel)
        scrollPane.preferredSize = Dimension(700, 500)
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.background = Color(30, 30, 30)
        scrollPane.border = BorderFactory.createLineBorder(Color(60, 60, 60))

        // 底部输入区
        val inputPanel = JPanel(BorderLayout(5, 5))
        inputField.background = Color(40, 40, 40)
        inputField.foreground = Color.WHITE
        inputField.caretColor = Color.WHITE
        inputField.border = BorderFactory.createEmptyBorder(5, 8, 5, 8)

        // 绑定 Enter 键为发送
        inputField.addActionListener {
            onSendClicked()
        }

        sendButton.background = Color(70, 130, 180)
        sendButton.foreground = Color.WHITE
        sendButton.isFocusPainted = false
        sendButton.addActionListener { onSendClicked() }

        inputPanel.add(inputField, BorderLayout.CENTER)
        inputPanel.add(sendButton, BorderLayout.EAST)

        root.add(scrollPane, BorderLayout.CENTER)
        root.add(inputPanel, BorderLayout.SOUTH)

        return root
    }

    override fun createActions(): Array<Action> {
        // 自定义按钮，仅保留“Exit”按钮
        val exitAction = object : DialogWrapperAction("Exit") {
            override fun doAction(e: java.awt.event.ActionEvent?) {
                close(OK_EXIT_CODE)
            }
        }
        return arrayOf(exitAction)
    }

    private fun onSendClicked() {
        val userInput = inputField.text.trim()
        if (userInput.isEmpty()) return

        addMessageBubble("👤 你：$userInput", isUser = true)
        inputField.text = ""
        sendButton.isEnabled = false

        val classSource = psiClass.text
        val className = psiClass.name ?: "UnnamedClass"

        addMessageBubble("🤖 LLM：正在生成测试代码，请稍候...", isUser = false)

        thread {
            try {
                val llmService = LLMService()
                val prompt = """
                    请根据以下 Java 类代码生成符合 JUnit5 规范的测试代码。
                    测试类命名为 ${className}Test。
                    测试需求：$userInput

                    类代码：
                    $classSource
                """.trimIndent()

                val response = llmService.generateResponse(classSource, prompt)

                SwingUtilities.invokeLater {
                    addMessageBubble("🤖 LLM：\n$response", isUser = false)
                    sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    addMessageBubble("❌ 生成失败：${e.message}", isUser = false)
                    sendButton.isEnabled = true
                }
            }
        }
    }

    /**
     * 添加一条对话气泡（全宽）
     */
    private fun addMessageBubble(text: String, isUser: Boolean) {
        val bubble = JTextArea(text)
        bubble.lineWrap = true
        bubble.wrapStyleWord = true
        bubble.isEditable = false
        bubble.margin = Insets(8, 10, 8, 10)
        bubble.background = if (isUser) Color(60, 60, 60) else Color(45, 45, 45)
        bubble.foreground = if (isUser) Color(255, 255, 255) else Color(173, 216, 230)
        bubble.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        val wrapper = JPanel(BorderLayout())
        wrapper.background = Color(30, 30, 30)
        wrapper.border = EmptyBorder(4, 4, 4, 4)
        wrapper.add(bubble, BorderLayout.CENTER)

        chatPanel.add(wrapper)
        chatPanel.add(Box.createVerticalStrut(5))
        chatPanel.revalidate()
        chatPanel.repaint()
    }
}