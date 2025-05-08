package com.linetranslate.bot.service.ai;

/**
 * AI 服務介面，定義所有 AI 提供者必須實現的方法
 */
public interface AiService {

    /**
     * 使用 AI 進行文本翻譯
     *
     * @param text 要翻譯的文本
     * @param targetLanguage 目標語言
     * @return 翻譯結果
     */
    String translateText(String text, String targetLanguage);

    /**
     * 使用 AI 處理圖片中的文字
     *
     * @param prompt 描述任務的提示
     * @param imageUrl 圖片 URL
     * @return 處理結果
     */
    String processImage(String prompt, String imageUrl);

    /**
     * 獲取 AI 提供者的名稱
     *
     * @return 提供者名稱 (如 "openai" 或 "gemini")
     */
    String getProviderName();

    /**
     * 獲取使用的模型名稱
     *
     * @return 模型名稱
     */
    String getModelName();
    
    /**
     * 使用 AI 生成文本
     *
     * @param prompt 提示詞
     * @return 生成的文本
     */
    String generateText(String prompt);
}