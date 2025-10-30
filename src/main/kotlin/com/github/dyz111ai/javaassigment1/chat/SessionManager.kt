package com.github.dyz111ai.javaassigment1.chat

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.*
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * 会话管理类
 * 负责管理聊天会话的创建、删除、切换和显示
 * 包括会话列表的UI展示和用户交互处理
 */
class SessionManager(
    private val parent: ChatPanel,              // 父级聊天面板，用于回调和方法调用
    private val chatHistoryManager: ChatHistoryManager // 聊天历史管理器，负责数据持久化
) {
    // ==================== 会话管理UI组件定义 ====================

    // 历史按钮：用于显示/隐藏会话面板
    val toggleHistoryButton = JButton("History")

    // 会话列表数据模型：存储会话显示字符串
    private val sessionListModel = DefaultListModel<String>()

    // 会话列表UI组件：显示所有会话
    private val sessionList = JList(sessionListModel)

    // 会话列表滚动面板：包装会话列表，支持滚动
    private val sessionScrollPane = JScrollPane(sessionList)

    // 会话主面板：包含会话按钮和列表的容器
    private val sessionPanel = JPanel(BorderLayout()).apply {
        preferredSize = Dimension(200, -1)      // 设置面板首选宽度为200px，高度自适应
        border = JBUI.Borders.empty(5)          // 设置内边距
        isVisible = false                       // 默认隐藏，点击历史按钮后显示
    }

    // 功能按钮
    private val newChatButton = JButton("New")          // 新建会话按钮
    private val deleteChatButton = JButton("Delete")    // 删除会话按钮

    /**
     * 初始化方法：设置会话组件和事件监听器
     */
    init {
        setupSessionComponents()    // 初始化会话UI组件
        setupEventListeners()       // 设置事件监听器
    }

    /**
     * 创建并返回会话面板
     * @return 配置好的会话面板JPanel
     */
    fun createSessionPanel(): JPanel {
        return sessionPanel
    }

    /**
     * 设置会话UI组件布局和属性
     */
    private fun setupSessionComponents() {
        // 会话按钮面板：包含新建和删除按钮
        val sessionButtonPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(newChatButton)      // 添加新建会话按钮
            add(deleteChatButton)   // 添加删除会话按钮
        }

        // 将按钮面板和会话列表添加到主面板
        sessionPanel.add(sessionButtonPanel, BorderLayout.NORTH)   // 按钮面板放在顶部
        sessionPanel.add(sessionScrollPane, BorderLayout.CENTER)   // 会话列表放在中间

        // 设置会话列表属性
        sessionList.selectionMode = ListSelectionModel.SINGLE_SELECTION  // 设置为单选模式
        sessionList.addListSelectionListener(SessionSelectionListener()) // 添加选择监听器
    }

    /**
     * 设置按钮事件监听器
     */
    private fun setupEventListeners() {
        // 历史按钮：切换会话面板的显示/隐藏
        toggleHistoryButton.addActionListener { toggleHistoryPanel() }

        // 新建会话按钮：创建新的聊天会话
        newChatButton.addActionListener { createNewChat() }

        // 删除会话按钮：删除选中的会话
        deleteChatButton.addActionListener { deleteSelectedChat() }
    }

    /**
     * 切换会话面板的显示状态
     * 点击历史按钮时在显示和隐藏之间切换
     */
    fun toggleHistoryPanel() {
        sessionPanel.isVisible = !sessionPanel.isVisible  // 切换可见状态
        parent.revalidate()  // 重新验证布局
        parent.repaint()     // 重绘界面
    }

    /**
     * 加载会话列表到UI
     * 从聊天历史管理器中获取所有会话并显示在列表中
     */
    fun loadSessionList() {
        sessionListModel.clear()  // 清空当前列表

        val sessions = chatHistoryManager.getAllSessions()  // 获取所有会话

        // 遍历所有会话，格式化显示信息
        sessions.forEach { session ->
            // 格式化会话创建时间：月/日 时:分
            val formattedTime = SimpleDateFormat("MM/dd HH:mm").format(Date(session.createdAt))
            // 添加到列表模型：会话标题 (创建时间)
            sessionListModel.addElement("${session.title} ($formattedTime)")
        }

        // 选中当前活跃的会话
        val currentSession = chatHistoryManager.getCurrentSession()
        if (currentSession != null) {
            // 查找当前会话在列表中的索引位置
            val index = sessions.indexOfFirst { it.id == currentSession.id }
            if (index >= 0) {
                sessionList.selectedIndex = index  // 设置选中状态
            }
        }
    }

    /**
     * 创建新的聊天会话
     * 弹出对话框让用户输入会话标题，然后创建新会话
     */
    fun createNewChat() {
        // 弹出输入对话框获取会话标题
        val sessionTitle = JOptionPane.showInputDialog(
            parent,                   // 父组件
            "Enter chat title:",      // 提示信息
            "New Chat",               // 对话框标题
            JOptionPane.QUESTION_MESSAGE  // 消息类型
        )

        // 如果用户输入了标题（没有取消）
        if (sessionTitle != null) {
            // 创建新会话，如果标题为空则使用默认标题
            val session = chatHistoryManager.createNewSession(sessionTitle.ifEmpty { "New Chat" })

            loadSessionList()                     // 重新加载会话列表
            parent.displayCurrentSessionMessages() // 显示新会话的消息（空）

            // 选中新创建的会话（最后一个）
            val index = sessionListModel.size - 1
            if (index >= 0) {
                sessionList.selectedIndex = index
            }
        }
    }

    /**
     * 删除选中的会话
     * 弹出确认对话框，确认后删除选中的会话
     */
    fun deleteSelectedChat() {
        val selectedIndex = sessionList.selectedIndex  // 获取选中的索引

        // 检查是否有选中的会话
        if (selectedIndex >= 0) {
            val sessions = chatHistoryManager.getAllSessions()

            // 验证索引有效性
            if (selectedIndex < sessions.size) {
                val sessionId = sessions[selectedIndex].id  // 获取会话ID

                // 弹出确认对话框
                val confirm = JOptionPane.showConfirmDialog(
                    parent,
                    "Are you sure you want to delete this chat?",  // 确认消息
                    "Confirm Delete",                             // 对话框标题
                    JOptionPane.YES_NO_OPTION                     // 是/否选项
                )

                // 如果用户确认删除
                if (confirm == JOptionPane.YES_OPTION) {
                    // 执行删除操作
                    if (chatHistoryManager.deleteSession(sessionId)) {
                        loadSessionList()                     // 重新加载会话列表
                        parent.displayCurrentSessionMessages() // 刷新消息显示
                    }
                }
            }
        }
    }

    /**
     * 会话选择监听器内部类
     * 处理用户在会话列表中的选择变化
     */
    private inner class SessionSelectionListener : ListSelectionListener {
        /**
         * 当会话列表选择发生变化时调用
         * @param e 列表选择事件
         */
        override fun valueChanged(e: ListSelectionEvent) {
            // 确保不是调整过程中的事件，并且有选中的项目
            if (!e.valueIsAdjusting && sessionList.selectedIndex >= 0) {
                val sessions = chatHistoryManager.getAllSessions()

                // 验证选中索引的有效性
                if (sessionList.selectedIndex < sessions.size) {
                    val session = sessions[sessionList.selectedIndex]

                    // 切换到选中的会话
                    if (chatHistoryManager.setCurrentSession(session.id)) {
                        parent.displayCurrentSessionMessages()  // 显示该会话的消息
                    }
                }
            }
        }
    }
}