plugins {
    id("java-library")
    id("idea")
    id("eclipse")
    alias(libs.plugins.protobuf)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(libs.protobuf.lite)
}

sourceSets {
    main {
        proto {
            srcDir("main/src/proto")
        }
    }
}

protobuf {
    protoc {
        artifact = libs.protoc.get().toString()
    }

    generateProtoTasks {
        all().forEach {
            it.builtins.findByName("java")?.option("lite")
        }
    }
}
