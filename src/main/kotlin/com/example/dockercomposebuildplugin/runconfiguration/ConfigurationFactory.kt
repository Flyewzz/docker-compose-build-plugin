package com.example.dockercomposebuildplugin.runconfiguration

import DockerComposeBuildRunConfiguration
import DockerComposeBuildRunConfigurationOptions
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class DockerComposeBuildConfigurationFactory(type: ConfigurationType?) : ConfigurationFactory(type!!) {
    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return DockerComposeBuildRunConfiguration(project, this, "DockerComposeBuild")
    }

    @NotNull
    override fun getId(): String {
        return DockerComposeBuildRunConfigurationType.ID
    }

    @Nullable
    override fun getOptionsClass(): Class<out BaseState>? {
        return DockerComposeBuildRunConfigurationOptions::class.java
    }
}