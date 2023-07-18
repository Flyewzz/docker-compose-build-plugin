package com.example.dockercomposebuildplugin.runconfiguration

import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NotNullLazyValue


open class DockerComposeBuildRunConfigurationType protected constructor() :
    ConfigurationTypeBase(
        ID, "Docker Compose build", "DockerComposeBuild run configuration type",
        NotNullLazyValue.createValue { AllIcons.Nodes.Console }) {
    init {
        addFactory(DockerComposeBuildConfigurationFactory(this))
    }

    companion object {
        const val ID = "DockerComposeBuildRunConfiguration"
    }
}