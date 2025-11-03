package com.github.dyz111ai.javaassigment1.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.openapi.application.ApplicationManager
import com.github.dyz111ai.javaassigment1.llm.LLMService

/**
 * 右键菜单：注释生成（直接在选中代码所属元素上插入注释，不经过对话框）。
 */
class GenerateCommentAction : AnAction("注释生成") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getRequiredData(CommonDataKeys.EDITOR)
        val psiFile = e.getRequiredData(CommonDataKeys.PSI_FILE)

        val selection = editor.selectionModel
        if (selection.hasSelection()) {
            if (!isFileModifiable(psiFile)) {
                Messages.showInfoMessage(project, "当前文件是编译产物（.class）或只读，无法插入注释。请打开对应的 .java 源文件后再试。", "注释生成")
                return
            }
            // 对所选代码段执行结构化批注（类/字段/方法/关键语句），并在顶部插入块注释摘要
            annotateSelection(project, psiFile, editor, selection.selectionStart, selection.selectionEnd)
            generateAndInsertAiSummary(project, editor, selection.selectedText ?: "")
            return
        }

        // 未选择文本：优先对光标所在方法生成/替换标准Javadoc，并为关键语句添加批注；若不在方法内，则尝试为类生成Javadoc
        val elementAtCaret = psiFile.findElementAt(editor.caretModel.offset)
            ?: psiFile.findElementAt((editor.caretModel.offset - 1).coerceAtLeast(0))
            ?: run {
                Messages.showInfoMessage(project, "未选中文本，且光标不在方法/类内。", "注释生成")
                return
            }
        val targetMethod = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethod::class.java, false)
        if (targetMethod != null) {
            if (!isElementModifiable(targetMethod)) {
                Messages.showInfoMessage(project, "该方法所在文件不可修改（可能为 .class 或只读）。请在 .java 源文件中执行。", "注释生成")
                return
            }
            generateOrReplaceMethodJavadoc(project, targetMethod)
            annotateKeyStatements(project, targetMethod)
            return
        }

        val targetClass = PsiTreeUtil.getParentOfType(elementAtCaret, PsiClass::class.java, false)
        if (targetClass != null) {
            if (!isElementModifiable(targetClass)) {
                Messages.showInfoMessage(project, "该类所在文件不可修改（可能为 .class 或只读）。请在 .java 源文件中执行。", "注释生成")
                return
            }
            generateClassJavadoc(project, targetClass)
            return
        }

        Messages.showInfoMessage(project, "未选中文本，且未检测到方法或类范围。请选中代码进行块注释。", "注释生成")
    }

    private fun generateAndInsertAiSummary(project: Project, editor: Editor, selectedText: String) {
        val doc: Document = editor.document
        val selStart = editor.selectionModel.selectionStart
        val firstLine = doc.getLineNumber(selStart)
        val firstLineStartOffset = doc.getLineStartOffset(firstLine)
        val firstLineEndOffset = doc.getLineEndOffset(firstLine)
        val firstLineText = doc.getText(com.intellij.openapi.util.TextRange(firstLineStartOffset, firstLineEndOffset))
        val firstNonWsIndex = firstLineText.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) firstLineText.length else it }
        val indent = firstLineText.substring(0, firstNonWsIndex)

        ApplicationManager.getApplication().executeOnPooledThread {
            val prompt = """
请基于以下代码片段（不需要逐行复述），用简洁、专业、规范的中文，生成一段可直接贴在代码上方的块注释：
- 先给出该代码块的业务意图/核心功能一句话说明
- 概述主要处理流程（按要点列出，不超过5行）
- 说明关键输入/状态（若有）与输出/副作用（若有）
- 指出异常/边界情况与处理要点（若涉及）
要求：
- 输出为标准块注释格式，以 /* 开始，以 */ 结束；每行以 " * " 前缀；不要包含 Markdown 代码块；不要重复粘贴原代码。
代码：
${"""$selectedText"""}
            """.trimIndent()

            val llm = LLMService()
            val ai = try {
                llm.generateResponse(prompt, "为所选代码生成中文规范块注释")
            } catch (_: Exception) {
                null
            }

            val commentBlock = sanitizeToBlockComment(ai) ?: buildZhBlockSummary(selectedText)

            ApplicationManager.getApplication().invokeLater {
        WriteCommandAction.runWriteCommandAction(project) {
                    if (!doc.isWritable) {
                        Messages.showInfoMessage(project, "当前文件不可写（只读或只读方案）。", "注释生成")
                        return@runWriteCommandAction
                    }
                    val commentText = buildString {
                        append(indent)
                        append(commentBlock)
                        append("\n")
                    }
                    doc.insertString(firstLineStartOffset, commentText)
                    PsiDocumentManager.getInstance(project).commitDocument(doc)
                }
            }
        }
    }

    private fun sanitizeToBlockComment(aiText: String?): String? {
        if (aiText.isNullOrBlank()) return null
        var t = aiText.trim()
        // 移除可能的 Markdown 代码块包裹
        if (t.startsWith("```")) {
            t = t.removePrefix("```java").removePrefix("```").removeSuffix("```").trim()
        }
        // 若不是标准块注释，则包裹为块注释并为每行加前缀
        if (!t.startsWith("/*")) {
            val b = StringBuilder()
            b.append("/*\n")
            t.lines().forEach { line -> b.append(" * ").append(line.trim()).append("\n") }
            b.append(" */")
            return b.toString()
        }
        return t
    }

    /**
     * 对选中范围做结构化批注：类Javadoc、字段意义注释、方法Javadoc、方法内关键语句与局部变量注释。
     */
    private fun annotateSelection(project: Project, psiFile: PsiFile, editor: Editor, selStart: Int, selEnd: Int) {
        val doc = editor.document
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val range = com.intellij.openapi.util.TextRange(selStart, selEnd)

        if (!isFileModifiable(psiFile)) return

        // 收集选区内的类
        val classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
            .filter { it.textRange.intersects(range) }

        if (classes.isEmpty()) {
            // 即便不是类，也尝试为选区内的方法和局部变量加注释
            val methods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
                .filter { it.textRange.intersects(range) }
            applyMethodAndLocalsAnnotations(project, methods)
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            for (cls in classes) {
                if (!isElementModifiable(cls)) continue
                // 类Javadoc
                if (cls.docComment == null) {
                    val docComment = factory.createDocCommentFromText(buildClassJavadoc(cls))
                    cls.addBefore(docComment, cls.firstChild)
                }

                // 字段意义注释
                for (field in cls.allFields) {
                    if (!field.textRange.intersects(range)) continue
                    if (!isElementModifiable(field)) continue
                    if (hasLeadingComment(field)) continue
                    val comment = factory.createCommentFromText("// ${guessVariableMeaning(field.name ?: "字段", field.type.presentableText)}", null)
                    cls.addBefore(comment, field)
                }
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        // 类中的方法：方法Javadoc + 关键语句 + 局部变量注释
        val methodsInClasses = classes.flatMap { it.methods.asList() }.filter { it.textRange.intersects(range) }
        applyMethodAndLocalsAnnotations(project, methodsInClasses)
    }

    private fun applyMethodAndLocalsAnnotations(project: Project, methods: List<PsiMethod>) {
        if (methods.isEmpty()) return
        
        // 使用 AI 为每个方法生成详细的 Javadoc 注释
        for (method in methods) {
            if (!isElementModifiable(method)) continue
            generateMethodJavadocWithAI(project, method)
        }
        
        // 局部变量和关键语句注释（保持原有逻辑）
        WriteCommandAction.runWriteCommandAction(project) {
            for (m in methods) {
                if (!isElementModifiable(m)) continue
                annotateLocalVariables(project, m)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        
        // 关键语句注释（需在写入外以便二次写操作）
        for (m in methods) annotateKeyStatements(project, m)
    }

    private fun annotateLocalVariables(project: Project, method: PsiMethod) {
        val body = method.body ?: return
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val targets = mutableListOf<Pair<PsiElement, String>>()

        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitDeclarationStatement(statement: PsiDeclarationStatement) {
                super.visitDeclarationStatement(statement)
                statement.declaredElements.forEach { el ->
                    if (el is PsiLocalVariable) {
                        val name = el.name ?: return@forEach
                        val type = el.type.presentableText
                        if (shouldAnnotateVariable(name, type)) {
                            targets += statement to "变量：${guessVariableMeaning(name, type)}"
                        }
                    }
                }
            }
        })

        if (targets.isEmpty()) return
        val limited = targets.take(6)
        WriteCommandAction.runWriteCommandAction(project) {
            for ((el, text) in limited) {
                if (!isElementModifiable(el)) continue
                if (hasLeadingComment(el)) continue
                val comment = factory.createCommentFromText("// ${text}", null)
                method.addBefore(comment, el)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun hasLeadingComment(element: PsiElement): Boolean {
        var prev: PsiElement? = element.prevSibling
        while (prev is PsiWhiteSpace) prev = prev.prevSibling
        return prev is PsiComment
    }

    private fun hasNonTrivialBody(element: PsiElement): Boolean {
        val block: PsiElement? = when (element) {
            is PsiIfStatement -> element.thenBranch
            is PsiForStatement -> element.body
            is PsiForeachStatement -> element.body
            is PsiWhileStatement -> element.body
            is PsiTryStatement -> element.tryBlock
            is PsiSwitchStatement -> element.body
            else -> null
        }
        block ?: return false
        val text = block.text.replace("\\s+".toRegex(), " ")
        return text.length > 2 && (text.contains(";") || text.contains(" if ") || text.contains(" for ") || text.contains(" while ") || text.contains(" try "))
    }

    private fun summarizeIf(stmt: PsiIfStatement): String {
        val cond = stmt.condition?.text?.let { simplifyExpr(it) } ?: "条件"
        val hasElse = stmt.elseBranch != null
        return if (hasElse) "条件分支：当 ${cond} 成立与不成立分别处理" else "条件判断：当 ${cond} 时执行"
    }

    private fun summarizeFor(stmt: PsiForStatement): String {
        val src = stmt.initialization?.text ?: stmt.condition?.text ?: "范围/计数器"
        return "循环：基于 ${simplifyExpr(src)} 迭代处理"
    }

    private fun summarizeForeach(stmt: PsiForeachStatement): String {
        val iter = stmt.iteratedValue?.text?.let { simplifyExpr(it) } ?: "集合"
        val varName = stmt.iterationParameter?.name ?: "元素"
        return "循环：遍历 ${iter}，逐项（${varName}）处理"
    }

    private fun summarizeWhile(stmt: PsiWhileStatement): String {
        val cond = stmt.condition?.text?.let { simplifyExpr(it) } ?: "条件"
        return "循环：在 ${cond} 成立期间持续处理"
    }

    private fun simplifyExpr(expr: String): String {
        return expr
            .replace("(\n|\r)".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .removePrefix("(").removeSuffix(")")
            .take(60)
    }

    private fun shouldAnnotateVariable(name: String, typeText: String): Boolean {
        val n = name.lowercase()
        if (n.length <= 2 && (n == "i" || n == "j" || n == "k")) return false
        return n.contains("id") || n.contains("name") || n.contains("flag") || n.contains("status") ||
            n.contains("count") || n.contains("size") || n.contains("num") || n.contains("total") ||
            n.contains("time") || n.contains("date") || n.contains("timestamp") ||
            n.contains("list") || typeText.contains("List") ||
            n.contains("map") || typeText.contains("Map") ||
            n.contains("set") || typeText.contains("Set") ||
            n.contains("url") || n.contains("uri") || n.contains("path") ||
            n.contains("result") || n.contains("res") ||
            n.contains("price") || n.contains("amount") || n.contains("money")
    }

    private fun guessVariableMeaning(name: String, typeText: String): String {
        val n = name.lowercase()
        return when {
            n == "id" || n.endsWith("id") -> "唯一标识"
            n.contains("name") -> "名称"
            n.contains("flag") || n.startsWith("is") || typeText.equals("boolean", true) -> "布尔标志"
            n.contains("count") || n.contains("size") || n.contains("num") || n.contains("total") -> "数量/规模"
            n.contains("index") || n == "i" || n == "j" -> "索引位置"
            n.contains("time") || n.contains("timestamp") || n.contains("date") -> "时间/时间戳"
            n.contains("price") || n.contains("amount") || n.contains("money") -> "金额/数值"
            n.contains("url") || n.contains("uri") || n.contains("path") -> "资源定位/路径"
            n.contains("list") || typeText.contains("List") -> "列表集合"
            n.contains("map") || typeText.contains("Map") -> "键值映射"
            n.contains("set") || typeText.contains("Set") -> "不重复集合"
            n.contains("result") || n.contains("res") -> "计算/调用结果"
            n.contains("status") || n.contains("state") -> "状态码/状态"
            n.contains("code") -> "编码/错误码"
            n.contains("temp") || n.contains("buf") || n.contains("cache") -> "临时/缓存数据"
            else -> "${typeText} 类型变量"
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        if (editor == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        // 始终在编辑器右键菜单中显示该动作；启用状态不再依赖即时可写性，避免误判导致置灰
        e.presentation.isVisible = true
        e.presentation.isEnabled = true
    }

    private fun generateMethodJavadoc(project: Project, method: PsiMethod) {
        if (method.docComment != null) {
            Messages.showInfoMessage(project, "该方法已存在注释。", "注释生成")
            return
        }
        // 使用 AI 生成详细注释
        generateMethodJavadocWithAI(project, method)
    }

    /**
     * 使用 AI 生成方法的详细 Javadoc 注释
     */
    private fun generateMethodJavadocWithAI(project: Project, method: PsiMethod) {
        // 获取方法签名和完整代码
        val methodSignature = buildMethodSignature(method)
        val methodCode = method.text
        
        // 使用 AI 生成详细的 Javadoc 注释
        ApplicationManager.getApplication().executeOnPooledThread {
            val prompt = """
请为以下 Java 方法生成完整的中文 Javadoc 注释。要求：
1. 使用标准的 Javadoc 格式（/** ... */）
2. 详细说明方法的功能、参数、返回值、可能抛出的异常
3. 不要使用占位符或"请完善"等提示文本
4. 所有内容用中文描述，专业且准确
5. 输出格式：每行以 " * " 开头

方法签名：
${methodSignature}

完整方法代码：
${methodCode}
            """.trimIndent()

            val llm = LLMService()
            val aiJavadoc = try {
                llm.generateResponse(prompt, "为方法生成详细Javadoc注释")
            } catch (_: Exception) {
                null
            }

            val finalJavadoc = sanitizeJavadocComment(aiJavadoc) ?: buildMethodJavadoc(method)

            ApplicationManager.getApplication().invokeLater {
                val factory = JavaPsiFacade.getInstance(project).elementFactory
                WriteCommandAction.runWriteCommandAction(project) {
                    val newDoc = factory.createDocCommentFromText(finalJavadoc)
                    val existing = method.docComment
                    if (existing != null) {
                        existing.replace(newDoc)
                    } else {
                        method.addBefore(newDoc, method.firstChild)
                    }
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }
            }
        }
    }

    private fun generateClassJavadoc(project: Project, cls: PsiClass) {
        if (cls.docComment != null) {
            Messages.showInfoMessage(project, "该类已存在注释。", "注释生成")
            return
        }
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val javadoc = buildClassJavadoc(cls)
        WriteCommandAction.runWriteCommandAction(project) {
            val doc = factory.createDocCommentFromText(javadoc)
            cls.addBefore(doc, cls.firstChild)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun insertSummaryBlockCommentAboveSelection(project: Project, psiFile: PsiFile, editor: Editor, selectedText: String) {
        val doc: Document = editor.document
        val selStart = editor.selectionModel.selectionStart
        val firstLine = doc.getLineNumber(selStart)
        val firstLineStartOffset = doc.getLineStartOffset(firstLine)
        val firstLineEndOffset = doc.getLineEndOffset(firstLine)
        val firstLineText = doc.getText(com.intellij.openapi.util.TextRange(firstLineStartOffset, firstLineEndOffset))

        val firstNonWsIndex = firstLineText.indexOfFirst { !it.isWhitespace() }.let { if (it == -1) firstLineText.length else it }
        val indent = firstLineText.substring(0, firstNonWsIndex)

        val block = buildZhBlockSummary(selectedText)

        WriteCommandAction.runWriteCommandAction(project) {
            val docIsWritable = editor.document.isWritable
            if (!docIsWritable) {
                Messages.showInfoMessage(project, "当前文件不可写（只读或只读方案）。", "注释生成")
                return@runWriteCommandAction
            }
            val commentText = buildString {
                append(indent)
                append(block)
                append("\n")
            }
            doc.insertString(firstLineStartOffset, commentText)
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
    }

    private fun buildZhBlockSummary(selectedText: String): String {
        val lines = selectedText.lines().filter { it.isNotBlank() }
        val lineCount = lines.size
        val text = selectedText.lowercase()

        val hasIf = text.contains("if(") || text.contains("if ")
        val hasElse = text.contains("else")
        val hasLoop = listOf("for(", "for ", "while(", "while ").any { text.contains(it) }
        val hasTry = text.contains("try{") || text.contains("try ")
        val hasCall = Regex("[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(").containsMatchIn(selectedText)
        val hasAssign = Regex("=\\s*").containsMatchIn(selectedText)

        val keyPoints = mutableListOf<String>()
        if (hasIf) keyPoints.add("条件分支")
        if (hasElse) keyPoints.add("兜底分支")
        if (hasLoop) keyPoints.add("循环处理")
        if (hasTry) keyPoints.add("异常处理")
        if (hasCall) keyPoints.add("方法调用")
        if (hasAssign) keyPoints.add("状态变更/赋值")

        val brief = if (keyPoints.isEmpty()) "通用逻辑" else keyPoints.joinToString("，")

        return buildString {
            append("/*\n")
            append(" * 功能概要：对以下 ${lineCount} 行代码进行业务性描述，说明意图与作用。\n")
            append(" * 关键点：").append(brief).append("。\n")
            append(" * 输入前置：列出依赖的入参、外部状态或前置条件（若有）。\n")
            append(" * 处理流程：简述主要步骤与分支逻辑，避免逐行解释。\n")
            append(" * 输出结果：说明直接返回值或对外部状态的影响。\n")
            append(" * 异常场景：列出可能的异常路径与处理策略（若涉及）。\n")
            append(" * 性能/复杂度：若存在嵌套循环或重计算，给出注意事项。\n")
            append(" */")
        }
    }

    /**
     * 仅为关键控制结构添加注释，并控制注释密度（避免逐句）。
     */
    private fun annotateKeyStatements(project: Project, method: PsiMethod) {
        val body = method.body ?: return
        val factory = JavaPsiFacade.getInstance(project).elementFactory

        val candidates = mutableListOf<Pair<PsiElement, String>>()

        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                candidates += statement to summarizeIf(statement)
            }

            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                candidates += statement to summarizeFor(statement)
            }

            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                candidates += statement to summarizeForeach(statement)
            }

            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                candidates += statement to summarizeWhile(statement)
            }

            override fun visitTryStatement(statement: PsiTryStatement) {
                super.visitTryStatement(statement)
                candidates += statement to "异常处理：保护关键逻辑并区分异常分支"
            }

            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                val key = statement.expression?.text?.let { simplifyExpr(it).take(40) } ?: "关键变量"
                candidates += statement to "分支选择：按 ${key} 进行多路处理"
            }
        })

        if (candidates.isEmpty()) return

        val filtered = candidates
            .filter { !hasLeadingComment(it.first) }
            .filter { hasNonTrivialBody(it.first) }
            .sortedBy { it.first.textRange.startOffset }
            .take(8)

        if (filtered.isEmpty()) return

        WriteCommandAction.runWriteCommandAction(project) {
            for ((el, text) in filtered) {
                if (!isElementModifiable(el)) continue
                val comment = factory.createCommentFromText("// ${text}", null)
                method.addBefore(comment, el)
            }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
    }

    private fun isFileModifiable(psiFile: PsiFile): Boolean {
        if (psiFile is PsiCompiledElement) return false
        val vFile = psiFile.virtualFile ?: return false
        if (vFile.fileType.isBinary) return false
        if (!psiFile.isWritable) return false
        return true
    }

    private fun isElementModifiable(element: PsiElement): Boolean {
        val file = element.containingFile ?: return false
        return isFileModifiable(file)
    }

    private fun describeLineZh(code: String): String {
        val t = code.trim()
        if (t.isBlank()) return "空行"
        val lower = t.lowercase()
        return when {
            lower.startsWith("if ") || lower.startsWith("if(") -> "条件判断：根据条件分支执行"
            lower.startsWith("else if") || lower.startsWith("elseif") -> "条件判断：备用分支"
            lower.startsWith("else") -> "条件判断：兜底分支"
            lower.startsWith("for ") || lower.startsWith("for(") -> "循环：遍历集合或范围"
            lower.startsWith("while ") || lower.startsWith("while(") -> "循环：条件满足时重复执行"
            lower.startsWith("try ") || lower.startsWith("try{") || lower.startsWith("try{") -> "异常处理：尝试执行受保护代码"
            lower.startsWith("catch ") || lower.startsWith("catch(") -> "异常处理：捕获并处理异常"
            lower.startsWith("finally") -> "异常处理：无论是否异常均执行的清理逻辑"
            lower.startsWith("return ") -> "返回语句：返回结果"
            lower.startsWith("break") -> "流程控制：跳出循环或开关"
            lower.startsWith("continue") -> "流程控制：继续下一轮循环"
            Regex("^[a-zA-Z_][a-zA-Z0-9_]*\\s*\\(.*\\)\\s*;?$").containsMatchIn(t) -> "方法调用：执行 ${t.substring(0, t.indexOf('(')).trim()}"
            Regex("""^([a-zA-Z_][a-zA-Z0-9_<>[\]]+\s+)+[a-zA-Z_][a-zA-Z0-9_]*\s*=.*""").containsMatchIn(t) -> "变量赋值：更新变量值"
            Regex("""^[a-zA-Z_][a-zA-Z0-9_<>[\]]+\s+[a-zA-Z_][a-zA-Z0-9_]*\s*;?$""").containsMatchIn(t) -> "变量声明：定义新变量"
            t.startsWith("//") || t.startsWith("/*") -> "已有注释"
            else -> "执行语句：" + t.take(40).trim()
        }
    }

    private fun generateOrReplaceMethodJavadoc(project: Project, method: PsiMethod) {
        // 统一使用 AI 生成方法注释
        generateMethodJavadocWithAI(project, method)
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val sb = StringBuilder()
        val modifiers = method.modifierList.text
        val returnType = method.returnType?.presentableText ?: "void"
        val name = method.name
        
        sb.append("$modifiers $returnType $name(")
        val params = method.parameterList.parameters
        for (i in params.indices) {
            if (i > 0) sb.append(", ")
            sb.append("${params[i].type.presentableText} ${params[i].name}")
        }
        sb.append(")")
        return sb.toString()
    }

    private fun sanitizeJavadocComment(aiText: String?): String? {
        if (aiText.isNullOrBlank()) return null
        var t = aiText.trim()
        
        // 移除可能的 Markdown 代码块包裹
        if (t.startsWith("```")) {
            t = t.removePrefix("```java").removePrefix("```").removeSuffix("```").trim()
        }
        
        // 确保以 /** 开始
        if (!t.startsWith("/**")) {
            t = "/**\n * $t"
        }
        
        // 确保以 */ 结束
        if (!t.endsWith("*/")) {
            t = "$t\n */"
        }
        
        // 确保每行以 " * " 开头（除了第一行的 /** 和最后一行的 */）
        val lines = t.lines().toMutableList()
        val sanitized = mutableListOf<String>()
        for (i in lines.indices) {
            val line = lines[i].trim()
            when {
                i == 0 && line == "/**" -> sanitized.add("/**")
                i == lines.size - 1 && line == "*/" -> sanitized.add(" */")
                line.isEmpty() -> sanitized.add(" *")
                line.startsWith(" * ") -> sanitized.add(line)
                else -> sanitized.add(" * $line")
            }
        }
        
        return sanitized.joinToString("\n")
    }

    private fun buildMethodJavadoc(method: PsiMethod): String {
        val sb = StringBuilder()
        val params = method.parameterList.parameters
        val returnType = method.returnType?.presentableText ?: "void"
        val throwsTypes = method.throwsList.referencedTypes

        sb.append("""/**\n""")
        sb.append(" * 功能：${method.name} 方法的职责与主要处理流程。\n")
        sb.append(" * 说明：请根据业务上下文补充输入前置条件、核心逻辑与后置效果。\n")
        for (p in params) {
            sb.append(" * @param ${p.name} 参数，类型：${p.type.presentableText}。说明：请完善。\n")
        }
        if (returnType != "void") {
            sb.append(" * @return 返回 ${returnType}。说明：请完善。\n")
        }
        for (t in throwsTypes) {
            sb.append(" * @throws ${t.presentableText} 当发生相应错误场景时抛出。\n")
        }
        sb.append(" */")
        return sb.toString()
    }

    private fun buildClassJavadoc(cls: PsiClass): String {
        val sb = StringBuilder()
        sb.append("""/**\n""")
        sb.append(" * ${cls.name ?: "该类"} 的职责说明。\n")
        sb.append(" */")
        return sb.toString()
    }
}
