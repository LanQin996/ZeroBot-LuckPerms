# ZeroBot 插件模板

这是一个可以独立复制出去的新插件模板。

插件开发者不需要下载整个 ZeroBot 源码，只需要能从 Maven 仓库拉到：

```text
cn.zerobot:zerobot-plugin-api:0.1.0
```

`zerobot-plugin-api` 是插件编译期依赖，ZeroBot 主程序运行时会提供这套 API。

## 复制后需要修改

1. 修改 `build.gradle.kts`：

```kotlin
plugins {
    java
}

group = "your.group"
version = "1.0.0"

dependencies {
    compileOnly("cn.zerobot:zerobot-plugin-api:0.1.0")
}

tasks.jar {
    archiveBaseName.set("你的插件 jar 名称")
}
```

仓库配置在 `settings.gradle.kts` 里，当前使用：

```kotlin
maven {
    url = uri("https://nexus.jsdu.cn/repository")
}
```

2. 修改 `src/main/resources/plugin.yml`：

```yml
id: your-plugin-id
name: Your Plugin Name
version: 1.0.0
main: your.package.YourPlugin
```

3. 修改 Java 包名和插件主类。

`plugin.yml` 里的 `main` 必须等于插件主类的完整类名。

## 构建模板

在插件项目根目录执行：

```powershell
.\gradlew.bat jar
```

生成的插件 jar：

```text
build\libs\zerobot-plugin-template-1.0.0.jar
```

把 jar 放进 ZeroBot 运行目录的 `plugins` 文件夹，然后在 ZeroBot 控制台执行：

```text
reload-all
```

## 常用监听

```java
context.onGroupMessage(event -> {});    // 群消息
context.onPrivateMessage(event -> {});  // 私聊消息
context.onNotice(event -> {});          // 通知事件
context.onRequest(event -> {});         // 请求事件
context.onEvent(event -> {});           // 所有事件
```

## 权限节点

模板已经示范了权限判断：

```java
if (!context.hasPermission(event, "template.ping", true)) {
    return;
}
```

ZeroBot 默认只读取主配置里的超级管理员：

```yml
superAdmins:
  - "123456"
```

更细的权限组、继承和临时权限可以由后续权限管理插件接管。

如果命令不需要权限，直接不要调用 `hasPermission()`。
如果命令有权限节点但默认所有人可用，使用第三个参数 `true`。

## 插件配置

模板已经示范了插件配置：

```java
Settings settings = context.loadConfig("config.yml", Settings.class);
```

首次加载插件时，ZeroBot 会自动生成：

```text
config/<插件ID>/config.yml
```

插件运行数据可以放到：

```java
context.dataDir()
```

默认目录：

```text
data/<插件ID>/
```
