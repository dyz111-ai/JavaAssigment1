package com.github.dyz111ai.javaassigment1.rag

import org.apache.tika.Tika
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileInputStream

data class DocumentChunk(
    val content: String,
    val source: String,
    val page: Int? = null,
    val chunkIndex: Int
)

class DocumentProcessor {

    private val tika = Tika()
    private val chunks = mutableListOf<DocumentChunk>()

    fun processDocument(file: File): List<DocumentChunk> {
        val fileChunks = mutableListOf<DocumentChunk>()
        val fileName = file.name

        return when (file.extension.toLowerCase()) {
            "pdf" -> processPdf(file, fileName)
            "docx" -> processDocx(file, fileName)
            "ppt", "pptx" -> processPresentation(file, fileName)
            "txt" -> processText(file, fileName)
            else -> processWithTika(file, fileName)
        }


    }

    private fun processPdf(file: File, fileName: String): List<DocumentChunk> {
        val documentChunks = mutableListOf<DocumentChunk>()
        PDDocument.load(file).use { pdfDocument ->
            val stripper = PDFTextStripper()

            for (page in 1..pdfDocument.numberOfPages) {
                stripper.startPage = page
                stripper.endPage = page
                val text = stripper.getText(pdfDocument)

                if (text.trim().isNotEmpty()) {
                    val pageChunks = chunkText(text, 500) // 每块约500字符
                    pageChunks.forEachIndexed { index, chunk ->
                        documentChunks.add(DocumentChunk(
                            content = chunk,
                            source = fileName,
                            page = page,
                            chunkIndex = index
                        ))
                    }
                }
            }
        }
        return documentChunks
    }

    private fun processDocx(file: File, fileName: String): List<DocumentChunk> {
        FileInputStream(file).use { fis ->
            XWPFDocument(fis).use { doc ->
                val extractor = XWPFWordExtractor(doc)
                val text = extractor.text
                return chunkText(text, 500).mapIndexed { index, chunk ->
                    DocumentChunk(chunk, fileName, null, index)
                }
            }
        }
    }

    private fun processText(file: File, fileName: String): List<DocumentChunk> {
        val text = file.readText()
        return chunkText(text, 500).mapIndexed { index, chunk ->
            DocumentChunk(chunk, fileName, null, index)
        }
    }

    private fun processPresentation(file: File, fileName: String): List<DocumentChunk> {
        val text = tika.parseToString(file)
        return chunkText(text, 500).mapIndexed { index, chunk ->
            DocumentChunk(chunk, fileName, null, index)
        }
    }

    private fun processWithTika(file: File, fileName: String): List<DocumentChunk> {
        val text = tika.parseToString(file)
        return chunkText(text, 500).mapIndexed { index, chunk ->
            DocumentChunk(chunk, fileName, null, index)
        }
    }

    private fun chunkText(text: String, chunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        var start = 0

        while (start < text.length) {
            var end = (start + chunkSize).coerceAtMost(text.length)

            // 尝试在句子边界分割
            if (end < text.length) {
                val nextPeriod = text.indexOf('.', end - 100)
                val nextNewline = text.indexOf('\n', end - 100)

                end = when {
                    nextPeriod in end-100..end+50 -> nextPeriod + 1
                    nextNewline in end-100..end+50 -> nextNewline + 1
                    else -> end
                }
            }

            chunks.add(text.substring(start, end).trim())
            start = end
        }

        return chunks.filter { it.isNotEmpty() }
    }

    fun getAllChunks(): List<DocumentChunk> = chunks.toList()

    fun clearChunks() {
        chunks.clear()
    }
}