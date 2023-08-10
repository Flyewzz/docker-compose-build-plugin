package com.example.dockercomposebuildplugin

import DockerComposeBuildRunConfiguration
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildConfigurationFactory
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildRunConfigurationType
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.After
import org.junit.Before
import java.io.File
import java.nio.file.Files

abstract class BaseDockerComposeTestCase : BasePlatformTestCase() {
    protected lateinit var runConfiguration: DockerComposeBuildRunConfiguration
    protected lateinit var editor: TestableSettingsEditor
    protected var projectDir: File? = null

    private val filesToCleanUp = mutableListOf<File>()

    @Before
    @Throws(Exception::class)
    public override fun setUp() {
        super.setUp()

        projectDir = project.basePath?.let { File(it) }
        checkNotNull(projectDir) { "Project directory should not be null" }

        if (!projectDir!!.exists()) {
            projectDir!!.mkdir()
        }

        runConfiguration = DockerComposeBuildRunConfiguration(
            project,
            DockerComposeBuildConfigurationFactory(DockerComposeBuildRunConfigurationType()),
            "DockerComposeBuild",
        )

        editor = TestableSettingsEditor(project)
    }

    @After
    @Throws(Exception::class)
    public override fun tearDown() {
        // Delete files first
        filesToCleanUp.filter { it.isFile }.forEach { it.delete() }

        // Delete directories next, in reverse order of creation
        filesToCleanUp.filter { it.isDirectory }.reversed().forEach { it.delete() }

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

    protected fun createAndRefreshSymbolicLink(filename: String, target: File, directory: File = projectDir!!): File {
        val linkPath = directory.toPath().resolve(filename)
        val targetPath = target.toPath()

        // Create the symbolic link
        Files.createSymbolicLink(linkPath, targetPath)

        // Refresh info about file existence
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(linkPath.toFile())?.refresh(false, false)
        filesToCleanUp.add(linkPath.toFile())

        return linkPath.toFile()
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
