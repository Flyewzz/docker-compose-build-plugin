import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.FormBuilder
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.commons.cli.Option
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


open class DockerComposeBuildSettingsEditor(project: Project) : SettingsEditor<DockerComposeBuildRunConfiguration?>() {
    private val myPanel: JPanel
    private val myDisposable = Disposer.newDisposable()

    protected var dockerPathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    protected val commandArgsField: TextFieldWithCompletion
    protected val dockerComposeFileField = TextFieldWithBrowseButton()

    private val myOptions: Options = Options()
        .addOption(null,"build_arg", true, "Set build-time variables for services")
        .addOption(null,"compress", false, "Compress the build context using gzip")
        .addOption(null,"force_rm", false, "Always remove intermediate containers")
        .addOption("m", "memory", true, "Set memory limit for the build container")
        .addOption(null, "no_cache", false, "Do not use cache when building the image")
        .addOption(null, "no_rm", false, "Do not remove intermediate containers after a successful build")
        .addOption(null,"parallel", false, "Build images in parallel")
        .addOption(null,"progress", true, "Set type of progress output (auto, plain, tty)")
        .addOption(null,"pull", false, "Always attempt to pull a newer version of the image")
        .addOption("q", "quiet", false, "Don't print anything to STDOUT")

    init {
        dockerPathField.addBrowseFolderListener(
            "Select Docker Compose path", null, null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        val fileChooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false).withFileFilter {
            it.extension in listOf("yml", "yaml")
        }
        dockerComposeFileField.addBrowseFolderListener(TextBrowseFolderListener(fileChooserDescriptor))

        commandArgsField =
            TextFieldWithCompletion(project, DockerComposeCompletionProvider(myOptions), "", true, true, true)
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Docker Compose path", dockerPathField)
            .addLabeledComponent("Docker Compose config path", dockerComposeFileField)
            .addLabeledComponent("Docker Compose arguments", commandArgsField)
            .panel
    }

    @VisibleForTesting
    public override fun resetEditorFrom(runConfiguration: DockerComposeBuildRunConfiguration) {
        val defaultPath = when {
            SystemInfo.isWindows -> "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker-compose.exe"
            SystemInfo.isMac -> "/usr/local/bin/docker-compose"
            SystemInfo.isLinux -> "/usr/bin/docker-compose"
            else -> ""
        }

        if (defaultPath.isNotEmpty() && File(defaultPath).canExecute()) {
            runConfiguration.dockerPath = defaultPath
        }

        val indicator = EmptyProgressIndicator()
        findDockerComposeFileAsync(myDisposable, runConfiguration.project, indicator) { dockerComposeFile ->
            if (dockerComposeFile != null) {
                runConfiguration.dockerComposeFilePath = dockerComposeFile.path
            }

            dockerPathField.text = runConfiguration.dockerPath ?: defaultPath
            dockerComposeFileField.text = runConfiguration.dockerComposeFilePath ?: ""
            commandArgsField.text = runConfiguration.commandArgs ?: ""
        }
    }

    @VisibleForTesting
    public override fun applyEditorTo(runConfiguration: DockerComposeBuildRunConfiguration) {
        // Check if the specified Docker Compose path is valid
        val dockerPath = dockerPathField.text
        if (!File(dockerPath).canExecute()) {
            throw ConfigurationException("Invalid Docker Compose path")
        }

        val dockerComposeFilePath = dockerComposeFileField.text
        if (dockerComposeFilePath.isEmpty() || !File(dockerComposeFilePath).exists()) {
            throw ConfigurationException("Invalid Docker Compose config path")
        }

        try {
            DefaultParser().parse(myOptions, commandArgsField.text.split(" ").toTypedArray())
        } catch (e: ParseException) {
            throw ConfigurationException("Invalid Docker Compose arguments: ${e.message}")
        }

        val args = commandArgsField.text
            .split(" ")
            .filter { it.isNotBlank() && it.isNotEmpty() }

        val optionsMap = myOptions.options.flatMap { listOf(it.opt to it, it.longOpt to it) }.toMap()

        val argCount = mutableMapOf<Option, Int>()

        for (arg in args) {
            val argWithoutPrefix = arg.removePrefix("--").removePrefix("-")
            val option = optionsMap[argWithoutPrefix]
            if (option != null) {
                argCount[option] = argCount.getOrDefault(option, 0) + 1
                if (argCount[option]!! > 1) {
                    throw ConfigurationException("$arg option cannot be used more than once")
                }
            }
        }

        runConfiguration.dockerPath = dockerPath
        runConfiguration.dockerComposeFilePath = dockerComposeFilePath
        runConfiguration.commandArgs = commandArgsField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }

    override fun disposeEditor() {
        Disposer.dispose(myDisposable)
    }

}

class DockerComposeCompletionProvider(options: Options?) : CommandLineCompletionProvider(options) {
    override fun addArgumentVariants(result: CompletionResultSet) {}
}

fun findDockerComposeFile(project: Project): VirtualFile? {
    val basePath = project.basePath ?: return null
    val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(basePath)) ?: return null

    val directoriesToCheck = ArrayDeque<VirtualFile>()
    val visitedPaths = HashSet<Path>()
    directoriesToCheck.addFirst(projectDir)

    while (directoriesToCheck.isNotEmpty()) {
        val directory = directoriesToCheck.removeFirst()
        val path = Paths.get(directory.path)

        if (Files.isSymbolicLink(path)) {
            val targetPath = Files.readSymbolicLink(path)
            if (!visitedPaths.add(targetPath)) continue
        } else {
            if (!visitedPaths.add(path)) continue
        }

        val dockerComposeFile = directory.children.find {
            it.extension in listOf("yml", "yaml") && it.nameWithoutExtension == "docker-compose"
        }

        if (dockerComposeFile != null) {
            return dockerComposeFile
        } else {
            directoriesToCheck.addAll(directory.children.filter { it.isDirectory })
        }
    }

    // No docker-compose file was found
    return null
}

fun findDockerComposeFileAsync(
    disposable: Disposable, project: Project, indicator: ProgressIndicator, callback: (VirtualFile?) -> Unit) {
    ReadAction.nonBlocking<VirtualFile?> {
        findDockerComposeFile(project)
    }
        .expireWith(disposable)
        .wrapProgress(indicator)
        .finishOnUiThread(ModalityState.any()) { result -> callback(result) }
        .submit(AppExecutorUtil.getAppExecutorService())
}