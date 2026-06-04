plugins {
    java
}

group = "cn.zerobot.plugins"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    compileOnly("cn.zerobot:zerobot-plugin-api:0.1.0")
    compileOnly("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.2")
}

tasks.jar {
    archiveBaseName.set("zerobot-luckperms")
}
