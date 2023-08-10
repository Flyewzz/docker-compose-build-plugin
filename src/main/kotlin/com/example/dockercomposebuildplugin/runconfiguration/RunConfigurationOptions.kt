import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class DockerComposeBuildRunConfigurationOptions : RunConfigurationOptions() {

    private val myDockerPath: StoredProperty<String?> = string("").provideDelegate(this, "dockerPath")
    private val myDockerComposeFiles: StoredProperty<String?> = string("").provideDelegate(this, "dockerComposeFiles")
    private val myCommandArgs: StoredProperty<String?> = string("").provideDelegate(this, "commandArgs")

    var dockerPath: String?
        get() = myDockerPath.getValue(this)
        set(dockerPath) {
            myDockerPath.setValue(this, dockerPath)
        }

    var dockerComposeFiles: List<String>
        get() {
            val files = myDockerComposeFiles.getValue(this)
            return if (files?.isNotBlank() == true) files.split(";") else emptyList()
        }
        set(value) {
            myDockerComposeFiles.setValue(this, value.joinToString(";"))
        }


    var commandArgs: String?
        get() = myCommandArgs.getValue(this)
        set(commandArgs) {
            myCommandArgs.setValue(this, commandArgs)
        }
}
