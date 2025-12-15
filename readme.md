# NekoSuite

NekoSuite 是一個模塊化的 Minecraft Server 擴展功能專案

## 模塊列表

| 模塊 | 狀態 | 描述 | 文檔 |
|------|------|------|------|
| wish | ✅ 已重構 | 祈願系統 | [wish/README.md](wish/README.md) |
| event | ✅ 已重構 | 活動系統 | [event/README.md](event/README.md) |
| buy | 🔄 PHP實現 | 購買系統 | buy/ |
| cdk | 🔄 PHP實現 | CDK系統 | cdk/ |
| exp | 🔄 PHP實現 | 經驗系統 | exp/ |
| tools | 🔄 PHP實現 | 工具系統 | tools/ |

## Java Bukkit 直接實現

- 位置：`java-plugin/`
- 構建：`mvn package`（JDK 1.8，需可訪問 Paper/Spigot Maven 倉庫）
- 功能：在 Bukkit 端直接提供 wish 與 event 模塊命令，無需 HTTP API

## 架構

- **前端**: [MyCommand](https://dev.bukkit.org/projects/mycommand) 腳本，負責遊戲內命令處理
- **後端**: Go 語言實現，負責核心邏輯和數據存儲
- **通訊**: HTTP API，響應格式為可執行的遊戲命令

## 快速開始

### 先決條件

- Go 1.16+
- [MyCommand](https://dev.bukkit.org/projects/mycommand) 5.7+

### 編譯和運行

克隆倉庫

```bash
git clone https://github.com/moehoshio/NekoSuite.git
cd NekoSuite
```

- 或者，從 Release 頁面下載已編譯的可執行檔

編譯
```bash
go build -o neko-suite.exe
```

建立和修改模塊配置檔
```bash
cp <module_name>/config.example.yml <module_name>/config.yml
# 修改 config.yml 以滿足你的需求
```

運行服務端

```bash
./neko-suite.exe
```

其將在端口 8080 上啟動 （確保運行在Minecraft Server Host）。

將需要的模塊的前端腳本部署到 `/plugins/MyCommand/commands/` 目錄下。
前端腳本通常在各模塊下的 `command_<module_name>.yml`。

重載你的 MyCommand

```text
/mycmd-reload commands
```

現在你可以使用 NekoSuite 提供的功能了！

## 文檔

- [整體架構設計](ARCHITECTURE.md)
- [開發指南](DEVELOPMENT_GUIDE.md)
- [各模塊詳細文檔](模塊文件夾中的 README.md)
