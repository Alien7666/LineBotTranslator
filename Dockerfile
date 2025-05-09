# 第一階段：構建應用程式
FROM eclipse-temurin:17-jdk as build
WORKDIR /workspace/app

# 複製 Maven 包裝器和 POM 文件
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# 修正 Maven 包裝器的執行權限
RUN chmod +x ./mvnw

# 下載依賴項（這將被緩存，除非 pom.xml 更改）
RUN ./mvnw dependency:go-offline -B

# 複製源代碼
COPY src src

# 使用生產環境配置構建應用程序
RUN ./mvnw clean package -DskipTests -Pprod

# 第二階段：運行應用程式
FROM eclipse-temurin:17-jre
WORKDIR /app

# 設置時區
RUN apt-get update && \
    apt-get install -y tzdata && \
    ln -fs /usr/share/zoneinfo/Asia/Taipei /etc/localtime && \
    dpkg-reconfigure -f noninteractive tzdata && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 創建日誌目錄
RUN mkdir -p /app/logs

# 複製構建的 JAR 文件
COPY --from=build /workspace/app/target/*.jar app.jar

# 設置環境變量
ENV SPRING_PROFILES_ACTIVE=prod

# 暴露端口
EXPOSE 4040

# 運行應用程序
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--server.port=4040"]
