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
  plugins.create("stuffs") {
    id = "ar.emily.gradle.stuffs"
    implementationClass = "ar.emily.gradle.stuffs.EmilyGradleStuffs"
  }
}

val functionalTestSourceSet = project.sourceSets.create("functionalTest")
gradlePlugin.testSourceSets.add(functionalTestSourceSet)

project.configurations {
  named("functionalTestImplementation") { extendsFrom(named("testImplementation")) }
  named("functionalTestRuntimeOnly") { extendsFrom(named("testRuntimeOnly")) }
}

project.tasks {
  val functionalTest =
    register<Test>("functionalTest") {
      testClassesDirs = functionalTestSourceSet.output.classesDirs
      classpath = functionalTestSourceSet.runtimeClasspath
      useJUnitPlatform()
    }


  named<Task>("check") {
    dependsOn(functionalTest)
  }

  named<Test>("test") {
    useJUnitPlatform()
  }
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
