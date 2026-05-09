plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0" apply false
}

tasks.register<Exec>("checkI18n") {
    group = "verification"
    description = "Checks for obvious hard-coded user-facing English strings in Kotlin UI code."
    commandLine("bash", "$rootDir/tools/check-i18n.sh")
}
