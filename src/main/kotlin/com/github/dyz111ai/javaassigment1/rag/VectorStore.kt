package com.github.dyz111ai.javaassigment1.rag

import java.util.*
import kotlin.math.*
import com.intellij.openapi.diagnostic.Logger

class VectorStore {
    private val logger = Logger.getInstance(VectorStore::class.java)

    // 核心数据结构
    private val chunks = mutableListOf<DocumentChunk>()
    private val embeddings = mutableMapOf<String, List<Double>>()

    // 改进：全局词汇表和IDF权重
    private val vocabulary = mutableSetOf<String>()
    private val documentFrequency = mutableMapOf<String, Int>() // 每个词出现在多少文档中
    private var totalDocuments = 0

    // 配置参数
    private val minWordLength = 2

    // 停用词列表（简化版）
    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
        "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did",
        "this", "that", "these", "those", "it", "its", "they", "them", "their"
    )

    /**
     * 添加文档块，使用改进的TF-IDF向量化
     */
    fun addChunks(newChunks: List<DocumentChunk>) {
        if (newChunks.isEmpty()) return

        logger.info("Adding ${newChunks.size} chunks to vector store")

        // 1. 更新词汇表和文档频率
        updateVocabularyAndDF(newChunks)

        // 2. 添加块并计算向量
        chunks.addAll(newChunks)
        newChunks.forEach { chunk ->
            embeddings[chunk.content] = computeTFIDFEmbedding(chunk.content)
        }

        logger.info("Vector store now contains ${chunks.size} chunks, vocabulary size: ${vocabulary.size}")
    }

    /**
     * 改进的搜索功能，支持多种搜索策略
     */
    fun search(query: String, topK: Int = 5, strategy: SearchStrategy = SearchStrategy.SEMANTIC): List<SearchResult> {
        if (chunks.isEmpty()) return emptyList()

        val queryEmbedding = computeTFIDFEmbedding(query)

        logger.debug("Searching for: '$query' with ${chunks.size} chunks available")

        val results = chunks.mapIndexed { index, chunk ->
            val chunkEmbedding = embeddings[chunk.content] ?: emptyList()
            val similarity = when (strategy) {
                SearchStrategy.SEMANTIC -> cosineSimilarity(queryEmbedding, chunkEmbedding)
                SearchStrategy.HYBRID -> {
                    val semanticScore = cosineSimilarity(queryEmbedding, chunkEmbedding)
                    val keywordScore = computeKeywordOverlap(query, chunk.content)
                    // 结合语义和关键词匹配
                    0.7 * semanticScore + 0.3 * keywordScore
                }
            }
            SearchResult(chunk, similarity, index, strategy)
        }

        val sortedResults = results.sortedWith(
            compareByDescending<SearchResult> { it.similarity }
                .thenByDescending { it.addOrderIndex }
        ).take(topK)

        // 记录搜索统计
        if (sortedResults.isNotEmpty()) {
            logger.debug("Search completed. Top result similarity: ${sortedResults.first().similarity}")
        }

        return sortedResults
    }

    /**
     * 改进的TF-IDF向量化
     */
    private fun computeTFIDFEmbedding(text: String): List<Double> {
        val words = preprocessText(text)
        if (words.isEmpty()) return List(vocabulary.size) { 0.0 }

        // 计算词频 (TF)
        val termFrequency = mutableMapOf<String, Double>()
        words.forEach { word ->
            termFrequency[word] = termFrequency.getOrDefault(word, 0.0) + 1.0
        }

        // 归一化词频
        val totalTerms = words.size.toDouble()
        termFrequency.keys.forEach { word ->
            termFrequency[word] = termFrequency[word]!! / totalTerms
        }

        // 计算TF-IDF向量
        return vocabulary.map { word ->
            val tf = termFrequency[word] ?: 0.0
            val idf = computeIDF(word)
            tf * idf
        }
    }

    /**
     * 计算逆文档频率 (IDF)
     */
    private fun computeIDF(term: String): Double {
        val df = documentFrequency[term] ?: 0
        if (df == 0) return 0.0

        return ln((totalDocuments + 1.0) / (df + 1.0)) + 1.0 // 平滑处理
    }

    /**
     * 文本预处理
     */
    private fun preprocessText(text: String): List<String> {
        return text.toLowerCase()
            .split("\\s+".toRegex())
            .map { it.replace(Regex("[^a-zA-Z0-9]"), "") } // 移除非字母数字字符
            .filter { it.length > minWordLength }
            .filter { it !in stopWords }
            .filter { it.isNotBlank() }
    }

    /**
     * 更新词汇表和文档频率
     */
    private fun updateVocabularyAndDF(newChunks: List<DocumentChunk>) {
        val chunkWords = mutableSetOf<String>()

        newChunks.forEach { chunk ->
            val words = preprocessText(chunk.content).toSet()
            chunkWords.addAll(words)

            // 更新文档频率
            words.forEach { word ->
                documentFrequency[word] = documentFrequency.getOrDefault(word, 0) + 1
            }
        }

        vocabulary.addAll(chunkWords)
        totalDocuments += newChunks.size
    }

    /**
     * 关键词重叠评分（用于混合搜索）
     */
    private fun computeKeywordOverlap(query: String, content: String): Double {
        val queryWords = preprocessText(query).toSet()
        val contentWords = preprocessText(content).toSet()

        if (queryWords.isEmpty() || contentWords.isEmpty()) return 0.0

        val intersection = queryWords.intersect(contentWords)
        return intersection.size.toDouble() / queryWords.size
    }

    /**
     * 改进的余弦相似度计算，处理稀疏向量
     */
    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        if (vec1.isEmpty() || vec2.isEmpty() || vec1.size != vec2.size) return 0.0

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            norm1 += vec1[i] * vec1[i]
            norm2 += vec2[i] * vec2[i]
        }

        norm1 = sqrt(norm1)
        norm2 = sqrt(norm2)

        return if (norm1 < 1e-10 || norm2 < 1e-10) 0.0 else {
            val similarity = dotProduct / (norm1 * norm2)
            // 确保相似度在合理范围内
            similarity.coerceIn(0.0, 1.0)
        }
    }

    /**
     * 获取向量存储统计信息
     */
    fun getStats(): VectorStoreStats {
        return VectorStoreStats(
            totalChunks = chunks.size,
            vocabularySize = vocabulary.size,
            totalDocuments = totalDocuments,
            chunkSources = chunks.map { it.source }.distinct()
        )
    }

    /**
     * 查找相似文档（基于内容相似度）
     */
    fun findSimilarDocuments(content: String, topK: Int = 3): List<SearchResult> {
        return search(content, topK, SearchStrategy.SEMANTIC)
    }

    /**
     * 按来源过滤块
     */
    fun getChunksBySource(source: String): List<DocumentChunk> {
        return chunks.filter { it.source == source }
    }

    /**
     * 清理指定来源的块
     */
    fun removeChunksBySource(source: String): Int {
        val chunksToRemove = chunks.filter { it.source == source }
        chunks.removeAll(chunksToRemove.toSet())

        // 清理对应的向量
        chunksToRemove.forEach { chunk ->
            embeddings.remove(chunk.content)
        }

        // 重新计算文档频率（简化处理）
        recalculateDocumentFrequency()

        return chunksToRemove.size
    }

    private fun recalculateDocumentFrequency() {
        documentFrequency.clear()
        totalDocuments = chunks.size

        chunks.forEach { chunk ->
            val words = preprocessText(chunk.content).toSet()
            words.forEach { word ->
                documentFrequency[word] = documentFrequency.getOrDefault(word, 0) + 1
            }
        }
    }

    fun clear() {
        chunks.clear()
        embeddings.clear()
        vocabulary.clear()
        documentFrequency.clear()
        totalDocuments = 0
        logger.info("Vector store cleared")
    }
}

/**
 * 搜索策略枚举
 */
enum class SearchStrategy {
    SEMANTIC,    // 纯语义搜索
    HYBRID       // 混合搜索（语义 + 关键词）
}

/**
 * 改进的搜索结果
 */
data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Double,
    val addOrderIndex: Int = 0,
    val strategy: SearchStrategy = SearchStrategy.SEMANTIC
) {
    /**
     * 格式化显示
     */
    fun toFormattedString(): String {
        return "Similarity: ${"%.3f".format(similarity)} | Source: ${chunk.source}${chunk.page?.let { " (Page $it)" } ?: ""}\n" +
                "Content: ${chunk.content.take(150)}..."
    }
}

/**
 * 向量存储统计信息
 */
data class VectorStoreStats(
    val totalChunks: Int,
    val vocabularySize: Int,
    val totalDocuments: Int,
    val chunkSources: List<String>
) {
    override fun toString(): String {
        return "Vector Store Stats:\n" +
                "  Total Chunks: $totalChunks\n" +
                "  Vocabulary Size: $vocabularySize\n" +
                "  Total Documents: $totalDocuments\n" +
                "  Sources: ${chunkSources.joinToString()}"
    }
}
