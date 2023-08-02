import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.StoredProperty

class DockerComposeBuildRunConfigurationOptions : RunConfigurationOptions() {

    private val myDockerPath: StoredProperty<String?> = string("").provideDelegate(this, "dockerPath")
    private val myDockerComposeFilePath: StoredProperty<String?> = string("").provideDelegate(this, "dockerComposeFilePath")
    private val myCommandArgs: StoredProperty<String?> = string("").provideDelegate(this, "commandArgs")

    var dockerPath: String?
        get() = myDockerPath.getValue(this)
        set(dockerPath) {
            myDockerPath.setValue(this, dockerPath)
        }

    var dockerComposeFilePath: String?
        get() = myDockerComposeFilePath.getValue(this)
        set(dockerComposeFilePath) {
            myDockerComposeFilePath.setValue(this, dockerComposeFilePath)
        }

    var commandArgs: String?
        get() = myCommandArgs.getValue(this)
        set(commandArgs) {
            myCommandArgs.setValue(this, commandArgs)
        }
}
