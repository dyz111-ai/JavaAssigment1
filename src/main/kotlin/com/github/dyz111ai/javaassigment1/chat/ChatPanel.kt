package com.github.dyz111ai.javaassigment1.chat

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout

import java.util.*
import javax.swing.*
import java.io.File
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
/**
 * 聊天主面板类，负责整个聊天界面的布局和核心功能协调
 * 集成了会话管理、文件上传、消息显示等功能组件
 */
class ChatPanel(private val project: Project) : JPanel(BorderLayout()) {

    // 服务层组件：处理AI聊天和聊天历史数据
    private val chatService = ChatService()                    // AI聊天服务，处理与AI的交互
    private val chatHistoryManager = ChatHistoryManager(project) // 聊天历史管理器，负责会话和消息的持久化
    // 测试对话框


    // 拆分的功能组件：遵循单一职责原则，提高代码可维护性
    private val sessionManager = SessionManager(this, chatHistoryManager)        // 会话管理组件
    private val fileUploadManager = FileUploadManager(this, chatService, chatHistoryManager) // 文件上传管理组件

    // UI Components - 界面核心组件定义
    private val chatArea = JTextArea().apply {
        isEditable = false
        background = JBColor.WHITE
        border = JBUI.Borders.empty(10)
        lineWrap = true
        wrapStyleWord = true
    }
    private val inputField = JTextField()     // 用户输入框
    private val sendButton = JButton("Send")  // 发送按钮
    private val scrollPane = JScrollPane(chatArea).apply {
        preferredSize = Dimension(400, 300)   // 设置聊天区域的默认大小
    }
    // 当前文件状态标签：显示当前在IDE中编辑的文件
    private val currentFileLabel = JBLabel("current file: none").apply {
        foreground = JBColor.GRAY
        border = JBUI.Borders.empty(2, 10)
        toolTipText = "The code file currently being edited"
    }



    /**
     * 初始化方法：设置界面、事件监听器，并加载初始数据
     */
    init {

        setupUI()                           // 初始化用户界面布局
        setupEventListeners()               // 设置事件监听器
        sessionManager.loadSessionList()    // 加载会话列表
        displayCurrentSessionMessages()     // 显示当前会话的消息记录
        addWelcomeMessageIfNeeded()         // 如果是新会话，添加欢迎消息
        updateCurrentFileStatus()           // 更新当前文件状态显示
    }

    /**
     * 设置用户界面布局
     * 使用BorderLayout进行整体布局管理
     */
    private fun setupUI() {
        // 状态面板 - 显示当前编辑的文件状态
        val statusPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)
            background = JBColor.PanelBackground
            val refreshButton = JButton("🔄").apply {
                addActionListener { updateCurrentFileStatus() }
                toolTipText = "refresh"
                border = JBUI.Borders.empty(2)
            }

            add(refreshButton, BorderLayout.WEST)   // 刷新按钮放在左侧
            add(currentFileLabel, BorderLayout.EAST) // 文件状态标签放在右侧
        }

        // 底部操作面板 - 包含历史按钮和文件上传按钮
        val bottomActionPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            border = JBUI.Borders.empty(5)
            add(sessionManager.toggleHistoryButton)
            add(JButton("Upload Documents").apply {
                addActionListener { fileUploadManager.uploadDocuments() }
            })
        }

        // 输入面板 - 用户输入区域
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5)
            add(inputField, BorderLayout.CENTER)
            add(sendButton, BorderLayout.EAST)
        }

        // 底部面板 - 整合所有底部组件
        val bottomPanel = JPanel(BorderLayout()).apply {
            add(statusPanel, BorderLayout.NORTH)
            add(bottomActionPanel, BorderLayout.WEST)
            add(fileUploadManager.createUploadPanel(), BorderLayout.EAST)
            add(inputPanel, BorderLayout.SOUTH)
        }

        // 主聊天内容面板 - 包含聊天区域和底部面板
        val chatContentPanel = JPanel(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
            add(bottomPanel, BorderLayout.SOUTH)
        }

        // 主面板容器 - 整合会话面板和聊天内容面板
        val mainPanel = JPanel(BorderLayout()).apply {
            add(sessionManager.createSessionPanel(), BorderLayout.WEST)
            add(chatContentPanel, BorderLayout.CENTER)
        }

        add(mainPanel, BorderLayout.CENTER)  // 将主面板添加到当前面板中心
    }

    /**
     * 设置事件监听器
     */
    private fun setupEventListeners() {
        sendButton.addActionListener { sendMessage() }  // 发送按钮点击事件
        inputField.addActionListener { sendMessage() }  // 输入框回车事件
    }



    /**
     * 发送消息处理
     * 处理用户输入，调用AI服务，并更新界面显示
     */
    fun sendMessage() {
        val question = inputField.text.trim()  // 获取并清理用户输入
        val currentSession = chatHistoryManager.getCurrentSession()  // 获取当前会话

        // 验证输入和会话状态
        if (question.isNotEmpty() && currentSession != null) {
            val userMessage = ChatMessage("You", question)  // 创建用户消息对象
            val sessionId = currentSession.id               // 获取会话ID

            // 添加到历史记录并显示在界面上
            chatHistoryManager.addMessage(sessionId, userMessage)
            appendMessageToUI(userMessage)
            inputField.text = ""  // 清空输入框

            // 发送前更新文件状态
            updateCurrentFileStatus()

            // 在后台线程中处理AI问答，避免阻塞UI
            Thread {
                try {
                    // 先处理待处理文件（如果有的话），并等待处理完成
                    // 这样可以确保检索时文件已经处理完成，避免竞态条件
                    val fileProcessingLatch = fileUploadManager.processPendingFiles()
                    if (fileProcessingLatch != null) {
                        // 等待文件处理完成（最多等待60秒，避免无限等待）
                        val completed = fileProcessingLatch.await(60, java.util.concurrent.TimeUnit.SECONDS)
                        if (!completed) {
                            // 如果超时，提示用户文件仍在处理中，但继续回答（可能无法检索到新文件）
                            SwingUtilities.invokeLater {
                                val timeoutMessage = ChatMessage("System",
                                    "⚠️ File processing is taking longer than expected. Answering based on previously processed files.")
                                chatHistoryManager.addMessage(sessionId, timeoutMessage)
                                appendMessageToUI(timeoutMessage)
                            }
                        }
                    }

                    val answer = chatService.askQuestion(sessionId, question, project)  // 调用AI服务，传入会话ID以实现会话隔离
                    SwingUtilities.invokeLater {
                        val aiMessage = ChatMessage("AI", answer)  // 创建AI回复消息
                        chatHistoryManager.addMessage(sessionId, aiMessage)  // 保存到历史
                        appendMessageToUI(aiMessage)  // 显示在界面上
                    }
                } catch (e: Exception) {
                    // 错误处理：显示错误信息
                    SwingUtilities.invokeLater {
                        val errorMessage = ChatMessage("AI", "Error: ${e.message}")
                        chatHistoryManager.addMessage(sessionId, errorMessage)
                        appendMessageToUI(errorMessage)
                    }
                }
            }.start()
        }
    }

    /**
     * 将消息追加到聊天界面显示
     * @param message 要显示的消息对象
     */
    fun appendMessageToUI(message: ChatMessage) {

        val formattedMessage = " ${message.sender}: ${message.content}\n\n"
        chatArea.append(formattedMessage)  // 追加到聊天区域
        chatArea.caretPosition = chatArea.document.length  // 自动滚动到最新消息
    }

    /**
     * 显示当前会话的所有消息
     */
    fun displayCurrentSessionMessages() {
        chatArea.text = ""  // 清空当前显示
        val currentSession = chatHistoryManager.getCurrentSession()
        // 遍历当前会话的所有消息并显示
        currentSession?.messages?.forEach { message ->
            appendMessageToUI(message)
        }
    }

    /**
     * 如果需要，添加欢迎消息（新会话时）
     */
    private fun addWelcomeMessageIfNeeded() {
        val currentSession = chatHistoryManager.getCurrentSession()
        // 检查是否是空会话（新创建的会话）
        if (currentSession?.messages?.isEmpty() == true) {
            val welcomeMessage = ChatMessage("AI", "Hello! I'm your Java Enterprise Course TA. How can I help you today?")
            chatHistoryManager.addMessage(currentSession.id, welcomeMessage)
            appendMessageToUI(welcomeMessage)
        }
    }

    // ==================== 文件状态相关方法 ====================

    /**
     * 更新当前文件状态显示
     * 显示当前在IDE中编辑的文件名
     */
    fun updateCurrentFileStatus() {
        val currentFile = getCurrentFile()
        if (currentFile != null) {
            currentFileLabel.text = "current file: $currentFile"  // 显示文件名
            currentFileLabel.foreground = JBColor.BLUE           // 蓝色表示有文件
        } else {
            currentFileLabel.text = "current file: none"         // 显示无文件
            currentFileLabel.foreground = JBColor.GRAY           // 灰色表示无文件
        }
    }

    /**
     * 获取当前在IDE中编辑的文件名
     * @return 文件名或null（如果没有打开的文件）
     */
    private fun getCurrentFile(): String? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFiles = fileEditorManager.selectedFiles  // 获取当前选中的文件
        return selectedFiles.firstOrNull()?.name             // 返回第一个文件的名称
    }

    // ==================== 公共访问方法 ====================

    /**
     * 获取聊天区域组件
     */
    fun getChatArea(): JTextArea = chatArea

    /**
     * 获取输入框组件
     */
    fun getInputField(): JTextField = inputField

    /**
     * 获取项目实例
     */
    fun getProject(): Project = project

    /**
     * 获取聊天历史管理器
     */
    fun getChatHistoryManager(): ChatHistoryManager = chatHistoryManager

    /**
     * 清理指定会话的向量存储
     * 在删除会话时调用，用于清理该会话的文档向量存储
     * @param sessionId 要清理的会话ID
     */
    fun clearSessionVectorStore(sessionId: String) {
        chatService.removeSessionVectorStore(sessionId)
    }

    /**
     * 设置输入框文本（供外部调用）
     * @param text 要设置的文本
     */
    fun setInputText(text: String) {
        inputField.text = text
        inputField.requestFocusInWindow()  // 获取焦点
        inputField.selectAll()             // 全选文本，方便修改
    }

    /**
     * 插入代码到输入框（供外部调用）
     * 用于从编辑器中选择代码后快速插入到聊天输入框
     * @param selectedCode 选中的代码文本
     */
    fun insertCodeToInput(selectedCode: String) {
        val currentText = inputField.text
        val codeBlock = "\n\nRegarding this code:\n```java\n$selectedCode\n```\n"  // 代码块格式

        if (currentText.isBlank()) {
            // 如果输入框为空，使用默认提问模板
            inputField.text = "Can you explain this code?$codeBlock"
        } else {
            // 如果已有内容，追加代码块
            inputField.text = "$currentText$codeBlock"
        }

        inputField.requestFocusInWindow()  // 获取焦点
        inputField.caretPosition = inputField.text.length  // 光标移动到末尾
    }
}