package com.example.dockercomposebuildplugin

import DockerComposeBuildRunConfiguration
import DockerComposeBuildSettingsEditor
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildConfigurationFactory
import com.example.dockercomposebuildplugin.runconfiguration.DockerComposeBuildRunConfigurationType
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission


class TestableDockerComposeBuildSettingsEditor(project: Project) : DockerComposeBuildSettingsEditor(project) {
    fun setDockerPathFieldText(text: String) {
        dockerPathField.text = text
    }

    fun setDockerComposeFileFieldText(text: String) {
        dockerComposeFileField.text = text
    }

    fun setCommandArgsFieldText(text: String) {
        commandArgsField.text = text
    }
}

class DockerComposeBuildSettingsEditorTest : BasePlatformTestCase() {
    private lateinit var dockerComposePath: Path
    private lateinit var dockerComposeConfigPath: Path
    private lateinit var runConfiguration: DockerComposeBuildRunConfiguration
    private lateinit var editor: TestableDockerComposeBuildSettingsEditor

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        // Create a real temporary file for docker-compose executable and set it as executable
        dockerComposePath = Files.createTempFile("docker-compose", null)
        Files.setPosixFilePermissions(dockerComposePath, setOf(PosixFilePermission.OWNER_EXECUTE))

        // Create real "file" for Docker Compose config path
        dockerComposeConfigPath = Files.createTempFile("docker-compose.yml", null)

        runConfiguration = DockerComposeBuildRunConfiguration(
            project,
            DockerComposeBuildConfigurationFactory(DockerComposeBuildRunConfigurationType()),
            "DockerComposeBuild",
        )
        editor = TestableDockerComposeBuildSettingsEditor(project)
    }

    @Throws(Exception::class)
    override fun tearDown() {
        // Delete the temporary files
        dockerComposePath.toFile().delete()
        dockerComposeConfigPath.toFile().delete()

        super.tearDown()
    }

    @Test
    fun `test applyEditorTo when all fields are valid`() {
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())
        editor.setCommandArgsFieldText("-m 4 -no_cache")

        editor.applyEditorTo(runConfiguration)

        assertEquals(dockerComposePath.toString(), runConfiguration.dockerPath)
        assertEquals(dockerComposeConfigPath.toString(), runConfiguration.dockerComposeFilePath)
        assertEquals("-m 4 -no_cache", runConfiguration.commandArgs)
    }

    @Test
    fun `test applyEditorTo`() {
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())
        editor.setCommandArgsFieldText("-m 4 --memory 2")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("--memory option cannot be used more than once", e.message)
        }
    }

    @Test
    fun `test applyEditorTo when Docker Compose path is invalid`() {
        editor.setDockerPathFieldText("invalid path")

        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("Invalid Docker Compose path", e.message)
        }
    }

    @Test
    fun `test applyEditorTo when Docker Compose file path is invalid`() {
        editor.setDockerComposeFileFieldText("invalid path")

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("Invalid Docker Compose config path", e.message)
        }
    }

    @Test
    fun `test applyEditorTo when Docker Compose arguments are invalid`() {
        // Set the Docker Compose arguments to an invalid argument
        editor.setCommandArgsFieldText("-invalid")

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertTrue(e.message!!.startsWith("Invalid Docker Compose arguments:"))
        }
    }

    @Test
    fun `test applyEditorTo when Docker Compose argument is used more than once`() {
        // Set the Docker Compose arguments to use the same argument twice
        editor.setCommandArgsFieldText("-m 4 -m 4")

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("-m option cannot be used more than once", e.message)
        }
    }
}
