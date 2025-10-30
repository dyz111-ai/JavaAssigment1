package com.github.dyz111ai.javaassigment1.chat

data class ChatMessage(
    val sender: String, // "You" or "AI" or "System"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val messages: MutableList<ChatMessage>
)