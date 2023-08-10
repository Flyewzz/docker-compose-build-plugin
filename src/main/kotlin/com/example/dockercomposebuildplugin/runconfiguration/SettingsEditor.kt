import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider
import com.intellij.openapi.fileChooser.FileChooser
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
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.util.concurrency.AppExecutorUtil
import org.apache.commons.cli.Option
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.GridLayout
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.*


open class DockerComposeBuildSettingsEditor(project: Project) : SettingsEditor<DockerComposeBuildRunConfiguration?>() {
    private val myPanel: JPanel
    private val myDisposable = Disposer.newDisposable()

    protected var dockerPathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    protected val commandArgsField: TextFieldWithCompletion

    protected val dockerComposeFilesList: DefaultListModel<String> = DefaultListModel()
    protected val dockerComposeFilesListView: JBList<String> = JBList(dockerComposeFilesList)
    
    protected val addDockerComposeFileButton: JButton = JButton("Add")
    protected val removeDockerComposeFileButton: JButton = JButton("Remove")

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
            "Select Docker Compose Path", null, null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        removeDockerComposeFileButton.addActionListener {
            val selectedIndices = dockerComposeFilesListView.selectedIndices
            for (i in selectedIndices.reversed()) {
                dockerComposeFilesList.removeElementAt(i)
            }
        }

        val dockerComposeFilePanel = JPanel()
        dockerComposeFilePanel.layout = BorderLayout()
        dockerComposeFilePanel.add(JScrollPane(dockerComposeFilesListView), BorderLayout.CENTER)

        val buttonPanel = JPanel()
        buttonPanel.layout = GridLayout(1, 2)
        buttonPanel.add(addDockerComposeFileButton)
        buttonPanel.add(removeDockerComposeFileButton)
        dockerComposeFilePanel.add(buttonPanel, BorderLayout.SOUTH)

        commandArgsField =
            TextFieldWithCompletion(project, DockerComposeCompletionProvider(myOptions), "", true, true, true)
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Docker Compose path", dockerPathField)
            .addLabeledComponent("Docker Compose config files", dockerComposeFilePanel)
            .addLabeledComponent("Docker Compose arguments", commandArgsField)
            .panel

        addDockerComposeFileButton.addActionListener {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, true).withFileFilter {
                it.extension in listOf("yml", "yaml")
            }
            val files = FileChooser.chooseFiles(descriptor, myPanel, project, null).toList()
            // Add files and display a message if they are already in the list
            if (addFilesToDockerComposeFilesList(files)) {
                JOptionPane.showMessageDialog(myPanel, "One or more selected files are already in the list.", "Duplicate Files", JOptionPane.WARNING_MESSAGE)
            }
        }
    }

    @VisibleForTesting
    public override fun resetEditorFrom(runConfiguration: DockerComposeBuildRunConfiguration) {
        val defaultPath = when {
            SystemInfo.isWindows -> "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker-compose.exe"
            SystemInfo.isMac -> "/usr/local/bin/docker-compose"
            SystemInfo.isLinux -> "/usr/bin/docker-compose"
            else -> ""
        }

        var dockerPath = runConfiguration.dockerPath
        if (dockerPath.isNullOrEmpty() && defaultPath.isNotEmpty() && File(defaultPath).canExecute()) {
            dockerPath = defaultPath
        }

        dockerPathField.text = dockerPath!!
        commandArgsField.text = runConfiguration.commandArgs ?: ""

        if (runConfiguration.dockerComposeFiles.isEmpty()) {
            loadDockerComposeFiles(runConfiguration.project) { dockerComposeFiles ->
                updateDockerComposeFilesList(dockerComposeFiles.map { it.path })
            }
        } else {
            updateDockerComposeFilesList(runConfiguration.dockerComposeFiles)
        }
    }

    @VisibleForTesting
    public override fun applyEditorTo(runConfiguration: DockerComposeBuildRunConfiguration) {
        // Check if the specified Docker Compose path is valid
        val dockerPath = dockerPathField.text
        if (!File(dockerPath).canExecute()) {
            throw ConfigurationException("Invalid Docker Compose path")
        }

        val dockerComposeElemFilesList = dockerComposeFilesList.elements().toList()
        if (dockerComposeElemFilesList.isEmpty()) {
            throw ConfigurationException("No Docker Compose config files specified")
        }

        val invalidDockerComposeFilePaths = dockerComposeElemFilesList.filter { !File(it).exists() }
        if (invalidDockerComposeFilePaths.isNotEmpty()) {
            throw ConfigurationException("Invalid Docker Compose config paths")
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
        if (dockerComposeFilesList.elements().toList().isNotEmpty()) {
            runConfiguration.dockerComposeFiles = dockerComposeFilesList.elements().toList()
        }
        runConfiguration.commandArgs = commandArgsField.text
    }

    open fun updateDockerComposeFilesList(dockerComposeFiles: List<String>) {
        dockerComposeFilesList.clear()
        dockerComposeFiles.forEach { dockerComposeFilesList.addElement(it) }
    }

    open fun loadDockerComposeFiles(runConfigurationProject: Project, action: (List<VirtualFile>) -> Unit) {
        val indicator = EmptyProgressIndicator()
        findAllDockerComposeFilesAsync(myDisposable, runConfigurationProject, indicator, action)
    }

    private fun addFilesToDockerComposeFilesList(files: List<VirtualFile>): Boolean {
        var duplicateFilesFound = false
        files.forEach { file ->
            val path = file.path
            if (!dockerComposeFilesList.contains(path)) {
                dockerComposeFilesList.addElement(path)
            } else {
                duplicateFilesFound = true
            }
        }

        return duplicateFilesFound
    }

    @VisibleForTesting
    fun setDockerPathFieldText(text: String) {
        dockerPathField.text = text
    }

    @VisibleForTesting
    fun setCommandArgsFieldText(text: String) {
        commandArgsField.text = text
    }

    @VisibleForTesting
    fun setDockerComposeFilesList(pathsList: List<String>) {
        dockerComposeFilesList.clear()
        pathsList.forEach { dockerComposeFilesList.addElement(it) }
    }

    @VisibleForTesting
    fun getDockerComposeFilesList(): List<String> {
        return dockerComposeFilesList.elements().toList()
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

fun findAllDockerComposeFiles(project: Project): List<VirtualFile> {
    val basePath = project.basePath ?: return emptyList()
    val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(basePath)) ?: return emptyList()

    val directoriesToCheck = ArrayDeque<VirtualFile>()
    val visitedPaths = HashSet<Path>()
    directoriesToCheck.addFirst(projectDir)

    val dockerComposeFiles = mutableListOf<VirtualFile>()

    while (directoriesToCheck.isNotEmpty()) {
        val directory = directoriesToCheck.removeFirst()
        // Resolves only the actual path, following symbolic links
        val path = Paths.get(directory.path).toRealPath()

        // If path has already been visited, skip it
        if (!visitedPaths.add(path)) continue

        dockerComposeFiles += directory.children.filter {
            it.extension in listOf("yml", "yaml") && it.nameWithoutExtension.contains("docker-compose")
        }

        directoriesToCheck.addAll(directory.children.filter { it.isDirectory })
    }

    return dockerComposeFiles
}

fun findAllDockerComposeFilesAsync(
    disposable: Disposable, project: Project, indicator: ProgressIndicator, callback: (List<VirtualFile>) -> Unit) {
    ReadAction.nonBlocking<List<VirtualFile>> {
        findAllDockerComposeFiles(project)
    }
        .expireWith(disposable)
        .wrapProgress(indicator)
        .finishOnUiThread(ModalityState.defaultModalityState()) { result -> callback(result) }
        .submit(AppExecutorUtil.getAppExecutorService())
}