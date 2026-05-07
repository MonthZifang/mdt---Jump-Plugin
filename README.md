[go-Mindustry](https://github.com/tomorrowsetout/go-Mindustry)

<div align="center">
  <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH">
    <img src="./md/logo.png" alt="月月岛科技 Logo" width="720" />
  </a>

  <p><strong>月月岛科技维护 MDT Jump Plugin</strong></p>

  <p>
    <a href="https://github.com/MonthZifang/YUEYUEDAO-TECH"><strong>查看月月岛科技详情</strong></a>
  </p>
</div>

# mdt Jump Plugin

这是一个 Mindustry 服务端插件模板，用于为每个玩家 UUID 生成唯一的四位 `com id`，并提供查询、反查、远程数据库存储与 HTTP 接口能力。

## 构建

```powershell
.\gradlew.bat jar
```

构建完成后会生成：

```text
build/libs/mdt-jump-plugin.jar
dist/mdt-jump-plugin.jar
```

## 配置文件

插件首次启动时会自动创建：

```text
config/mdt-jump-plugin/plugin-config.properties
```

配置文件名使用英文，配置键按你的要求保留中文。

## 功能说明

- 为玩家 UUID 生成四位 `com id`
- 支持按 UUID 查询 `com id`
- 支持按 `com id` 反查 UUID
- 支持本地文件存储
- 支持可选远程 JDBC 数据库存储
- 支持对外 HTTP 查询接口
- 支持其他插件直接调用 `JumpComIdApi`

## 命令

- `jump-comid-get <uuid>`
- `jump-comid-find <comId>`
- `jump-comid-status`
- `/comid`
- `/comid <uuid>`

## HTTP 接口

- `GET /api/v1/health`
- `GET /api/v1/com-id?uuid=<uuid>`
- `GET /api/v1/com-id?uuid=<uuid>&create=false`
- `GET /api/v1/com-id/reverse?comId=<comId>`

## 插件入口

```text
com.mdt.jump.JumpComIdPlugin
```

## 版本

- 插件版本：`v1`
- 建议市场版本：`v1`
