package com.github.dyz111ai.javaassigment1.chat

import com.github.dyz111ai.javaassigment1.llm.LLMService
import com.github.dyz111ai.javaassigment1.rag.DocumentProcessor
import com.github.dyz111ai.javaassigment1.rag.VectorStore
import com.intellij.openapi.project.Project
import java.io.File

class ChatService {

    private val documentProcessor = DocumentProcessor()
    private val vectorStore = VectorStore()
    private val llmService = LLMService()

    fun processDocuments(files: List<File>) {
        files.forEach { file ->
            val chunks = documentProcessor.processDocument(file)
            vectorStore.addChunks(chunks)
        }
    }

    fun askQuestion(question: String, project: Project? = null): String {
        val relevantChunks = vectorStore.search(question)
        val similarityThreshold = 0.3
        val highSimilarityChunks = relevantChunks.filter { it.similarity > similarityThreshold }

        val context = if (highSimilarityChunks.isNotEmpty()) {
            buildContext(highSimilarityChunks)
        } else {
            "没有找到相关的课程材料。请基于你的通用知识回答问题，并建议用户上传相关材料以获得更准确的答案。"
        }

        val enhancedPrompt = """
        ${if (highSimilarityChunks.isEmpty()) "注意：没有找到相关的课程材料，请基于通用知识回答。" else "以下是相关课程材料："}
        
        $context
        
        问题：$question
        
        请回答用户的问题。如果使用了课程材料请注明来源，如果是基于通用知识请说明。
    """.trimIndent()

        val answer = llmService.generateResponse(enhancedPrompt, question)

        return if (highSimilarityChunks.isNotEmpty()) {
            formatAnswerWithCitations(answer, highSimilarityChunks)
        } else {
            "🤖 ${answer}"
        }
    }

    private fun buildContext(chunks: List<com.github.dyz111ai.javaassigment1.rag.SearchResult>): String {
        return chunks.joinToString("\n\n") { result ->
            "Source: ${result.chunk.source}" +
                    (result.chunk.page?.let { ", Page: $it" } ?: "") +
                    "\nContent: ${result.chunk.content}"
        }
    }

    private fun formatAnswerWithCitations(answer: String, chunks: List<com.github.dyz111ai.javaassigment1.rag.SearchResult>): String {
        val citation = "\n\n---\n*Sources referenced:* " +
                chunks.joinToString("; ") {
                    "${it.chunk.source}" +
                            (it.chunk.page?.let { page -> " (Page $page)" } ?: "")
                }

        return answer + citation
    }
    fun askQuestionWithCodeContext(question: String, codeSnippet: String, project: Project? = null): String {
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

        val answer = llmService.generateResponse(enhancedPrompt, question)

        return if (relevantChunks.isNotEmpty()) {
            formatAnswerWithCitations(answer, relevantChunks)
        } else {
            "👨‍💻 **Code Analysis**:\n\n$answer"
        }
    }
    fun clearKnowledgeBase() {
        vectorStore.clear()
    }
}