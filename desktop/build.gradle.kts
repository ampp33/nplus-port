plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by project
val controllersVersion: String by project

application {
    mainClass.set("com.nplus.desktop.DesktopLauncherKt")
}

// Separate config for gdx-tools (packing tool only, not shipped at runtime)
val gdxTools by configurations.creating { isTransitive = true }

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
    implementation("com.badlogicgames.gdx-controllers:gdx-controllers-desktop:$controllersVersion")
    gdxTools("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
    compileOnly("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
}

kotlin {
    jvmToolchain(17)
}

// Bundle assets into the runnable jar
tasks.jar {
    from("../assets") { into("assets") }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest { attributes["Main-Class"] = application.mainClass.get() }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

// Point the run task at the shared assets folder so Gdx.files.internal() works
// identically to Android (both look inside the assets/ root without a prefix).
tasks.run.configure {
    workingDir = rootProject.file("assets")
}

// Pack all sprites into a single TextureAtlas. Run once before building.
// Output: assets/atlas/sprites.atlas + sprites.png
tasks.register<JavaExec>("packAtlas") {
    group = "nplus"
    description = "Pack assets/sprites/** into assets/atlas/sprites.atlas"
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath + gdxTools
    mainClass.set("com.nplus.tools.PackAtlasKt")
    args = listOf(
        rootProject.file("assets/sprites").absolutePath,
        rootProject.file("assets/atlas").absolutePath
    )
}
