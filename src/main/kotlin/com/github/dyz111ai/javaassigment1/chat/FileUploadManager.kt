package com.github.dyz111ai.javaassigment1.chat

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File

import java.util.*
import java.util.concurrent.CountDownLatch
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 文件上传管理类
 * 负责处理文件上传、待处理文件列表管理和文件处理逻辑
 * 包括文件选择、临时存储、UI显示和后台处理等功能
 */
class FileUploadManager(
    private val parent: ChatPanel,                  // 父级聊天面板，用于UI更新和回调
    private val chatService: ChatService,           // 聊天服务，用于处理上传的文件内容
    private val chatHistoryManager: ChatHistoryManager // 聊天历史管理器，用于保存系统消息
) {
    // ==================== 文件管理相关属性 ====================

    // 临时存储上传的文件列表：保存用户选择但尚未处理的文件
    private val pendingFiles = mutableListOf<File>()

    // 待处理文件面板：垂直排列显示所有待处理文件的UI容器
    private val pendingFilesPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)  // 垂直盒式布局，文件项从上到下排列
        border = JBUI.Borders.empty(5)              // 设置内边距
        isVisible = false                           // 初始隐藏，有文件时显示
    }

    // 文件面板的滚动容器：支持文件列表过长时滚动查看
    private lateinit var pendingFilesScrollPane: JScrollPane



    /**
     * 创建文件上传面板
     * @return 配置好的文件上传滚动面板JScrollPane
     */
    fun createUploadPanel(): JScrollPane {
        pendingFilesScrollPane = JScrollPane(pendingFilesPanel).apply {
            preferredSize = Dimension(250, 80)      // 设置面板首选尺寸
            isVisible = false                       // 初始隐藏，有文件时显示
            background = JBColor.PanelBackground    // 使用IDE面板背景色保持一致性
        }
        return pendingFilesScrollPane
    }

    /**
     * 上传文档主方法
     * 打开文件选择器，让用户选择要上传的文件
     */
    fun uploadDocuments() {
        // 创建文件选择器并配置属性
        val fileChooser = JFileChooser().apply {
            setFileSelectionMode(JFileChooser.FILES_ONLY)  // 只能选择文件，不能选择文件夹
            isMultiSelectionEnabled = true                 // 支持多选文件
        }

        // 添加文件类型过滤器，限制可选择的文件格式
        fileChooser.addChoosableFileFilter(FileNameExtensionFilter(
            "Course Documents (PDF, Word, Text, PowerPoint)",  // 过滤器描述
            "pdf", "docx", "txt", "ppt", "pptx", "doc"         // 支持的文件扩展名
        ))

        // 显示文件选择对话框并处理用户选择
        if (fileChooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            val files = fileChooser.selectedFiles  // 获取用户选择的文件数组

            if (files.isNotEmpty()) {
                addFilesToPending(files.toList())  // 将文件添加到待处理列表

                // 显示系统确认消息，告知用户文件已添加但尚未处理
                val confirmMessage = ChatMessage("System",
                    "📎 Added ${files.size} file(s) to pending upload. They will be processed when you send your next message.")

                // 将确认消息保存到聊天历史并显示
                chatHistoryManager.getCurrentSession()?.let { session ->
                    chatHistoryManager.addMessage(session.id, confirmMessage)
                    appendMessageToUI(confirmMessage)
                }
            }
        }
    }

    /**
     * 添加文件到待处理列表
     * @param files 要添加的文件列表
     */
    fun addFilesToPending(files: List<File>) {
        files.forEach { file ->
            // 检查文件是否已存在（通过绝对路径去重）
            if (pendingFiles.none { it.absolutePath == file.absolutePath }) {
                pendingFiles.add(file)              // 添加到内存列表
                addFileToPendingPanel(file)         // 添加到UI面板
            }
        }
        updatePendingFilesVisibility()  // 更新面板可见性
    }

    /**
     * 处理所有待处理文件
     * 在用户发送消息时调用，将待处理文件发送到AI服务进行处理
     * @return CountDownLatch? 如果有文件被处理，返回一个latch用于等待处理完成；否则返回null
     */
    fun processPendingFiles(): CountDownLatch? {
        // 检查是否有待处理文件
        if (pendingFiles.isEmpty()) return null

        // 复制文件列表并清空原始列表（避免并发修改）
        val filesToProcess = pendingFiles.toList()
        pendingFiles.clear()
        pendingFilesPanel.removeAll()           // 清空UI面板
        updatePendingFilesVisibility()          // 更新面板可见性

        // 创建CountDownLatch，用于同步等待文件处理完成
        val latch = CountDownLatch(1)

        // 在后台线程中处理文件，避免阻塞UI
        Thread {
            try {
                // 获取当前会话ID
                val currentSession = chatHistoryManager.getCurrentSession()
                if (currentSession == null) {
                    SwingUtilities.invokeLater {
                        val errorMessage = ChatMessage("System",
                            "❌ No active session. Please create a session first.")
                        chatHistoryManager.getCurrentSession()?.let { session ->
                            chatHistoryManager.addMessage(session.id, errorMessage)
                            appendMessageToUI(errorMessage)
                        }
                    }
                    latch.countDown()  // 即使出错也要释放latch
                    return@Thread
                }
                
                // 调用聊天服务处理文档，传入会话ID以实现会话隔离
                chatService.processDocuments(currentSession.id, filesToProcess)

                // 在UI线程中更新界面（Swing线程安全要求）
                SwingUtilities.invokeLater {
                    // 显示处理成功的系统消息
                    val systemMessage = ChatMessage("System",
                        "✅ Successfully processed ${filesToProcess.size} document(s)")

                    chatHistoryManager.getCurrentSession()?.let { session ->
                        chatHistoryManager.addMessage(session.id, systemMessage)
                        appendMessageToUI(systemMessage)
                    }

                    // 为每个处理的文件显示详细信息
                    filesToProcess.forEach { file ->
                        val fileMessage = ChatMessage("System", "   📄 ${file.name}")
                        chatHistoryManager.getCurrentSession()?.let { session ->
                            chatHistoryManager.addMessage(session.id, fileMessage)
                            appendMessageToUI(fileMessage)
                        }
                    }
                }
            } catch (e: Exception) {
                // 处理过程中发生错误，显示错误信息
                SwingUtilities.invokeLater {
                    val errorMessage = ChatMessage("System",
                        "❌ Error processing documents: ${e.message}")

                    chatHistoryManager.getCurrentSession()?.let { session ->
                        chatHistoryManager.addMessage(session.id, errorMessage)
                        appendMessageToUI(errorMessage)
                    }
                }
            } finally {
                // 无论成功或失败，都要释放latch，表示处理完成
                latch.countDown()
            }
        }.start()

        return latch  // 返回latch，调用者可以等待处理完成
    }

    /**
     * 添加单个文件到待处理文件面板
     * 创建文件项UI，包含文件名和删除按钮
     * @param file 要添加的文件对象
     */
    private fun addFileToPendingPanel(file: File) {
        // 创建单个文件项的面板
        val filePanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2)          // 设置细边框
            background = JBColor.border()           // 使用边框颜色作为背景，突出显示

            // 文件名标签：显示文件图标和名称
            val fileNameLabel = JLabel("📄 ${file.name}").apply {
                border = JBUI.Borders.empty(2, 5)   // 设置标签内边距
            }

            // 删除按钮：允许用户取消单个文件的上传
            val deleteButton = JButton("❌").apply {
                preferredSize = Dimension(30, 20)   // 设置按钮固定尺寸
                addActionListener {
                    removePendingFile(file)         // 从内存列表中移除文件

                    // 通过按钮找到父面板并从UI中移除
                    val parentPanel = this@apply.parent as? JPanel
                    parentPanel?.let { panel ->
                        pendingFilesPanel.remove(panel)     // 从面板中移除文件项
                        pendingFilesPanel.revalidate()      // 重新验证布局
                        pendingFilesPanel.repaint()         // 重绘面板
                        updatePendingFilesVisibility()      // 更新可见性
                    }
                }
            }

            // 将标签和按钮添加到文件面板
            add(fileNameLabel, BorderLayout.CENTER)  // 文件名占据中间主要空间
            add(deleteButton, BorderLayout.EAST)     // 删除按钮放在右侧
        }

        // 将文件面板添加到主待处理文件面板
        pendingFilesPanel.add(filePanel)
        pendingFilesPanel.revalidate()  // 重新验证布局，确保新组件正确显示
        pendingFilesPanel.repaint()     // 重绘面板，刷新显示
    }

    /**
     * 从待处理列表中移除文件
     * @param file 要移除的文件对象
     */
    private fun removePendingFile(file: File) {
        // 通过绝对路径匹配并移除文件（确保准确匹配）
        pendingFiles.removeAll { it.absolutePath == file.absolutePath }
    }

    /**
     * 更新待处理文件面板的可见性
     * 根据是否有待处理文件来决定显示或隐藏面板
     */
    private fun updatePendingFilesVisibility() {
        val hasPendingFiles = pendingFiles.isNotEmpty()  // 检查是否有待处理文件

        // 更新文件面板可见性
        pendingFilesPanel.isVisible = hasPendingFiles

        // 如果滚动面板已初始化，更新其可见性
        if (::pendingFilesScrollPane.isInitialized) {
            pendingFilesScrollPane.isVisible = hasPendingFiles
        }

        // 强制父容器重新布局和重绘
        parent.revalidate()
        parent.repaint()
    }

    /**
     * 将消息追加到聊天界面显示
     * 用于显示文件上传相关的系统消息
     * @param message 要显示的消息对象
     */
    fun appendMessageToUI(message: ChatMessage) {

        val formattedMessage = " ${message.sender}: ${message.content}\n\n"
        parent.getChatArea().append(formattedMessage)                   // 追加到聊天区域
        parent.getChatArea().caretPosition = parent.getChatArea().document.length  // 自动滚动到底部
    }
}