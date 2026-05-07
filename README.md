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

This is a Mindustry server plugin template that generates a unique 4-character `com id` for each player UUID.

## Build

```powershell
.\gradlew.bat jar
```

The build produces:

```text
build/libs/mdt-jump-plugin.jar
dist/mdt-jump-plugin.jar
```

## Runtime Config

The plugin creates this file on first startup:

```text
config/mdt-jump-plugin/plugin-config.properties
```

The file name is English, while the config keys remain Chinese as requested.

## Commands

- `jump-comid-get <uuid>`
- `jump-comid-find <comId>`
- `jump-comid-status`
- `/comid`
- `/comid <uuid>`

## HTTP API

- `GET /api/v1/health`
- `GET /api/v1/com-id?uuid=<uuid>`
- `GET /api/v1/com-id?uuid=<uuid>&create=false`
- `GET /api/v1/com-id/reverse?comId=<comId>`

## Entry

```text
com.mdt.jump.JumpComIdPlugin
```

## Version

- Plugin version: `v1`
- Expected market version: `v1`
