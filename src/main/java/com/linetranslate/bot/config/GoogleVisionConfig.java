package com.linetranslate.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@Getter
@Slf4j
public class GoogleVisionConfig {

    @Value("${google.cloud.vision.api.key:#{null}}")
    private String apiKey;

    @Value("${GOOGLE_APPLICATION_CREDENTIALS:./linebot.json}")
    private String credentialsPath;

    @Bean
    public ImageAnnotatorClient imageAnnotatorClient() {
        try {
            // 首先檢查環境變數
            String envCredentialsPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");

            // 如果環境變數存在且不為空，優先使用
            if (envCredentialsPath != null && !envCredentialsPath.isEmpty()) {
                log.info("使用環境變數中的服務帳戶認證文件路徑: {}", envCredentialsPath);

                Path path = Paths.get(envCredentialsPath);
                if (Files.exists(path)) {
                    log.info("環境變數指定的認證文件存在");
                    return ImageAnnotatorClient.create();
                } else {
                    log.error("環境變數指定的認證文件不存在: {}", envCredentialsPath);
                }
            }

            // 嘗試使用配置文件中指定的路徑
            log.info("嘗試使用配置路徑: {}", credentialsPath);
            File credentialsFile = new File(credentialsPath);

            if (credentialsFile.exists()) {
                log.info("找到認證文件: {}", credentialsFile.getAbsolutePath());

                try (FileInputStream serviceAccountStream = new FileInputStream(credentialsFile)) {
                    GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream);
                    ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
                            .setCredentialsProvider(() -> credentials)
                            .build();

                    log.info("成功載入Google Vision認證文件");
                    return ImageAnnotatorClient.create(settings);
                } catch (IOException e) {
                    log.error("讀取認證文件失敗: {}", e.getMessage(), e);
                    return null;
                }
            } else {
                log.warn("未找到 Google Vision API 認證文件: {}", credentialsFile.getAbsolutePath());

                // 列出當前目錄的文件，幫助排查問題
                File currentDir = new File(".");
                log.info("當前目錄: {}", currentDir.getAbsolutePath());
                for (File file : currentDir.listFiles()) {
                    log.info(" - 檔案: {}", file.getName());
                }

                return null;
            }
        } catch (IOException e) {
            log.error("無法創建 Google Vision 客戶端: {}", e.getMessage(), e);
            return null;
        }
    }
}