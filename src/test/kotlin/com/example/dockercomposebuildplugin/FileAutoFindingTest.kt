package com.example.dockercomposebuildplugin

import org.junit.Test

class FileAutoFindingTest : BaseDockerComposeTestCase() {
    @Test
    fun `test Docker Compose file auto-finding`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertEquals(dockerComposeFile.absolutePath, editor.getDockerComposeFileFieldText())
    }

    @Test
    fun `test Docker Compose file auto-finding with yaml extension`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yaml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertEquals(dockerComposeFile.absolutePath, editor.getDockerComposeFileFieldText())
    }

    @Test
    fun `test non Docker Compose yml file is selected`() {
        // Create a yml file with a different name in the project directory
        createAndRefreshFile("not-docker-compose.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is empty
        assertEquals("", editor.getDockerComposeFileFieldText())
    }

    @Test
    fun `test search process stops at first docker-compose file encountered`() {
        // Create a Docker Compose runConfiguration file in the project directory and the subdirectory
        val firstFile = createAndRefreshFile("docker-compose.yml")
        val subDir = createAndRefreshDirectory("sub-dir")
        createAndRefreshFile("docker-compose.yml", subDir)

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the first file
        assertEquals(firstFile.absolutePath, editor.getDockerComposeFileFieldText())
    }
}
