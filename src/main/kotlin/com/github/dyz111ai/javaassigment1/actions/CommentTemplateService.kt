package com.github.dyz111ai.javaassigment1.actions

import com.intellij.openapi.project.Project
import java.io.File
import java.util.Properties

class CommentTemplateService(private val project: Project) {

    private val props: Properties = Properties()

    init {
        loadDefaults()
        loadProjectOverrides()
    }

    private fun loadDefaults() {
        props["method.header.start"] = "/**"
        props["method.header.desc.prefix"] = " * 功能："
        props["method.blank"] = " *"
        props["method.param"] = " * @param {{name}} {{type}} - {{desc}}"
        props["method.return"] = " * @return {{desc}}"
        props["method.throws"] = " * @throws {{type}} {{desc}}"
        props["method.end"] = " */"

        props["class.header.start"] = "/**"
        props["class.header.desc.prefix"] = " * 职责："
        props["class.fields.title"] = " * 主要字段："
        props["class.field"] = " * - {{name}}: {{type}}"
        props["class.methods.title"] = " * 公开方法："
        props["class.method"] = " * - {{name}}({{params}}) : {{return}}"
        props["class.end"] = " */"
    }

    private fun loadProjectOverrides() {
        val basePath = project.basePath ?: return
        val candidates = listOf(
            File(basePath, "comment-template.properties"),
            File(basePath, ".idea/comment-template.properties")
        )
        val file = candidates.firstOrNull { it.exists() && it.isFile } ?: return
        file.inputStream().use { props.load(it) }
    }

    fun methodHeaderStart(): String = props.getProperty("method.header.start")
    fun methodHeaderDescPrefix(): String = props.getProperty("method.header.desc.prefix")
    fun methodBlank(): String = props.getProperty("method.blank")
    fun methodParam(): String = props.getProperty("method.param")
    fun methodReturn(): String = props.getProperty("method.return")
    fun methodThrows(): String = props.getProperty("method.throws")
    fun methodEnd(): String = props.getProperty("method.end")

    fun classHeaderStart(): String = props.getProperty("class.header.start")
    fun classHeaderDescPrefix(): String = props.getProperty("class.header.desc.prefix")
    fun classFieldsTitle(): String = props.getProperty("class.fields.title")
    fun classField(): String = props.getProperty("class.field")
    fun classMethodsTitle(): String = props.getProperty("class.methods.title")
    fun classMethod(): String = props.getProperty("class.method")
    fun classEnd(): String = props.getProperty("class.end")

    fun render(template: String, values: Map<String, String>): String {
        var result = template
        values.forEach { (k, v) ->
            result = result.replace("{{${k}}}", v)
        }
        return result
    }
}




