package com.example.dockercomposebuildplugin

import com.intellij.openapi.progress.EmptyProgressIndicator
import findAllDockerComposeFiles

class FileAutoFindingTest : BaseDockerComposeTestCase() {
    fun `test Docker Compose file auto-finding`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertTrue(editor.getDockerComposeFilesListModel().contains(dockerComposeFile.absolutePath))
    }

    fun `test Docker Compose file auto-finding with yaml extension`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yaml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertTrue(editor.getDockerComposeFilesListModel().contains(dockerComposeFile.absolutePath))
    }

    fun `test non Docker Compose yml file is selected`() {
        // Create a yml file with a different name in the project directory
        createAndRefreshFile("config.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is empty
        assertEmpty(editor.getDockerComposeFilesListModel())
    }

    fun `test search process of docker-compose config files in subdirectories`() {
        // Create a Docker Compose runConfiguration file in the project directory and the subdirectory
        val firstFile = createAndRefreshFile("docker-compose.yml")
        val subDir = createAndRefreshDirectory("sub-dir")
        var secondFile = createAndRefreshFile("docker-compose2.yml", subDir)
        createAndRefreshFile("docker-invalid.yml", subDir)

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose config files field is set to the path of the first file
        assertEquals(listOf(firstFile.absolutePath, secondFile.absolutePath), editor.getDockerComposeFilesListModel())
    }

    fun `test Docker Compose empty project`() {
        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose config files field is empty
        assertEmpty(editor.getDockerComposeFilesListModel())
    }

    fun `test Docker Compose handling of recursive symbolic links`() {
        val osName = System.getProperty("os.name").lowercase()
        // Skip the test if running on Windows
        if (osName.contains("mac") || osName.contains("nux")) {
            return
        }

        // Arrange
        createAndRefreshDirectory("link-dir")
        createAndRefreshSymbolicLink("recursive-link", projectDir!!)

        val indicator = EmptyProgressIndicator()

        // Act
        val resultFile = findAllDockerComposeFiles(project, indicator)

        // Assert
        assertEmpty(resultFile)
    }
}
