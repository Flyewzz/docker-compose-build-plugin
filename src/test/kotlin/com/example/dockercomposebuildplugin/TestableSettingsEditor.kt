package com.example.dockercomposebuildplugin

import DockerComposeBuildSettingsEditor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import findAllDockerComposeFiles

class TestableSettingsEditor(project: Project) : DockerComposeBuildSettingsEditor(project) {
    override fun loadDockerComposeFiles(runConfigurationProject: Project, action: (List<VirtualFile>) -> Unit) {
        // Synchronous call
        val dockerComposeFiles = findAllDockerComposeFiles(runConfigurationProject, EmptyProgressIndicator())
        action(dockerComposeFiles)
    }
}