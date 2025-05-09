# LINE Bot 翻譯服務部署指南

## 一、本地打包與推送到 Docker Hub

1. 使用 Dockerfile 構建映像：
   ```bash
   # 在專案根目錄執行
   docker build -t 您的用戶名/linebot-translator:latest .
   ```

2. 登入 Docker Hub：
   ```bash
   docker login
   ```

3. 推送映像到 Docker Hub：
   ```bash
   docker push 您的用戶名/linebot-translator:latest
   ```

4. 或者直接使用 docker-deploy.bat 批處理文件：
   ```bash
   .\docker-deploy.bat
   ```
   按照提示輸入 Docker Hub 用戶名和標籤。

## 二、伺服器部署準備

1. 將以下文件上傳到伺服器：
   - `.env`（環境變數配置）
   - `linebot.json`（Google Cloud 憑證）
   - `run-on-server.sh`（部署腳本）
   - `docker-compose.yml`（容器配置）

2. 確保 .env 文件中的 MongoDB 連接字串指向正確的伺服器位置：
   ```
   MONGODB_URI=mongodb://用戶名:密碼@伺服器IP:27017/數據庫名?authSource=admin
   ```

## 三、伺服器部署步驟

1. 設置腳本的執行權限：
   ```bash
   chmod +x run-on-server.sh
   ```

2. 執行部署腳本：
   ```bash
   ./run-on-server.sh
   ```

3. 查看容器狀態：
   ```bash
   docker ps | grep linebot-translator
   ```

4. 查看容器日誌：
   ```bash
   docker logs -f linebot-translator
   ```

## 四、手動部署方法（如果腳本不起作用）

1. 確保已有 docker-compose.yml 文件

2. 拉取最新映像：
   ```bash
   docker pull alien7666/linebot-translator:latest
   ```

3. 啟動容器：
   ```bash
   docker-compose down
   docker-compose up -d
   ```

## 五、重要說明

1. 我們使用 `network_mode: "host"` 設定，讓容器直接使用宿主機的網路，以連接到宿主機上的 MongoDB 服務。

2. 我們添加了以下環境變數來解決 gRPC 原生庫問題：
   ```
   GRPC_NETTY_SHADED_NETTY_TCNATIVE_DO_NOT_USE_CONSCRYPT=true
   GRPC_NETTY_SHADED_NETTY_TCNATIVE_DO_NOT_USE_NATIVE=true
   ```

3. 確保 .env 文件和 linebot.json 文件的權限正確：
   ```bash
   chmod 644 .env
   chmod 644 linebot.json
   ```
