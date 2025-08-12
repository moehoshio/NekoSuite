@echo off

echo Building NekoSuite server...

:: 确保在项目根目录
cd /d "%~dp0"

:: 初始化 Go 模块并下载依赖
go mod tidy

:: 构建应用
go build -o bin\neko-suite.exe main.go

if %ERRORLEVEL% EQU 0 (
    echo Build successful!
    echo Run with: bin\neko-suite.exe
) else (
    echo Build failed!
    exit /b 1
)
