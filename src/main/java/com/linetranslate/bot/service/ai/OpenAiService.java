package com.linetranslate.bot.service.ai;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.linetranslate.bot.config.OpenAiConfig;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OpenAiService implements AiService {

    private final com.theokanning.openai.service.OpenAiService openAiClient;
    private final String modelName;

    @Autowired
    public OpenAiService(OpenAiConfig openAiConfig, @Qualifier("openAiClient") @Autowired(required = false) com.theokanning.openai.service.OpenAiService openAiClient) {
        this.openAiClient = openAiClient;
        this.modelName = openAiConfig.getModelName();

        if (openAiClient != null) {
            log.info("OpenAI 服務初始化成功，使用模型: {}", modelName);
        } else {
            log.warn("OpenAI 服務初始化失敗，API 金鑰可能未設置");
        }
    }

    @Override
    public String translateText(String text, String targetLanguage) {
        if (openAiClient == null) {
            log.warn("OpenAI 客戶端未初始化，無法進行翻譯");
            return "翻譯失敗: OpenAI API 未正確配置";
        }

        try {
            List<ChatMessage> messages = new ArrayList<>();

            // 系統訊息設定翻譯任務
            ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "你是一個專業的翻譯助手。請將用戶提供的文本翻譯成" + targetLanguage + "。只需返回翻譯結果，不要添加任何解釋或額外信息。");
            messages.add(systemMessage);

            // 用戶訊息包含要翻譯的文本
            ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), text);
            messages.add(userMessage);

            // 建立請求
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                    .model(modelName)
                    .messages(messages)
                    .temperature(0.3)  // 較低的溫度使輸出更加確定性和準確
                    .build();

            // 執行請求並獲取回應
            String response = openAiClient.createChatCompletion(chatCompletionRequest)
                    .getChoices().get(0).getMessage().getContent();

            return response.trim();
        } catch (Exception e) {
            log.error("OpenAI 翻譯失敗: {}", e.getMessage());
            return "翻譯失敗: " + e.getMessage();
        }
    }

    @Override
    public String processImage(String prompt, String imageUrl) {
        // 本方法將在 OCR 功能實現時更新
        log.warn("OpenAI 圖片處理功能尚未實現");
        return "OpenAI 圖片處理功能尚未實現";
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return modelName;
    }
}