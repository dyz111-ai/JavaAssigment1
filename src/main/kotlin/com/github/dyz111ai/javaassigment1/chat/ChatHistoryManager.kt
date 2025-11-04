package com.github.dyz111ai.javaassigment1.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.util.UUID
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.diagnostic.Logger
/**
 * 聊天历史管理器
 * 负责聊天会话的持久化存储、加载和管理
 * 使用JSON文件格式存储会话数据，支持会话的增删改查操作
 */
class ChatHistoryManager(private val project: Project) {

    // ==================== 核心属性定义 ====================

    // JSON序列化器：用于将会话对象序列化为JSON格式和反序列化
    private val mapper = ObjectMapper().registerKotlinModule()

    // 会话内存缓存：使用Map存储所有加载的会话，key为会话ID，value为会话对象
    private val sessions = mutableMapOf<String, ChatSession>()

    // 当前活跃会话ID：记录用户当前正在查看或操作的会话
    private var currentSessionId: String? = null

    // 历史记录目录：延迟初始化，存储所有会话JSON文件的目录
    private val historyDir by lazy { getHistoryDirectory() }

    /**
     * 初始化方法：加载所有已保存的会话
     */
    init {
        loadSessions()  // 从文件系统加载所有会话数据到内存
    }

    /**
     * 获取历史记录存储目录
     * 优先使用项目目录，如果不可用则使用用户主目录
     * @return 历史记录目录File对象
     */
    private fun getHistoryDirectory(): File {
        // 尝试获取项目基础路径
        val projectDir = project.basePath ?: return File(System.getProperty("user.home"), ".javaassigment1")

        // 在项目目录的.idea文件夹下创建chat_history子目录
        val dir = File(projectDir, ".idea/chat_history")
        dir.mkdirs()  // 确保目录存在，如果不存在则创建
        //println("使用项目目录: ${dir.absolutePath}")

        return dir
    }

    /**
     * 创建新的聊天会话
     * @param title 会话标题，默认为"New Chat"
     * @return 新创建的ChatSession对象
     */
    fun createNewSession(title: String = "New Chat"): ChatSession {
        // 生成唯一的会话ID
        val sessionId = UUID.randomUUID().toString()

        // 创建新的会话对象
        val session = ChatSession(
            sessionId,
            title,
            System.currentTimeMillis(),  // 使用当前时间作为创建时间戳
            mutableListOf()              // 初始为空消息列表
        )

        // 添加到内存缓存
        sessions[sessionId] = session
        // 设置为当前活跃会话
        currentSessionId = sessionId
        // 持久化到文件系统
        saveSession(session)

        return session
    }

    /**
     * 获取当前活跃的会话
     * @return 当前会话对象，如果没有则返回null
     */
    fun getCurrentSession(): ChatSession? {
        return currentSessionId?.let { sessions[it] }
    }

    /**
     * 设置当前活跃会话
     * @param sessionId 要设置为当前会话的ID
     * @return Boolean 是否设置成功（会话存在则成功）
     */
    fun setCurrentSession(sessionId: String): Boolean {
        if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            return true
        }
        return false
    }

    /**
     * 向指定会话添加消息
     * @param sessionId 目标会话ID
     * @param message 要添加的消息对象
     * @return Boolean 是否添加成功（会话存在则成功）
     */
    fun addMessage(sessionId: String, message: ChatMessage): Boolean {
        val session = sessions[sessionId]
        if (session != null) {
            session.messages.add(message)    // 添加到内存中的消息列表
            saveSession(session)             // 立即持久化到文件
            return true
        }
        return false
    }

    /**
     * 获取所有会话列表
     * 按创建时间降序排列（最新的在前面）
     * @return 排序后的会话列表
     */
    fun getAllSessions(): List<ChatSession> {
        return sessions.values.toList().sortedByDescending { it.createdAt }
    }

    /**
     * 删除指定会话
     * @param sessionId 要删除的会话ID
     * @return Boolean 是否删除成功（会话存在则成功）
     */
    fun deleteSession(sessionId: String): Boolean {
        // 从内存缓存中移除
        if (sessions.remove(sessionId) != null) {
            // 删除对应的JSON文件
            val file = File(historyDir, "${sessionId}.json")
            file.delete()
            // 添加调试信息

            // 如果删除的是当前会话，需要重新设置当前会话
            if (currentSessionId == sessionId) {
                // 设置为剩余会话中的第一个（如果有的话）
                currentSessionId = sessions.keys.firstOrNull()
            }
            return true
        }
        return false
    }

    /**
     * 重命名会话
     * @param sessionId 要重命名的会话ID
     * @param newTitle 新的会话标题
     * @return Boolean 是否重命名成功（会话存在则成功）
     */
    fun renameSession(sessionId: String, newTitle: String): Boolean {
        val session = sessions[sessionId]
        if (session != null) {
            // 创建更新后的会话对象（不可变数据，需要创建新实例）
            val updatedSession = ChatSession(
                id = session.id,
                title = newTitle,
                createdAt = session.createdAt,
                messages = session.messages
            )
            // 更新内存缓存
            sessions[sessionId] = updatedSession
            // 持久化到文件
            saveSession(updatedSession)
            return true
        }
        return false
    }

    /**
     * 从文件系统加载所有会话
     * 在初始化时调用，将会话数据从JSON文件加载到内存
     */
    private fun loadSessions() {
        sessions.clear()  // 清空现有缓存

        // 检查历史记录目录是否存在
        if (!historyDir.exists()) return

        // 遍历目录中的所有JSON文件
        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                // 从JSON文件反序列化会话对象
                val session = mapper.readValue(file, ChatSession::class.java)
                sessions[session.id] = session  // 添加到内存缓存
            } catch (e: IOException) {
                // 忽略无效的JSON文件，继续处理其他文件
                // 在实际项目中可以考虑记录日志
            }
        }

        // 如果没有任何会话，自动创建一个默认会话
        if (sessions.isEmpty()) {
            createNewSession()
        } else {
            // 设置创建时间最新的会话为当前会话
            currentSessionId = sessions.values.maxByOrNull { it.createdAt }?.id
        }
    }

    /**
     * 保存会话到文件系统
     * 将会话对象序列化为JSON格式并保存到文件
     * @param session 要保存的会话对象
     */
    private fun saveSession(session: ChatSession) {
        try {
            // 构建文件路径：{sessionId}.json
            val file = File(historyDir, "${session.id}.json")
            // 使用美化格式写入JSON文件（便于阅读和调试）
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, session)
        } catch (e: IOException) {
            // 忽略保存错误，避免应用崩溃
            // 在实际项目中应该记录错误日志
        }
    }
}