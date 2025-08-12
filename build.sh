#!/bin/bash

echo "Building NekoSuite server..."

# 确保在项目根目录
cd "$(dirname "$0")"

# 初始化 Go 模块并下载依赖
go mod tidy

# 构建应用
go build -o bin/neko-suite main.go

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "Run with: ./bin/neko-suite"
else
    echo "Build failed!"
    exit 1
fi
