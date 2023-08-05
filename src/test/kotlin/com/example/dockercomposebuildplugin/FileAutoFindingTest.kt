package com.example.dockercomposebuildplugin

class FileAutoFindingTest : BaseDockerComposeTestCase() {
    fun `test Docker Compose file auto-finding`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertEquals(dockerComposeFile.absolutePath, editor.getDockerComposeFileFieldText())
    }

    fun `test Docker Compose file auto-finding with yaml extension`() {
        val dockerComposeFile = createAndRefreshFile("docker-compose.yaml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the created file
        assertEquals(dockerComposeFile.absolutePath, editor.getDockerComposeFileFieldText())
    }

    fun `test non Docker Compose yml file is selected`() {
        // Create a yml file with a different name in the project directory
        createAndRefreshFile("not-docker-compose.yml")

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is empty
        assertEquals("", editor.getDockerComposeFileFieldText())
    }

    fun `test search process stops at first docker-compose file encountered`() {
        // Create a Docker Compose runConfiguration file in the project directory and the subdirectory
        val firstFile = createAndRefreshFile("docker-compose.yml")
        val subDir = createAndRefreshDirectory("sub-dir")
        createAndRefreshFile("docker-compose.yml", subDir)

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the first file
        assertEquals(firstFile.absolutePath, editor.getDockerComposeFileFieldText())
    }

    fun `test Docker Compose empty project`() {
        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is empty
        assertEquals("", editor.getDockerComposeFileFieldText())
    }

    fun `test Docker Compose in subdirectory if none in root`() {
        val subDir = createAndRefreshDirectory("subdir")
        val subDirFile = createAndRefreshFile("docker-compose.yml", subDir)

        editor.resetEditorFrom(runConfiguration)

        // Check that the Docker Compose file field is set to the path of the subdirectory file
        assertEquals(subDirFile.absolutePath, editor.getDockerComposeFileFieldText())
    }
}
