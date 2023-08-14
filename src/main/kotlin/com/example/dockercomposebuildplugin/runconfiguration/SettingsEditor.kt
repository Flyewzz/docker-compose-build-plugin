import com.example.dockercomposebuildplugin.runconfiguration.CancellableBackgroundableTask
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.service.execution.cmd.CommandLineCompletionProvider
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.FormBuilder
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.ParseException
import org.jetbrains.annotations.VisibleForTesting
import java.awt.*
import java.awt.event.ActionEvent
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.*

class DockerComposeFilesPanel(model: DefaultListModel<String>) : JPanel() {
    private val filesListView = JBList(model)

    private val loadingLabel = JLabel("Loading...").apply {
        horizontalAlignment = JLabel.CENTER
    }

    private var loadingDotCount = 0
    private val loadingTimer = Timer(500) {
        loadingDotCount = (loadingDotCount + 1) % 4
        loadingLabel.text = "Loading" + ".".repeat(loadingDotCount)
    }

    private val loadingPanel = createLoadingPanel()
    private var cancelLoading: (() -> Unit)? = null

    private val addDockerComposeFileButton = JButton("Add")
    private val removeDockerComposeFileButton = JButton("Remove")

    init {
        layout = CardLayout()
        add(createListViewPanel(), LIST_VIEW)
        add(loadingPanel, LOADING_VIEW)
    }

    fun setLoadingState(loading: Boolean) {
        val cardLayout = layout as CardLayout
        if (loading) {
            cardLayout.show(this, LOADING_VIEW)
            // Start the timer when entering the loading state
            loadingTimer.start()
        } else {
            // Stop the timer when leaving the loading state
            loadingTimer.stop()
            cardLayout.show(this, LIST_VIEW)
        }
    }

    internal fun setCancelAction(cancelAction: (() -> Unit)?) {
        cancelLoading = cancelAction
    }

    internal fun setAddAction(addAction: ((ActionEvent) -> Unit)) {
        addDockerComposeFileButton.addActionListener(addAction)
    }

    internal fun setRemoveAction(removeAction: ((ActionEvent) -> Unit)) {
        removeDockerComposeFileButton.addActionListener(removeAction)
    }

    private fun createLoadingPanel(): JPanel {
        val loadingPanel = JPanel(BorderLayout())

        // Wrap the loading label in a JPanel and center it
        val centerPanel = JPanel(GridBagLayout())
        centerPanel.add(loadingLabel)

        // Create a JBScrollPane to simulate the appearance of a list
        val loadingScrollPane = JBScrollPane(centerPanel)

        val cancelButton = JButton("Cancel").apply {
            addActionListener {
                cancelLoading?.invoke()
            }
        }

        loadingPanel.add(loadingScrollPane, BorderLayout.CENTER)
        loadingPanel.add(cancelButton, BorderLayout.SOUTH)

        return loadingPanel
    }

    private fun createListViewPanel(): JPanel {
        val listViewPanel = JPanel(BorderLayout())
        listViewPanel.add(JScrollPane(filesListView), BorderLayout.CENTER)

        val buttonPanel = JPanel()
        buttonPanel.layout = GridLayout(1, 2)
        buttonPanel.add(addDockerComposeFileButton)
        buttonPanel.add(removeDockerComposeFileButton)
        listViewPanel.add(buttonPanel, BorderLayout.SOUTH)

        return listViewPanel
    }

    fun getFilesListView(): JBList<String> {
        return filesListView
    }

    companion object {
        private const val LOADING_VIEW = "LoadingView"
        private const val LIST_VIEW = "ListView"
    }
}

open class DockerComposeBuildSettingsEditor(project: Project) : SettingsEditor<DockerComposeBuildRunConfiguration?>(),
    Disposable {
    private val myPanel: JPanel

    private var dockerPathField: TextFieldWithBrowseButton = TextFieldWithBrowseButton()
    private val commandArgsField: TextFieldWithCompletion

    private val dockerComposeFilesListModel: DefaultListModel<String> = DefaultListModel()
    private val dockerComposeFilesListView = DockerComposeFilesPanel(dockerComposeFilesListModel)

    private var myBackgroundableTask: CancellableBackgroundableTask? = null
    
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

        dockerComposeFilesListView.preferredSize = Dimension(200, 150)

        val dockerComposeFilePanel = JPanel()
        dockerComposeFilePanel.layout = BorderLayout()
        dockerComposeFilePanel.add(JScrollPane(dockerComposeFilesListView), BorderLayout.CENTER)

        commandArgsField =
            TextFieldWithCompletion(project, DockerComposeCompletionProvider(myOptions), "", true, true, true)
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Docker Compose path", dockerPathField)
            .addLabeledComponent("Docker Compose config files", dockerComposeFilesListView)
            .addLabeledComponent("Docker Compose arguments", commandArgsField)
            .panel

        dockerComposeFilesListView.setAddAction {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, true).withFileFilter {
                it.extension in listOf("yml", "yaml")
            }
            val files = FileChooser.chooseFiles(descriptor, myPanel, project, null).toList()
            // Add files and display a message if they are already in the list
            if (addFilesToDockerComposeFilesList(files)) {
                JOptionPane.showMessageDialog(myPanel, "One or more selected files are already in the list.", "Duplicate Files", JOptionPane.WARNING_MESSAGE)
            }
        }

        dockerComposeFilesListView.setRemoveAction {
            val selectedIndices = dockerComposeFilesListView.getFilesListView().selectedIndices
            for (i in selectedIndices.reversed()) {
                dockerComposeFilesListModel.removeElementAt(i)
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
                updateDockerComposeFilesListModel(dockerComposeFiles.map { it.path })
            }
        } else {
            updateDockerComposeFilesListModel(runConfiguration.dockerComposeFiles)
        }
    }

    @VisibleForTesting
    public override fun applyEditorTo(runConfiguration: DockerComposeBuildRunConfiguration) {
        // Check if the specified Docker Compose path is valid
        val dockerPath = dockerPathField.text
        if (!File(dockerPath).canExecute()) {
            throw ConfigurationException("Invalid Docker Compose path")
        }

        val dockerComposeElemFilesList = dockerComposeFilesListModel.elements().toList()
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
        if (dockerComposeFilesListModel.elements().toList().isNotEmpty()) {
            runConfiguration.dockerComposeFiles = dockerComposeFilesListModel.elements().toList()
        }
        runConfiguration.commandArgs = commandArgsField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }

    override fun disposeEditor() {
        myBackgroundableTask?.cancel()
        super.disposeEditor()
    }

    open fun updateDockerComposeFilesListModel(dockerComposeFiles: List<String>) {
        dockerComposeFilesListModel.clear()
        dockerComposeFiles.forEach { dockerComposeFilesListModel.addElement(it) }
    }

    open fun loadDockerComposeFiles(runConfigurationProject: Project, action: (List<VirtualFile>) -> Unit) {
        myBackgroundableTask =  object : CancellableBackgroundableTask(runConfigurationProject, "Loading Docker Compose config files") {
            override fun run(indicator: ProgressIndicator) {
                super.run(indicator)

                dockerComposeFilesListView.setCancelAction { indicator.cancel() }
                dockerComposeFilesListView.setLoadingState(true)

                val files = findAllDockerComposeFiles(runConfigurationProject, indicator)
                action(files)
            }

            override fun onFinished() {
                dockerComposeFilesListView.setLoadingState(false)
            }
        }

        myBackgroundableTask!!.queue()
    }

    private fun addFilesToDockerComposeFilesList(files: List<VirtualFile>): Boolean {
        var duplicateFilesFound = false
        files.forEach { file ->
            val path = file.path
            if (!dockerComposeFilesListModel.contains(path)) {
                dockerComposeFilesListModel.addElement(path)
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
    fun setDockerComposeFilesListModel(pathsList: List<String>) {
        dockerComposeFilesListModel.clear()
        pathsList.forEach { dockerComposeFilesListModel.addElement(it) }
    }

    @VisibleForTesting
    fun getDockerComposeFilesListModel(): List<String> {
        return dockerComposeFilesListModel.elements().toList()
    }
}

class DockerComposeCompletionProvider(options: Options?) : CommandLineCompletionProvider(options) {
    override fun addArgumentVariants(result: CompletionResultSet) {}
}

fun findAllDockerComposeFiles(project: Project, indicator: ProgressIndicator): List<VirtualFile> {
    val basePath = project.basePath ?: return emptyList()
    val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(basePath)) ?: return emptyList()

    val directoriesToCheck = ArrayDeque<VirtualFile>()
    val visitedPaths = HashSet<Path>()
    directoriesToCheck.addFirst(projectDir)

    val dockerComposeFiles = mutableListOf<VirtualFile>()

    while (directoriesToCheck.isNotEmpty()) {
        if (indicator.isCanceled) {
            return emptyList()
        }

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