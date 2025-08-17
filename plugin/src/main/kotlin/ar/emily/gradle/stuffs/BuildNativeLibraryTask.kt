package ar.emily.gradle.stuffs

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import java.io.File
import javax.inject.Inject

abstract class BuildNativeLibraryTask : DefaultTask() {

  @get:Internal
  val libraryName: Property<String> = objects.property(String::class.java)

  @get:Internal
  val platformType: Property<PlatformType> = objects.property(PlatformType::class.java)

  @get:Input
  val cppLanguageVersion: Property<String> = objects.property(String::class.java)

  @get:InputFiles
  val sourceFiles: ConfigurableFileCollection = objects.fileCollection()

  @get:InputFiles
  protected val includeDirectories: ListProperty<String> = objects.listProperty(String::class.java)
  fun includeDirectory(dir: Directory): Unit = includeDirectories.add(dir.asFile.path)
  fun includeDirectory(dir: Provider<out Directory>): Unit = includeDirectories.add(dir.map { it.asFile.path })

  @get:InputFiles
  protected val libraryDirectories: ListProperty<String> = objects.listProperty(String::class.java)
  fun libraryDirectory(dir: Directory): Unit = libraryDirectories.add(dir.asFile.path)
  fun libraryDirectory(dir: Provider<out Directory>): Unit = libraryDirectories.add(dir.map { it.asFile.path })

  @get:Input
  val libraryDependencies: ListProperty<String> = objects.listProperty(String::class.java)

  @get:OutputDirectory
  protected val outputDirectory: Provider<Directory> = layout.buildDirectory.dir("natives")

  @get:Internal
  val outputFile: Provider<RegularFile> = outputDirectory.zip(libraryName.map(System::mapLibraryName), Directory::file)

  @get:Input
  @get:Optional
  protected val cppCompiler: Provider<String> = providers.environmentVariable("CXX")

  @get:Input
  val compilerArguments: ListProperty<String> = objects.listProperty(String::class.java)

  @get:Input
  val linkerArguments: ListProperty<String> = objects.listProperty(String::class.java)

  init {
    platformType.convention(
      if (System.getProperty("os.name").lowercase() == "linux") {
        PlatformType.LINUX
      } else {
        PlatformType.WINDOWS
      }
    )
  }

  @get:Inject
  protected abstract val objects: ObjectFactory

  @get:Inject
  protected abstract val providers: ProviderFactory

  @get:Inject
  protected abstract val layout: ProjectLayout

  @get:Inject
  protected abstract val execOps: ExecOperations

  @TaskAction
  protected fun build() {
    execOps.exec { spec -> spec.args(collectArgs()).executable(cppCompiler.getOrElse("clang++")) }
  }

  private fun collectArgs() = mutableListOf<String>().apply {
    addAll(listOf("-shared", "-x", "c++", "-O3", "-Wall", "-Wextra"))
    add("-o${outputFile.get().asFile.path}")
    add("-std=${cppLanguageVersion.get()}")
    addAll(includeDirectories.get().map { "-I$it" })
    addAll(libraryDirectories.get().map { "-L$it" })
    addAll(libraryDependencies.get().map { "-l$it" })
    addAll(linkerArguments.get().map { "-z$it" })
    addAll(compilerArguments.get())
    addAll(sourceFiles.asFileTree.filter { it.extension == "cpp" }.files.map(File::getPath))
  }
}
