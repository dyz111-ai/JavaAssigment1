package com.github.dyz111ai.javaassigment1.rag

import java.util.*
import kotlin.math.sqrt

class VectorStore {

    private val chunks = mutableListOf<DocumentChunk>()
    private val embeddings = mutableMapOf<String, List<Double>>()

    fun addChunks(newChunks: List<DocumentChunk>) {
        chunks.addAll(newChunks)
        // 为简单起见，我们使用TF-IDF风格的简单向量化
        newChunks.forEach { chunk ->
            embeddings[chunk.content] = computeSimpleEmbedding(chunk.content)
        }
    }

    fun search(query: String, topK: Int = 3): List<SearchResult> {
        val queryEmbedding = computeSimpleEmbedding(query)

        val results = chunks.map { chunk ->
            val similarity = cosineSimilarity(queryEmbedding, embeddings[chunk.content] ?: emptyList())
            SearchResult(chunk, similarity)
        }

        return results.sortedByDescending { it.similarity }.take(topK)
    }

    private fun computeSimpleEmbedding(text: String): List<Double> {
        val words = text.toLowerCase()
            .split("\\s+".toRegex())
            .filter { it.length > 2 }

        val wordFreq = mutableMapOf<String, Double>()
        words.forEach { word ->
            wordFreq[word] = wordFreq.getOrDefault(word, 0.0) + 1.0
        }

        // 简单的归一化
        val total = wordFreq.values.sum()
        return words.distinct().map { word ->
            (wordFreq[word] ?: 0.0) / total
        }
    }

    private fun cosineSimilarity(vec1: List<Double>, vec2: List<Double>): Double {
        if (vec1.isEmpty() || vec2.isEmpty()) return 0.0

        val dotProduct = vec1.zip(vec2) { a, b -> a * b }.sum()
        val norm1 = sqrt(vec1.map { it * it }.sum())
        val norm2 = sqrt(vec2.map { it * it }.sum())

        return if (norm1 == 0.0 || norm2 == 0.0) 0.0 else dotProduct / (norm1 * norm2)
    }

    fun clear() {
        chunks.clear()
        embeddings.clear()
    }
}

data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Double
)