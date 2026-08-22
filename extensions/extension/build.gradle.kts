plugins {
    alias(libs.plugins.protobuf)
}

extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.extremetube.extension"
    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.protobuf.javalite)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}
