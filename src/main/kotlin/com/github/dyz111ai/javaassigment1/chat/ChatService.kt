package com.github.dyz111ai.javaassigment1.chat

import com.github.dyz111ai.javaassigment1.llm.LLMService
import com.github.dyz111ai.javaassigment1.rag.DocumentProcessor
import com.github.dyz111ai.javaassigment1.rag.VectorStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class ChatService {

    private val documentProcessor = DocumentProcessor()
    // 为每个会话维护独立的向量存储
    private val sessionVectorStores = mutableMapOf<String, VectorStore>()
    private val llmService = LLMService()

    /**
     * 获取或创建指定会话的向量存储
     * @param sessionId 会话ID
     * @return 该会话的VectorStore实例
     */
    private fun getOrCreateVectorStore(sessionId: String): VectorStore {
        return sessionVectorStores.getOrPut(sessionId) { VectorStore() }
    }

    /**
     * 处理上传的文档
     * 将文档分块并存储到对应会话的向量数据库中
     * @param sessionId 会话ID，用于隔离不同会话的文档
     * @param files 要处理的文件列表
     */
    fun processDocuments(sessionId: String, files: List<File>) {
        val vectorStore = getOrCreateVectorStore(sessionId)
        files.forEach { file ->
            val chunks = documentProcessor.processDocument(file)
            vectorStore.addChunks(chunks)
        }
    }

    /**
     * 回答问题的主要方法
     * 基于RAG（检索增强生成）流程：检索相关文档 → 构建上下文 → 生成回答
     * @param sessionId 会话ID，用于检索该会话的文档
     * @param question 用户问题
     * @param project 可选的项目上下文
     * @return AI生成的回答
     */
    fun askQuestion(sessionId: String, question: String, project: Project? = null): String {
        val vectorStore = getOrCreateVectorStore(sessionId)
        val relevantChunks = vectorStore.search(question)
        val similarityThreshold = 0.1
        val highSimilarityChunks = relevantChunks.filter { it.similarity > similarityThreshold }

        // 获取当前代码上下文
        val codeContext = getCurrentCodeContext(project)

        // 构建完整上下文（课程材料 + 代码上下文）
        val fullContext = buildFullContext(highSimilarityChunks, codeContext)

        val enhancedPrompt = """
        ${if (highSimilarityChunks.isEmpty()) "Note: No relevant materials found. Please answer based on general knowledge." else "Here are the relevant materials:"}
        
        $fullContext
        
        Please answer the user's question in English. If you used materials, please cite the sources. If based on general knowledge, please indicate so.
    """.trimIndent()

        val answer = llmService.generateResponse(enhancedPrompt,question)

        return if (highSimilarityChunks.isNotEmpty()) {
            //formatAnswerWithCitations(answer, highSimilarityChunks)
            "🤖 ${answer}"
        } else {
            "🤖 ${answer}"
        }
    }

    /**
     * 构建上下文字符串
     * 将检索到的文档块格式化为LLM可理解的上下文
     * @param chunks 检索到的文档块列表
     * @return 格式化的上下文字符串
     */
    private fun buildContext(chunks: List<com.github.dyz111ai.javaassigment1.rag.SearchResult>): String {
        return chunks.joinToString("\n\n") { result ->
            "Source: ${result.chunk.source}" +
                    (result.chunk.page?.let { ", Page: $it" } ?: "") +
                    "\nContent: ${result.chunk.content}"
        }
    }

    /**
     * 构建完整上下文（课程材料 + 代码上下文）
     */
    private fun buildFullContext(chunks: List<com.github.dyz111ai.javaassigment1.rag.SearchResult>, codeContext: String): String {
        val courseMaterials = if (chunks.isNotEmpty()) {
            "=== Relevant Course Materials ===\n${buildContext(chunks)}"
        } else {
            "=== Course Materials ===\nNo relevant course materials found"
        }

        return if (codeContext.isNotEmpty()) {
            "$courseMaterials\n\n=== Current Code Context ===\n$codeContext"
        } else {
            courseMaterials
        }
    }

    /**
     * 格式化带引用的回答
     * 在回答末尾添加引用来源信息
     * @param answer LLM生成的原始回答
     * @param chunks 引用的文档块列表
     * @return 带引用格式的回答
     */
//    private fun formatAnswerWithCitations(answer: String, chunks: List<com.github.dyz111ai.javaassigment1.rag.SearchResult>): String {
//        val citation = "\n\n---\nSources referenced: " +
//                chunks.joinToString("; ") {
//                    "${it.chunk.source}" +
//                            (it.chunk.page?.let { page -> " (Page $page)" } ?: "")
//                }
//
//        return answer + citation
//    }

    /**
     * 带代码上下文的提问方法
     * 专门用于分析用户选中的代码片段
     * @param sessionId 会话ID，用于检索该会话的文档
     * @param question 用户关于代码的问题
     * @param codeSnippet 选中的代码片段
     * @param project 可选的项目上下文
     * @return 针对代码分析的AI回答
     */
    fun askQuestionWithCodeContext(sessionId: String, question: String, codeSnippet: String, project: Project? = null): String {
        val vectorStore = getOrCreateVectorStore(sessionId)
        // 检索相关文档块
        val relevantChunks = vectorStore.search(question + " " + codeSnippet)

        // 构建增强的上下文
        val enhancedContext = if (relevantChunks.isNotEmpty()) {
            "Course Materials:\n${buildContext(relevantChunks)}\n\nSelected Code:\n```java\n$codeSnippet\n```"
        } else {
            "Selected Code:\n```java\n$codeSnippet\n```\n\nNo relevant course materials found."
        }

        val enhancedPrompt = """
        $enhancedContext
        
        Question: $question
        
        Please analyze the selected code in detail. Consider:
        - Code functionality and purpose
        - Programming concepts used
        - Potential improvements or issues
        - How it relates to Java enterprise development
        - Best practices recommendations
        
        If course materials are available, reference them in your answer.
    """.trimIndent()

        val answer = llmService.generateResponse(enhancedPrompt,question)

        return if (relevantChunks.isNotEmpty()) {
            "👨‍💻 **Code Analysis**:\n\n$answer"
            //formatAnswerWithCitations(answer, relevantChunks)
        } else {
            "👨‍💻 **Code Analysis**:\n\n$answer"
        }
    }

    /**
     * 清空指定会话的知识库
     * @param sessionId 要清空的会话ID
     */
    fun clearKnowledgeBase(sessionId: String) {
        sessionVectorStores[sessionId]?.clear()
    }

    /**
     * 清空所有会话的知识库
     */
    fun clearAllKnowledgeBases() {
        sessionVectorStores.values.forEach { it.clear() }
        sessionVectorStores.clear()
    }

    /**
     * 删除指定会话的向量存储（会话删除时调用）
     * @param sessionId 要删除的会话ID
     */
    fun removeSessionVectorStore(sessionId: String) {
        sessionVectorStores.remove(sessionId)
    }

    private fun getCurrentCodeContext(project: Project?): String {
        if (project == null) return ""

        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFiles = fileEditorManager.selectedFiles

        if (selectedFiles.isEmpty()) return ""

        val currentFile = selectedFiles[0]
        return try {
            val content = String(currentFile.contentsToByteArray())
            "current file: ${currentFile.name}\ncode content:\n$content"
        } catch (e: Exception) {
            "Cannot read current file"
        }
    }
}