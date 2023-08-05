package com.example.dockercomposebuildplugin

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.vfs.LocalFileSystem
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission


class DockerComposeApplyEditorToSettingsEditorTest : BaseDockerComposeTestCase() {
    private lateinit var dockerComposePath: Path
    private lateinit var dockerComposeConfigPath: Path

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        // Create a real temporary file for docker-compose executable and set it as executable
        dockerComposePath = createAndRefreshFile("docker-compose").toPath()
        Files.setPosixFilePermissions(dockerComposePath, setOf(PosixFilePermission.OWNER_EXECUTE))

        // Create real "file" for Docker Compose config path
        dockerComposeConfigPath = createAndRefreshFile("docker-compose.yml").toPath()
    }

    @Throws(Exception::class)
    override fun tearDown() {
        projectDir?.let {
            if (it.exists()) {
                it.delete()
            }
        }

        LocalFileSystem.getInstance().refresh(false)

        super.tearDown()
    }

    fun `test applyEditorTo when all fields are valid`() {
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFileFieldText(dockerComposeConfigPath.toString())
        editor.setCommandArgsFieldText("-m 4 -no_cache")

        editor.applyEditorTo(runConfiguration)

        assertEquals(dockerComposePath.toString(), runConfiguration.dockerPath)
        assertEquals(dockerComposeConfigPath.toString(), runConfiguration.dockerComposeFilePath)
        assertEquals("-m 4 -no_cache", runConfiguration.commandArgs)
    }

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
