package com.github.dyz111ai.javaassigment1.git

import com.github.dyz111ai.javaassigment1.llm.LLMService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import git4idea.commands.GitCommand
import git4idea.repo.GitRepositoryManager
import java.nio.charset.StandardCharsets

class CommitMessageService {

    private val logger: Logger = Logger.getInstance(CommitMessageService::class.java)
    private val llmService = LLMService()

    fun generateCommitMessage(project: Project): String {
        val diff = collectStagedDiff(project)
        if (diff.isBlank()) {
            return "未发现已暂存(staged)的变更。请先暂存需要提交的文件。"
        }

        val prompt = buildPrompt(diff)
        // 复用现有 LLMService：context 放入变更，question 作为具体指令
        val question = "请基于以上diff语义，生成符合 Conventional Commits 的提交信息。"
        val result = llmService.generateResponse(prompt, question)
        return postProcess(result)
    }

    /**
     * 生成逐文件的中文改动分析，便于用户理解改动语义。
     */
    fun generateDiffAnalysis(project: Project): String {
        val perFileDiffs = collectPerFileStagedDiffs(project)
        if (perFileDiffs.isEmpty()) {
            return "未发现已暂存(staged)的变更。请先暂存需要提交的文件。"
        }

        val analysisPrompt = buildPerFileAnalysisPrompt(perFileDiffs)
        val question = "请输出每个文件改动的中文分析与建议测试点。"
        val result = llmService.generateResponse(analysisPrompt, question)
        return postProcess(result)
    }

    private fun collectStagedDiff(project: Project): String {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repository = repoManager.repositories.firstOrNull() ?: return ""

        return try {
            val handler = GitLineHandler(project, repository.root, GitCommand.DIFF)
            handler.addParameters("--staged")
            handler.addParameters("-U3") // 上下文
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                result.output.joinToString("\n")
            } else {
                logger.warn("git diff --staged 失败: ${result.errorOutputAsJoinedString}")
                ""
            }
        } catch (e: Exception) {
            logger.warn("收集staged diff异常", e)
            ""
        }
    }

    private fun collectPerFileStagedDiffs(project: Project): Map<String, String> {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repository = repoManager.repositories.firstOrNull() ?: return emptyMap()

        return try {
            // 1) 获取已暂存的文件列表
            val nameOnlyHandler = GitLineHandler(project, repository.root, GitCommand.DIFF)
            nameOnlyHandler.addParameters("--staged", "--name-only")
            val namesResult = Git.getInstance().runCommand(nameOnlyHandler)
            if (!namesResult.success()) return emptyMap()

            val files = namesResult.output.filter { it.isNotBlank() }
            if (files.isEmpty()) return emptyMap()

            // 2) 针对每个文件获取带上下文的diff
            val perFile = LinkedHashMap<String, String>()
            for (path in files) {
                val fileDiffHandler = GitLineHandler(project, repository.root, GitCommand.DIFF)
                fileDiffHandler.addParameters("--staged", "-U3", "--", path)
                val fileDiffResult = Git.getInstance().runCommand(fileDiffHandler)
                if (fileDiffResult.success()) {
                    perFile[path] = fileDiffResult.output.joinToString("\n")
                } else {
                    logger.warn("获取文件diff失败: $path -> ${fileDiffResult.errorOutputAsJoinedString}")
                }
            }
            perFile
        } catch (e: Exception) {
            logger.warn("收集逐文件staged diff异常", e)
            emptyMap()
        }
    }

    private fun buildPrompt(diff: String): String {
        return """
        你是资深软件工程师，请基于以下 Git 已暂存变更生成“中文”的高质量提交信息，遵循 Conventional Commits 规范。

        变更 diff（可能很长）：
        ```diff
        $diff
        ```

        要求：
        - 识别语义意图（feat/fix/refactor/docs/test/chore 等，type 请用英文标准词）；
        - 如可识别模块或目录，提供合理 scope（可省略）；
        - subject 使用中文，≤ 50 字，祈使句语气，首字母小写，末尾不加句号；
        - body 使用中文要点列举关键修改点（每条一行、简洁明确，必要时可省略）；
        - 存在破坏性改动时，添加 footer：BREAKING CHANGE: <中文说明>；
        - 严格输出如下结构（若 body/footer 为空则省略对应段落）：
          <type>(<scope>): <subject>

          - <body item 1>
          - <body item 2>

          BREAKING CHANGE: <说明>

        仅输出提交信息本身，不要添加多余解释或前后缀。
        """.trimIndent()
    }

    private fun buildPerFileAnalysisPrompt(perFileDiffs: Map<String, String>): String {
        val content = perFileDiffs.entries.joinToString("\n\n") { (path, diff) ->
            """
            文件: $path
            ```diff
            $diff
            ```
            """.trimIndent()
        }

        return """
        你是一名代码审查专家。请对下列每个已暂存文件的变更进行中文分析，要求：
        - 概括每个文件的改动目的与语义（修复/特性/重构/文档/测试/杂项）。
        - 列出关键修改点（项目符号、每条一行、简洁明确）。
        - 提出潜在风险与边界情况（如果有）。
        - 给出建议的测试点或验证步骤（如果适用）。
        - 输出结构示例：
          文件: <path>
          改动类型: <feat|fix|refactor|docs|test|chore 等>
          关键修改点:
          - ...
          - ...
          风险与兼容性:
          - ...
          建议测试点:
          - ...

        下列为各文件的diff：
        $content

        仅输出以上结构化内容，不要附加与分析无关的话术。
        """.trimIndent()
    }

    private fun postProcess(text: String): String {
        // 简单清理：去除意外包裹或前缀
        return text
            .replace("\r\n", "\n")
            .trim()
    }
}


