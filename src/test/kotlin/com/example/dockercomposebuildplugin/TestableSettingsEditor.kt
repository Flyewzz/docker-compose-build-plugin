package com.example.dockercomposebuildplugin

import DockerComposeBuildSettingsEditor
import com.intellij.openapi.project.Project

class TestableSettingsEditor(project: Project) : DockerComposeBuildSettingsEditor(project) {
    fun setDockerPathFieldText(text: String) {
        dockerPathField.text = text
    }

    fun setDockerComposeFileFieldText(text: String) {
        dockerComposeFileField.text = text
    }

    fun setCommandArgsFieldText(text: String) {
        commandArgsField.text = text
    }

    fun getDockerComposeFileFieldText(): String {
        return dockerComposeFileField.text
    }
}