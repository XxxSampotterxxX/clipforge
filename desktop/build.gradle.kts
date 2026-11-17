import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Preview playback uses VLC. VLC must be installed on the machine that runs the app.
    implementation("uk.co.caprica:vlcj:4.8.3")

    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    useJUnit()
}

compose.desktop {
    application {
        mainClass = "dev.clipforge.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ClipForge"
            packageVersion = "1.0.0"
        }
    }
}
