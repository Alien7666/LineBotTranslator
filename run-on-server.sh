#!/bin/bash

# 檢查 Docker 是否安裝
if ! command -v docker &> /dev/null; then
    echo "Docker 未安裝，請先安裝 Docker"
    exit 1
fi

# 檢查 Docker Compose 是否安裝
if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose 未安裝，請先安裝 Docker Compose"
    exit 1
fi

# 檢查 .env 文件是否存在
if [ ! -f ".env" ]; then
    echo "錯誤: .env 文件不存在"
    exit 1
fi

# 檢查 linebot.json 文件是否存在
if [ ! -f "linebot.json" ]; then
    echo "錯誤: linebot.json 文件不存在"
    exit 1
fi

# 停止並移除現有容器
echo "停止並移除現有容器..."
docker-compose down

# 創建 docker-compose.yml 文件
echo "創建 docker-compose.yml 文件..."
cat > docker-compose.yml << 'EOL'
version: '3.8'

services:
  app:
    image: alien7666/linebot-translator:latest
    container_name: linebot-translator
    restart: always
    network_mode: "host"  # 使用宿主機網路
    env_file:
      - .env
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SERVER_PORT=4040
      # 解決 gRPC 原生庫問題
      - GRPC_NETTY_SHADED_NETTY_TCNATIVE_DO_NOT_USE_CONSCRYPT=true
      - GRPC_NETTY_SHADED_NETTY_TCNATIVE_DO_NOT_USE_NATIVE=true
    volumes:
      - ./logs:/app/logs
      - ./linebot.json:/app/linebot.json:ro
EOL

# 拉取最新映像
echo "拉取最新映像..."
docker pull alien7666/linebot-translator:latest

# 啟動容器
echo "啟動容器..."
docker-compose up -d

# 顯示容器狀態
echo "容器狀態:"
docker ps | grep linebot-translator

echo "查看日誌請執行: docker logs -f linebot-translator"
