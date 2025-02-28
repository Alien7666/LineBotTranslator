package com.linetranslate.bot;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.File;

@SpringBootApplication
@EnableAsync
@EnableCaching
public class LinebotTranslatorApplication {

	public static void main(String[] args) {
		// 載入環境變數 (從 .env 文件)
		loadEnvVariables();

		SpringApplication.run(LinebotTranslatorApplication.class, args);
	}

	/**
	 * 載入環境變數
	 */
	private static void loadEnvVariables() {
		try {
			// 檢查項目根目錄中是否存在 .env 文件
			File rootEnvFile = new File(".env");
			if (rootEnvFile.exists()) {
				// 從根目錄載入 .env 文件
				Dotenv dotenv = Dotenv.configure().load();

				// 將變數設置到系統環境變數中
				dotenv.entries().forEach(entry -> {
					if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
						System.setProperty(entry.getKey(), entry.getValue());
					}
				});

				System.out.println("已從 .env 文件載入環境變數");
			} else {
				System.out.println("警告: 未找到 .env 文件，請確保環境變數已設置");
			}
		} catch (Exception e) {
			System.err.println("載入環境變數失敗: " + e.getMessage());
		}
	}
}