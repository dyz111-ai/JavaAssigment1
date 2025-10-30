package com.github.dyz111ai.javaassigment1.chat

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.intellij.openapi.project.Project
import java.io.File
import java.io.IOException
import java.util.UUID

class ChatHistoryManager(private val project: Project) {
    private val mapper = ObjectMapper().registerKotlinModule()
    private val sessions = mutableMapOf<String, ChatSession>()
    private var currentSessionId: String? = null
    private val historyDir by lazy { getHistoryDirectory() }

    init {
        loadSessions()
    }

    private fun getHistoryDirectory(): File {
        val projectDir = project.basePath ?: return File(System.getProperty("user.home"), ".javaassigment1")
        val dir = File(projectDir, ".idea/chat_history")
        dir.mkdirs()
        return dir
    }

    fun createNewSession(title: String = "New Chat"): ChatSession {
        val sessionId = UUID.randomUUID().toString()
        val session = ChatSession(sessionId, title, System.currentTimeMillis(), mutableListOf())
        sessions[sessionId] = session
        currentSessionId = sessionId
        saveSession(session)
        return session
    }

    fun getCurrentSession(): ChatSession? {
        return currentSessionId?.let { sessions[it] }
    }

    fun setCurrentSession(sessionId: String): Boolean {
        if (sessions.containsKey(sessionId)) {
            currentSessionId = sessionId
            return true
        }
        return false
    }

    fun addMessage(sessionId: String, message: ChatMessage): Boolean {
        val session = sessions[sessionId]
        if (session != null) {
            session.messages.add(message)
            saveSession(session)
            return true
        }
        return false
    }

    fun getAllSessions(): List<ChatSession> {
        return sessions.values.toList().sortedByDescending { it.createdAt }
    }

    fun deleteSession(sessionId: String): Boolean {
        if (sessions.remove(sessionId) != null) {
            val file = File(historyDir, "${sessionId}.json")
            file.delete()
            if (currentSessionId == sessionId) {
                currentSessionId = sessions.keys.firstOrNull()
            }
            return true
        }
        return false
    }

    fun renameSession(sessionId: String, newTitle: String): Boolean {
        val session = sessions[sessionId]
        if (session != null) {
            val updatedSession = ChatSession(
                id = session.id,
                title = newTitle,
                createdAt = session.createdAt,
                messages = session.messages
            )
            sessions[sessionId] = updatedSession
            saveSession(updatedSession)
            return true
        }
        return false
    }

    private fun loadSessions() {
        sessions.clear()
        if (!historyDir.exists()) return

        historyDir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
            try {
                val session = mapper.readValue(file, ChatSession::class.java)
                sessions[session.id] = session
            } catch (e: IOException) {
                // Ignore invalid files
            }
        }

        if (sessions.isEmpty()) {
            createNewSession()
        } else {
            currentSessionId = sessions.values.maxByOrNull { it.createdAt }?.id
        }
    }

    private fun saveSession(session: ChatSession) {
        try {
            val file = File(historyDir, "${session.id}.json")
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, session)
        } catch (e: IOException) {
            // Ignore save errors
        }
    }
}