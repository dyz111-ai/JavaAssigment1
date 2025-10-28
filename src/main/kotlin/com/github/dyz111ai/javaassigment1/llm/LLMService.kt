package com.github.dyz111ai.javaassigment1.llm

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import java.io.IOException

/**
 * 聊天消息数据类
 * 表示与LLM API交互的单条消息
 * @property role 消息角色："user"（用户）、"assistant"（助手）、"system"（系统）
 * @property content 消息内容
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * 聊天请求数据类
 * 封装调用LLM API所需的请求参数
 * @property model 使用的模型名称，默认为"qwen-plus"
 * @property messages 消息历史列表
 * @property temperature 生成温度，控制随机性（0.0-1.0），默认0.3
 * @property max_tokens 最大生成token数，默认2000
 * @property stream 是否使用流式传输，默认false
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatRequest(
    val model: String = "qwen-plus",
    val messages: List<ChatMessage>,
    val temperature: Double = 0.3,
    val max_tokens: Int = 2000,
    val stream: Boolean = false
)


/**
 * 聊天响应数据类
 * 封装LLM API返回的响应数据
 * @property choices 生成的候选回答列表
 * @property usage token使用统计信息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatResponse(
    val choices: List<Choice>,
    val usage: Usage? = null
)


/**
 * 候选回答数据类
 * 表示API返回的一个候选回答
 * @property message 生成的聊天消息
 * @property index 候选回答的索引
 * @property finish_reason 生成结束原因
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(
    val message: ChatMessage,
    val index: Int? = null,
    val finish_reason: String? = null
)

/**
 * Token使用统计数据类
 * 记录API调用的token消耗情况
 * @property prompt_tokens 提示消耗的token数
 * @property completion_tokens 生成内容消耗的token数
 * @property total_tokens 总token数
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

/**
 * LLM服务类
 * 负责与大型语言模型API进行交互，生成回答
 * 使用阿里云通义千问API作为后端服务
 */
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

    /**
     * 生成回答的主要方法
     * 基于提供的上下文和问题，调用LLM API生成回答
     * @param context 相关上下文信息（如检索到的文档内容）
     * @param question 用户提出的问题
     * @return LLM生成的回答字符串，如果调用失败则返回错误信息
     */
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

    /**
     * 执行API调用的私有方法
     * 负责构建HTTP请求、发送请求并解析响应
     * @param request 聊天请求对象
     * @return 解析后的聊天响应对象
     * @throws IOException 当HTTP请求失败或响应解析出错时抛出
     */
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