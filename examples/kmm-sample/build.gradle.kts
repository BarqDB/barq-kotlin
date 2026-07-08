buildscript {
    extra["ciBuild"] = Barq.ciBuild
    repositories {
        if (extra["ciBuild"] as Boolean) {
            maven(url = "file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.latestKotlin}")
        classpath("com.android.tools.build:gradle:${Versions.Android.buildTools}")
        classpath ("io.github.barqdb.kotlin:gradle-plugin:${Barq.version}")
    }
}
group = "io.github.barqdb.example"
version = Barq.version

allprojects {
    repositories {
        if (rootProject.extra["ciBuild"] as Boolean) {
            maven("file://${rootProject.rootDir.absolutePath}/../../packages/build/m2-buildrepo")
        }
        google()
        mavenCentral()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    }
}
