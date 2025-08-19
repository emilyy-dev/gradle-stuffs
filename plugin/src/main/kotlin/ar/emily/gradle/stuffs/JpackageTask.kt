package ar.emily.gradle.stuffs

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Jar
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.process.ExecOperations
import java.io.File
import java.lang.module.ModuleFinder
import java.lang.module.ModuleReference
import java.net.URI
import javax.inject.Inject

@CacheableTask
abstract class JpackageTask : DefaultTask() {

  @get:Input
  val applicationName: Property<String> = objects.property(String::class.java)

  @get:Input
  val applicationVersion: Property<String> = objects.property(String::class.java)

  @get:Input
  val applicationType: Property<ApplicationType> = objects.property(ApplicationType::class.java)

  @get:Input
  @get:Optional
  val vendor: Property<String> = objects.property(String::class.java)

  @get:Input
  @get:Optional
  val copyright: Property<String> = objects.property(String::class.java)

  @get:Input
  @get:Optional
  val description: Property<String> = objects.property(String::class.java)

  @get:Input
  @get:Optional
  val aboutUrl: Property<URI> = objects.property(URI::class.java)

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Optional
  val icon: RegularFileProperty = objects.fileProperty()

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  @get:Optional
  val licenseFile: RegularFileProperty = objects.fileProperty()

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val appContent: ConfigurableFileCollection = objects.fileCollection()

  @get:Input
  @get:Optional
  val installDir: Property<String> = objects.property(String::class.java)

  @get:Input
  @get:Optional
  val launcherAsService: Property<Boolean> = objects.property(Boolean::class.java)

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val applicationJar: RegularFileProperty = objects.fileProperty()

  @get:Internal
  val runtimeClasspath: ConfigurableFileCollection = objects.fileCollection()

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val classPath: ConfigurableFileCollection = objects.fileCollection()

  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  val modulePath: ConfigurableFileCollection = objects.fileCollection()

  @get:Input
  @get:Optional
  val mainModule: Property<String> = objects.property(String::class.java)

  @get:Input
  val mainClass: Property<String> = objects.property(String::class.java)

  @get:Input
  val additionalModules: ListProperty<String> = objects.listProperty(String::class.java)

  @get:Input
  val jvmArgs: ListProperty<String> = objects.listProperty(String::class.java)

  @get:Input
  val systemProperties: MapProperty<String, String> = objects.mapProperty(String::class.java, String::class.java)

  @get:Input
  val applicationArgs: ListProperty<String> = objects.listProperty(String::class.java)

  @get:OutputDirectory
  val outputDir: Provider<Directory> = layout.buildDirectory.dir("jpackage")

  @get:Input
  val jlinkOptions: ListProperty<String> = objects.listProperty(String::class.java)

  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.ABSOLUTE)
  @get:Optional
  val runtimeImage: DirectoryProperty = objects.directoryProperty()

  @get:Nested
  val javaLauncher: Property<JavaLauncher> = objects.property(JavaLauncher::class.java)

  @get:Input
  val extraJpackageArgs: ListProperty<String> = objects.listProperty(String::class.java)

  init {
    applicationType.convention(ApplicationType.APP_IMAGE)
    applicationJar.convention(project.tasks.named(JavaPlugin.JAR_TASK_NAME, Jar::class.java).flatMap { it.archiveFile })

    runtimeClasspath.convention(
      objects.fileCollection().from(
        configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME),
        applicationJar
      ) as Any
    )
    classPath.convention(runtimeClasspath.filter { it.isAutomaticModule } as Any)
    modulePath.convention(runtimeClasspath.filter { !it.isAutomaticModule } as Any)

    if (project.plugins.hasPlugin(ApplicationPlugin::class.java)) {
      val application = project.extensions.getByType(JavaApplication::class.java)
      mainModule.convention(application.mainModule)
      mainClass.convention(application.mainClass)
    }

    javaLauncher.convention(
      javaToolchains.launcherFor(project.extensions.getByType(JavaPluginExtension::class.java).toolchain)
    )
  }

  @get:Inject
  protected abstract val objects: ObjectFactory

  @get:Inject
  protected abstract val layout: ProjectLayout

  @get:Inject
  protected abstract val configurations: ConfigurationContainer

  @get:Inject
  protected abstract val javaToolchains: JavaToolchainService

  @get:Inject
  protected abstract val files: FileSystemOperations

  @get:Inject
  protected abstract val execOps: ExecOperations

  @TaskAction
  protected fun runJpackage() {
    val inputDir = temporaryDir.resolve("input")
    val tempDir = temporaryDir.resolve("temp")
    files.delete { spec -> spec.delete(inputDir, tempDir, outputDir) }
    inputDir.mkdir()
    tempDir.mkdir()
    files.copy { spec -> spec.from(classPath).into(inputDir) }
    execOps.exec { spec -> spec.args(collectArguments(inputDir, tempDir)).executable(jpackageExecutable()) }
  }

  private fun jpackageExecutable(): File = javaLauncher.get().executablePath.asFile.resolveSibling("jpackage")

  private fun collectArguments(inputDir: File, tempDir: File) = mutableListOf<String>().apply {
    add("--temp")
    add(tempDir.path)

    add("--dest")
    add(outputDir.get().asFile.path)

    add("--input")
    add(inputDir.path)

    add("--module-path")
    add(modulePath.asPath)

    add("--add-modules")
    add(modulePath.asSequence().map { it.moduleName!! }.plus(additionalModules.get().asSequence()).joinToString(","))

    addAll(
      mainModule.zip(mainClass) { mainModule, mainClass ->
        listOf("--module", "$mainModule/$mainClass")
      }.orElse(applicationJar.zip(mainClass) { mainJar, mainClass ->
        listOf(
          "--main-jar", mainJar.asFile.name,
          "--main-class", mainClass
        )
      }).get()
    )

    jlinkOptions.get().forEach {
      add("--jlink-options")
      add(it)
    }

    jvmArgs.get().forEach {
      add("--java-options")
      add(it)
    }

    systemProperties.get().forEach { (key, value) ->
      add("--java-options")
      add("-D$key=$value")
    }

    applicationArgs.get().forEach {
      add("--arguments")
      add(it)
    }

    add("--name")
    add(applicationName.get())

    vendor.orNull?.also {
      add("--vendor")
      add(it)
    }

    copyright.orNull?.also {
      add("--copyright")
      add(it)
    }

    description.orNull?.also {
      add("--description")
      add(it)
    }

    aboutUrl.orNull?.also {
      add("--about-url")
      add(it.toString())
    }

    icon.orNull?.also {
      add("--icon")
      add(it.asFile.path)
    }

    licenseFile.orNull?.also {
      add("--license-file")
      add(it.asFile.path)
    }

    appContent.files.takeIf(Set<File>::isNotEmpty)?.also { files ->
      add("--app-content")
      add(files.joinToString(",") { file -> file.path })
    }

    installDir.orNull?.also {
      add("--install-dir")
      add(it)
    }

    if (launcherAsService.getOrElse(false)) {
      add("--launcher-as-service")
    }

    addAll(extraJpackageArgs.get())

    runtimeImage.orNull?.also {
      add("--runtime-image")
      add(it.asFile.path)
    }

    add("--app-version")
    add(applicationVersion.get())

    add("--type")
    add(applicationType.get().type)
  }
}

private val File.jarModule: ModuleReference?
  get() = ModuleFinder.of(toPath()).findAll().firstOrNull()

private val File.isAutomaticModule: Boolean
  get() = jarModule?.descriptor()?.isAutomatic ?: true

private val File.moduleName: String?
  get() = jarModule?.descriptor()?.name()
