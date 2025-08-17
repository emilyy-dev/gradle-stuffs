package ar.emily.gradle.stuffs

enum class ApplicationType(val type: String) {
  APP_IMAGE("app-image"),
  EXE("exe"),
  MSI("msi"),
  RPM("rpm"),
  DEB("deb"),
  PKG("pkg"),
  DMG("dmg"),
}
