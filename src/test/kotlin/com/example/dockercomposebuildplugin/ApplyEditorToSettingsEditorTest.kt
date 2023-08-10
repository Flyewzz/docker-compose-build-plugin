package com.example.dockercomposebuildplugin

import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission


class DockerComposeApplyEditorToSettingsEditorTest : BaseDockerComposeTestCase() {
    private lateinit var dockerComposePath: Path
    private lateinit var dockerComposeConfigPaths: List<Path>

    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()

        // Create a real temporary file for docker-compose executable and set it as executable
        dockerComposePath = createAndRefreshFile("docker-compose").toPath()
        Files.setPosixFilePermissions(dockerComposePath, setOf(PosixFilePermission.OWNER_EXECUTE))

        // Create real "file" for Docker Compose config path
        dockerComposeConfigPaths = listOf(
            createAndRefreshFile("docker-compose1.yml").toPath(),
            createAndRefreshFile("docker-compose2.yml").toPath()
        )
    }

    fun `test applyEditorTo when all fields are valid`() {
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFilesList(dockerComposeConfigPaths.map { it.toString() })
        editor.setCommandArgsFieldText("-m 4 -no_cache")

        editor.applyEditorTo(runConfiguration)

        assertEquals(dockerComposePath.toString(), runConfiguration.dockerPath)
        assertEquals(dockerComposeConfigPaths.map { it.toString() }, runConfiguration.dockerComposeFiles)
        assertEquals("-m 4 -no_cache", runConfiguration.commandArgs)
    }

    fun `test applyEditorTo`() {
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFilesList(dockerComposeConfigPaths.map { it.toString() })
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
        editor.setDockerComposeFilesList(dockerComposeConfigPaths.map { it.toString() })
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("Invalid Docker Compose path", e.message)
        }
    }

    fun `test applyEditorTo when Docker Compose config file path is invalid`() {
        editor.setDockerComposeFilesList(listOf("invalid path"))

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("Invalid Docker Compose config paths", e.message)
        }
    }

    fun `test applyEditorTo when some Docker Compose config file paths are invalid`() {
        val validFilePath1 = createAndRefreshFile("docker-compose.yml").path
        val invalidPath1 = "invalid/path1"
        val validFilePath2 = createAndRefreshFile("docker-compose.yaml").path
        val invalidPath2 = "invalid/path2"

        editor.setDockerComposeFilesList(listOf(validFilePath1, validFilePath2, invalidPath1, invalidPath2))

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("Invalid Docker Compose config paths", e.message)
        }
    }

    fun `test applyEditorTo when Docker Compose arguments are invalid`() {
        // Set the Docker Compose arguments to an invalid argument
        editor.setCommandArgsFieldText("-invalid")

        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFilesList(dockerComposeConfigPaths.map { it.toString() })

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
        editor.setDockerComposeFilesList(dockerComposeConfigPaths.map { it.toString() })

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("-m option cannot be used more than once", e.message)
        }
    }

    fun `test applyEditorTo when no docker compose config files specified`() {
        // Set other fields to valid values
        editor.setDockerPathFieldText(dockerComposePath.toString())
        editor.setDockerComposeFilesList(emptyList())
        // Set the Docker Compose arguments to use the same argument twice
        editor.setCommandArgsFieldText("-m 4")

        try {
            editor.applyEditorTo(runConfiguration)
            fail("Expected an exception to be thrown")
        } catch (e: ConfigurationException) {
            assertEquals("No Docker Compose config files specified", e.message)
        }
    }
}
