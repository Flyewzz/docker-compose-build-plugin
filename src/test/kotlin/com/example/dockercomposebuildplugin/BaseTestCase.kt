package com.example.dockercomposebuildplugin

import DockerComposeBuildRunConfiguration
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildConfigurationFactory
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildRunConfigurationType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

abstract class BaseDockerComposeTestCase : BasePlatformTestCase() {
    protected lateinit var runConfiguration: DockerComposeBuildRunConfiguration
    protected lateinit var editor: TestableSettingsEditor
    protected var projectDir: File? = null

    private val filesToCleanUp = mutableListOf<File>()

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        runConfiguration = DockerComposeBuildRunConfiguration(
            project,
            DockerComposeBuildConfigurationFactory(DockerComposeBuildRunConfigurationType()),
            "DockerComposeBuild",
        )

        editor = TestableSettingsEditor(project)

        projectDir = project.basePath?.let { File(it) }
        checkNotNull(projectDir) { "Project directory should not be null" }

        if (!projectDir!!.exists()) {
            projectDir!!.mkdir()
        }
    }

    @Throws(Exception::class)
    override fun tearDown() {
        // Clean up the files
        filesToCleanUp.forEach { it.delete() }
        filesToCleanUp.clear()

        projectDir?.let {
            if (it.exists()) {
                it.delete()
            }
        }

        LocalFileSystem.getInstance().refresh(false)

        super.tearDown()
    }

    protected fun createAndRefreshFile(filename: String, directory: File = projectDir!!): File {
        val file = File(directory, filename)
        file.createNewFile()

        // refresh info about file existence
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)?.refresh(false, false)
        filesToCleanUp.add(file)

        return file
    }

    protected fun createAndRefreshDirectory(dirname: String): File {
        val dir = File(projectDir!!, dirname)
        dir.mkdir()

        // refresh info about dir existence
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)?.refresh(false, false)
        filesToCleanUp.add(dir)

        return dir
    }
}
