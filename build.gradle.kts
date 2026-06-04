plugins {
    java
}

group = "your.group"
version = "1.0.0"

dependencies {
    compileOnly("cn.zerobot:zerobot-plugin-api:0.1.0")
}

tasks.jar {
    archiveBaseName.set("zerobot-plugin-template")
}
