package com.github.dyz111ai.javaassigment1.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import javax.swing.JComponent
import javax.swing.JTextArea

class DiffAnalysisDialog(project: Project, private val analysis: String) : DialogWrapper(project) {

	private val textArea = JTextArea().apply {
		isEditable = false
		lineWrap = true
		wrapStyleWord = true
		text = analysis
	}

	init {
		title = "改动分析（AI）"
		setResizable(true)
		init()
	}

	override fun createCenterPanel(): JComponent {
		return JBScrollPane(textArea)
	}
}
