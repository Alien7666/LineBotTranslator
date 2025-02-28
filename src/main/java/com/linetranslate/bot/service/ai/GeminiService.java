package com.linetranslate.bot.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.linetranslate.bot.config.GeminiConfig;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GeminiService implements AiService {

    private final String modelName;
    private final String apiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiService(GeminiConfig geminiConfig) {
        this.modelName = geminiConfig.getModelName();
        this.apiKey = geminiConfig.getApiKey();

        // 初始化 HTTP 客戶端
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        this.objectMapper = new ObjectMapper();

        log.info("Gemini 服務初始化成功，使用模型: {}", modelName);
    }

    @Override
    public String translateText(String text, String targetLanguage) {
        try {
            // 建立提示
            String prompt = "請將以下文本翻譯成" + targetLanguage + "。只需返回翻譯結果，不要添加任何解釋或額外信息：\n\n" + text;

            // 建立請求體 JSON
            String requestBody = "{\n" +
                    "  \"contents\": [\n" +
                    "    {\n" +
                    "      \"parts\": [\n" +
                    "        {\n" +
                    "          \"text\": \"" + prompt.replace("\"", "\\\"") + "\"\n" +
                    "        }\n" +
                    "      ]\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"generationConfig\": {\n" +
                    "    \"temperature\": 0.2,\n" +
                    "    \"topK\": 40,\n" +
                    "    \"topP\": 0.95,\n" +
                    "    \"maxOutputTokens\": 1024\n" +
                    "  }\n" +
                    "}";

            // 建立請求
            Request request = new Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey)
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            // 發送請求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Gemini API 請求失敗: {}", response);
                    return "翻譯失敗: Gemini API 請求錯誤 " + response.code();
                }

                String responseBody = response.body().string();
                JsonNode jsonResponse = objectMapper.readTree(responseBody);

                // 解析回應
                JsonNode candidatesNode = jsonResponse.path("candidates");
                if (candidatesNode.isArray() && candidatesNode.size() > 0) {
                    JsonNode contentNode = candidatesNode.get(0).path("content");
                    JsonNode partsNode = contentNode.path("parts");

                    if (partsNode.isArray() && partsNode.size() > 0) {
                        String translatedText = partsNode.get(0).path("text").asText();
                        return translatedText.trim();
                    }
                }

                log.error("無法從 Gemini 回應中解析翻譯結果: {}", responseBody);
                return "翻譯失敗: 無法解析回應";
            }
        } catch (IOException e) {
            log.error("Gemini 翻譯失敗: {}", e.getMessage());
            return "翻譯失敗: " + e.getMessage();
        }
    }

    @Override
    public String processImage(String prompt, String imageUrl) {
        // 本方法將在 OCR 功能實現時更新
        log.warn("Gemini 圖片處理功能尚未實現");
        return "Gemini 圖片處理功能尚未實現";
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}