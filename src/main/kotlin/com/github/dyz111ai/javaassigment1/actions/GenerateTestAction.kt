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
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*
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

        // 打开自定义对话框
        GenerateTestDialog(project, psiClass).show()
    }

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        event.presentation.isEnabledAndVisible = editor != null
    }
}

/**
 * 自定义对话框：输入需求、显示生成进度与结果
 */
class GenerateTestDialog(private val project: Project, private val psiClass: PsiClass) :
    DialogWrapper(project, true) {

    private val inputField = JTextField()
    private val outputArea = JTextArea()
    private val generateButton = JButton("Generate test code")

    init {
        title = "Generate test code for ${psiClass.name}"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(10, 10))

        // 输入区
        val inputPanel = JPanel(BorderLayout(5, 5))
        inputPanel.add(JLabel("Please input your test requirement："), BorderLayout.NORTH)
        inputPanel.add(inputField, BorderLayout.CENTER)

        // 输出区
        outputArea.isEditable = false
        outputArea.lineWrap = true
        outputArea.wrapStyleWord = true
        val scrollPane = JScrollPane(outputArea)
        scrollPane.preferredSize = Dimension(700, 400)

        // 按钮事件
        generateButton.addActionListener {
            val userRequirement = inputField.text.trim()
            if (userRequirement.isEmpty()) {
                Messages.showWarningDialog(project, "Please input your test requirement", "Prompt")
                return@addActionListener
            }

            generateButton.isEnabled = false
            outputArea.text = "Generating test code..."

            // 异步调用 LLM
            thread {
                try {
                    val llmService = LLMService()
                    val classSource = psiClass.text
                    val className = psiClass.name ?: "UnnamedClass"

                    val prompt = """
                        请根据以下 Java 类代码生成符合 JUnit5 规范的测试代码。
                        测试类命名为 ${className}Test。
                        测试需求：$userRequirement

                        类代码：
                        $classSource
                    """.trimIndent()

                    val response = llmService.generateResponse(classSource, prompt)

                    SwingUtilities.invokeLater {
                        outputArea.text = response
                        generateButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    SwingUtilities.invokeLater {
                        outputArea.text = "Failed to generate test code. Error message：${e.message}"
                        generateButton.isEnabled = true
                    }
                }
            }
        }

        // 底部按钮区
        val buttonPanel = JPanel(BorderLayout())
        buttonPanel.add(generateButton, BorderLayout.EAST)

        // 布局组合
        panel.add(inputPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }
}
