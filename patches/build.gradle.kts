group = "app.extremetube"

patches {
    about {
        name = "Extreme Tube Patches"
        description = "Security-first YouTube patches with an enhanced all-formats quality selector"
        source = "https://github.com/darkironman-collab/Tube"
        author = "darkironman-collab"
        contact = "https://github.com/darkironman-collab/Tube/issues"
        website = "https://github.com/darkironman-collab/Tube"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    publish {
        dependsOn("generatePatchesList")
    }
}
