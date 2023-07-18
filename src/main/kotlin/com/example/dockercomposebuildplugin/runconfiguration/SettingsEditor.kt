import com.intellij.codeInsight.completion.CompletionResultSet
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

class DockerComposeBuildSettingsEditor(private val project: Project) : SettingsEditor<DockerComposeBuildRunConfiguration?>() {
    private val myPanel: JPanel

    private val dockerPathField: TextFieldWithBrowseButton
    private val commandArgsField: TextFieldWithCompletion
    private val myOptions: Options = Options()
        .addOption("build_arg", true, "Set build-time variables for services")
        .addOption("compress", false, "Compress the build context using gzip")
        .addOption("force_rm", false, "Always remove intermediate containers")
        .addOption("m", "memory", true, "Set memory limit for the build container")
        .addOption("no_cache", false, "Do not use cache when building the image")
        .addOption("no_rm", false, "Do not remove intermediate containers after a successful build")
        .addOption("parallel", false, "Build images in parallel")
        .addOption("progress", true, "Set type of progress output (auto, plain, tty)")
        .addOption("pull", false, "Always attempt to pull a newer version of the image")
        .addOption("q", "quiet", false, "Don't print anything to STDOUT")
        .addOption("f", "file", true, "Path to docker-compose.yml file")

    init {
        dockerPathField = TextFieldWithBrowseButton()
        dockerPathField.addBrowseFolderListener(
            "Select Docker Compose path", null, null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        commandArgsField =
            TextFieldWithCompletion(project, DockerComposeCompletionProvider(myOptions), "", true, true, true)
        myPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Docker Compose path", dockerPathField)
            .addLabeledComponent("Docker Compose arguments", commandArgsField)
            .panel
    }

    override fun resetEditorFrom(runConfiguration: DockerComposeBuildRunConfiguration) {
        val defaultPath = when {
            SystemInfo.isWindows -> "C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker-compose.exe"
            SystemInfo.isMac -> "/usr/local/bin/docker-compose"
            SystemInfo.isLinux -> "/usr/bin/docker-compose"
            else -> ""
        }

        if (defaultPath.isNotEmpty() && File(defaultPath).canExecute()) {
            runConfiguration.dockerPath = defaultPath
        }

        dockerPathField.text = runConfiguration.dockerPath ?: defaultPath
        commandArgsField.text = runConfiguration.commandArgs ?: ""
    }

    override fun applyEditorTo(runConfiguration: DockerComposeBuildRunConfiguration) {
        // Check if the specified Docker Compose path is valid
        val dockerPath = dockerPathField.text
        if (!File(dockerPath).canExecute()) {
            throw ConfigurationException("Invalid Docker Compose path")
        }

        try {
            DefaultParser().parse(myOptions, commandArgsField.text.split(" ").toTypedArray())
        } catch (e: ParseException) {
            throw ConfigurationException("Invalid Docker Compose arguments: ${e.message}")
        }

        runConfiguration.dockerPath = dockerPath
        runConfiguration.commandArgs = commandArgsField.text
    }

    override fun createEditor(): JComponent {
        return myPanel
    }
}

class DockerComposeCompletionProvider(options: Options?) : CommandLineCompletionProvider(options) {
    override fun addArgumentVariants(result: CompletionResultSet) {}
}
