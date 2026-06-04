# ZeroBot LuckPerms

ZeroBot LuckPerms 是一个 LuckPerms 风格的权限组插件，用来接管 ZeroBot 的 `context.hasPermission(...)` 判断。

## 功能

- 用户权限节点
- 权限组和用户加入权限组
- 权限组继承
- 权限组权重
- `*` 和 `xxx.*` 通配权限
- 显式拒绝权限，例如 `false`
- 上下文权限，例如 `group=123456`、`admin=true`
- YAML 数据持久化
- `/lp` 管理命令，别名由 `plugin.yml` 注册

内置超级管理员仍然有效：插件会先询问 ZeroBot 原有权限服务，再判断权限组数据。

## 构建

```powershell
.\gradlew.bat jar
```

生成文件：

```text
build\libs\zerobot-luckperms-1.0.0.jar
```

把 JAR 放到 ZeroBot 运行目录的 `plugins` 文件夹，然后在控制台执行：

```text
plugin load zerobot-luckperms-1.0.0.jar
```

或重载全部插件：

```text
plugin reload-all
```

## 配置

首次加载会生成：

```text
config/luckperms/config.yml
```

默认配置：

```yml
dataFile: "permissions.yml"
defaultGroup: "default"
createAdminGroup: true
```

命令入口、别名、权限节点和无权限提示由 `plugin.yml` 的 `commands` 统一注册。

`defaultGroup` 是所有用户自动继承的基础权限组，默认是 `default`，不需要给每个用户手动添加。

如果没有给用户授权 `luckperms.admin`，ZeroBot 主配置里的 `superAdmins` 仍然可以使用管理命令。

## 命令

`plugin.yml` 已注册 `lp` 命令，别名为 `luckperms`。下面以 `/lp` 为例：

```text
/lp help
/lp groups
/lp group <组名> create [权重]
/lp group <组名> delete
/lp group <组名> info
/lp group <组名> weight <整数>
/lp group <组名> permission set <权限节点> [true|false] [key=value...]
/lp group <组名> permission unset <权限节点> [key=value...]
/lp group <组名> parent add <父组>
/lp group <组名> parent remove <父组>
/lp user <QQ> info
/lp user <QQ> parent add <组名>
/lp user <QQ> parent remove <组名>
/lp user <QQ> permission set <权限节点> [true|false] [key=value...]
/lp user <QQ> permission unset <权限节点> [key=value...]
/lp check <QQ> <权限节点> [群号] [key=value...]
/lp reload
/lp save
```

示例：

```text
/lp group vip create 10
/lp group vip permission set zerobot.echo true
/lp user 123456 parent add vip
/lp check 123456 zerobot.echo
/lp group vip permission set zerobot.echo true group=987654
/lp check 123456 zerobot.echo group=987654
```

常用上下文：

```text
type=group
type=private
group=<群号>
level=member
level=administrator
level=owner
admin=true
admin=false
```

## 数据文件

数据保存在：

```text
data/luckperms/permissions.yml
```

示例：

```yml
groups:
  default:
    name: "default"
    weight: 0
    parents: []
    permissions: {}
  admin:
    name: "admin"
    weight: 100
    parents: []
    permissions:
      "*": true
      luckperms.admin: true
users:
  "123456":
    groups:
      - "admin"
    permissions: {}
```
