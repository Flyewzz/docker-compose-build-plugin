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
                val dockerPath = options.dockerPath ?: ""
                if (dockerPath.isEmpty()) {
                    throw ExecutionException("docker-compose path is not specified")
                }

                val command = mutableListOf(dockerPath)

                val args = options.commandArgs
                    ?.split(" ")
                    ?.filter { it.isNotBlank() && it.isNotEmpty() }
                    ?.map { it.replace("_", "-") }
                    ?.toMutableList()
                if (args != null) {
                    var fileIndex = args.indexOf("-f")
                    if (fileIndex == -1) {
                        fileIndex = args.indexOf("--file")
                    }

                    if (fileIndex != -1 && fileIndex < args.size - 1) {
                        // If `-f` option is found, move it and its value to the correct position
                        command.add("-f")
                        command.add(args[fileIndex + 1])
                        args.removeAt(fileIndex + 1)
                        args.remove("-f")
                        args.remove("--file")
                    }

                    command.add("build")
                    command.addAll(args)
                } else {
                    command.add("build")
                }

                val commandLine = GeneralCommandLine(command)

                // Set the working directory to the project base path
                val basePath = environment.project.basePath
                if (basePath != null) {
                    commandLine.withWorkDirectory(basePath)
                } else {
                    throw ExecutionException("Project base path is null")
                }

                val processHandler = ProcessHandlerFactory.getInstance()
                    .createColoredProcessHandler(commandLine)
                ProcessTerminatedListener.attach(processHandler)
                return processHandler
            }
        }
    }
}
