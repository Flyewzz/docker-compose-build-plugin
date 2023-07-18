import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull

class DockerComposeBuildRunConfiguration(
    project: Project?,
    factory: ConfigurationFactory?,
    name: String?
) :
    RunConfigurationBase<DockerComposeBuildRunConfigurationOptions?>(project!!, factory, name) {
    @NotNull
    override fun getOptions(): DockerComposeBuildRunConfigurationOptions {
        return super.getOptions() as DockerComposeBuildRunConfigurationOptions
    }

    var dockerPath: String?
        get() = options.dockerPath
        set(dockerPath) {
            options.dockerPath = dockerPath
        }

    var commandArgs: String?
        get() = options.commandArgs
        set(commandArgs) {
            options.commandArgs = commandArgs
        }

    @NotNull
    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration?> {
        return DockerComposeBuildSettingsEditor(project)
    }

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment
    ): RunProfileState? {
        return object : CommandLineState(environment) {
            @Throws(ExecutionException::class)
            override fun startProcess(): ProcessHandler {
                var command = arrayListOf(options.dockerPath ?: "")
                if (command[0].isNotEmpty()) {
                    command.add("build")
                }

                options.commandArgs?.let { args ->
                    args
                        .split(" ")
                        .filter { it.isNotBlank() && it.isNotEmpty() }
                        // arguments with '-' are not supported
                        .forEach{ command.add(it.replace('_', '-')) }
                }

                val commandLine = GeneralCommandLine(command.toList())
                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }
}
