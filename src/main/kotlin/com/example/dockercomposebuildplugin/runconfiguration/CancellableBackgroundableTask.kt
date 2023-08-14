package com.example.dockercomposebuildplugin.runconfiguration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

open class CancellableBackgroundableTask(project: Project, title: String) : Task.Backgroundable(project, title, true) {
    private var myIndicator: ProgressIndicator? = null

    override fun run(indicator: ProgressIndicator) {
        myIndicator = indicator
    }

    fun cancel() {
        myIndicator?.cancel()
    }
}
