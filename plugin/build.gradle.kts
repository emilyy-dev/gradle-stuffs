plugins {
  `java-gradle-plugin`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.plugin.publish)
  signing
}

kotlin {
  jvmToolchain(17)
}

repositories {
  mavenCentral()
}

dependencies {
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
  val stuffs by plugins.creating {
    id = "ar.emily.gradle.stuffs"
    implementationClass = "ar.emily.gradle.stuffs.EmilyGradleStuffs"
  }
}

val functionalTestSourceSet = project.sourceSets.create("functionalTest")

project.configurations["functionalTestImplementation"].extendsFrom(project.configurations["testImplementation"])
project.configurations["functionalTestRuntimeOnly"].extendsFrom(project.configurations["testRuntimeOnly"])

val functionalTest by project.tasks.registering(Test::class) {
  testClassesDirs = functionalTestSourceSet.output.classesDirs
  classpath = functionalTestSourceSet.runtimeClasspath
  useJUnitPlatform()
}

gradlePlugin.testSourceSets.add(functionalTestSourceSet)

project.tasks.named<Task>("check") {
  dependsOn(functionalTest)
}

project.tasks.named<Test>("test") {
  useJUnitPlatform()
}

publishing {
  repositories {
    val repoUri =
      if (version.toString().endsWith("-SNAPSHOT")) {
        "https://maven.emily.ar/snapshots"
      } else {
        "https://maven.emily.ar/releases"
      }

    maven {
      name = "emilyMaven"
      url = uri(repoUri)
      credentials(PasswordCredentials::class)
    }
  }
}
