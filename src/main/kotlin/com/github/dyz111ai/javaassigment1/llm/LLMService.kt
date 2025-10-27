package com.github.dyz111ai.javaassigment1.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    val role: String,
    val content: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatRequest(
    val model: String = "qwen-plus",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 2000,
    val stream: Boolean = false
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatResponse(
    val choices: List<Choice>,
    val usage: Usage? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(
    val message: ChatMessage,
    val index: Int? = null,
    val finish_reason: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

class LLMService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val mapper = jacksonObjectMapper()
    private val mediaType = "application/json".toMediaType()

    private val apiKey = "sk-d59b79124cdc459a80359aa66b0f9500"
    private val apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"

    fun generateResponse(context: String, question: String): String {
        val prompt = """
            课程材料：$context
            问题：$question
            基于课程材料回答问题。
        """.trimIndent()

        val messages = listOf(
            ChatMessage("user", prompt)
        )

        val request = ChatRequest(
            model = "qwen-plus",
            messages = messages,
            temperature = 0.3,
            max_tokens = 2000
        )

        return try {
            val response = makeApiCall(request)
            response.choices.firstOrNull()?.message?.content ?: "API返回空响应"
        } catch (e: Exception) {
            "API调用失败: ${e.message}"
        }
    }

    private fun makeApiCall(request: ChatRequest): ChatResponse {
        val body = RequestBody.create(mediaType, mapper.writeValueAsString(request))

        val httpRequest = Request.Builder()
            .url(apiUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body!!.string()

            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: $responseBody")
            }

            return mapper.readValue(responseBody)
        }
    }

    fun testConnection(): String {
        return try {
            val testRequest = ChatRequest(
                model = "qwen-turbo",
                messages = listOf(ChatMessage("user", "test")),
                max_tokens = 10
            )
            val response = makeApiCall(testRequest)
            "连接成功"
        } catch (e: Exception) {
            "连接失败: ${e.message}"
        }
    }
}