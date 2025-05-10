package com.linetranslate.bot.controller;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.UserProfileRepository;
import com.linetranslate.bot.service.ocr.ImageTranslationService;
import com.linetranslate.bot.service.translation.TranslationService;
import com.linetranslate.bot.service.line.LineUserProfileService;
import com.linetranslate.bot.service.AdminService;
import com.linetranslate.bot.util.LanguageUtils;
import com.linetranslate.bot.config.OpenAiConfig;
import com.linetranslate.bot.config.GeminiConfig;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

@LineMessageHandler
@Slf4j
public class LineBotController {

    private final TranslationService translationService;
    private final LineUserProfileService lineUserProfileService;
    private final AdminService adminService;
    private final AdminController adminController;
    private final ImageTranslationService imageTranslationService;
    private final OpenAiConfig openAiConfig;
    private final GeminiConfig geminiConfig;

    @Autowired
    public LineBotController(
            TranslationService translationService,
            LineUserProfileService lineUserProfileService,
            AdminService adminService,
            AdminController adminController,
            ImageTranslationService imageTranslationService,
            OpenAiConfig openAiConfig,
            GeminiConfig geminiConfig) {
        this.translationService = translationService;
        this.lineUserProfileService = lineUserProfileService;
        this.adminService = adminService;
        this.adminController = adminController;
        this.imageTranslationService = imageTranslationService;
        this.openAiConfig = openAiConfig;
        this.geminiConfig = geminiConfig;
    }
    
    /**
     * 處理文本消息事件
     */
    @EventMapping
    public Message handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
        String userId = event.getSource().getUserId();
        String receivedText = event.getMessage().getText();

        log.info("收到用戶 {} 的文字訊息: {}", userId, receivedText);

        // 檢查是否是系統命令
        if (receivedText.startsWith("/")) {
            return handleCommand(userId, receivedText);
        }

        // 檢查是否是快速翻譯請求
        if (receivedText.startsWith("快速翻譯:") || receivedText.startsWith("快速翻譯：")) {
            return handleQuickTranslation(userId, receivedText);
        }

        // 處理翻譯請求
        String response = translationService.processTranslationRequest(userId, receivedText);
        return new TextMessage(response);
    }

    /**
     * 處理圖片消息事件
     */
    @EventMapping
    public Message handleImageMessageEvent(MessageEvent<ImageMessageContent> event) {
        String userId = event.getSource().getUserId();
        String messageId = event.getMessage().getId();
        log.info("收到用戶 {} 的圖片訊息，ID: {}", userId, messageId);

        try {
            // 處理圖片翻譯
            String translationResult = imageTranslationService.processImageTranslation(userId, messageId);
            return new TextMessage(translationResult);
        } catch (Exception e) {
            log.error("圖片翻譯處理失敗: {}", e.getMessage(), e);
            return new TextMessage("圖片處理失敗: " + e.getMessage() +
                    "\n請確保圖片清晰且包含可識別的文字，或稍後再試。");
        }
    }

    /**
     * 處理其他未定義的事件
     */
    @EventMapping
    public void handleDefaultMessageEvent(Event event) {
        log.info("收到未處理的事件: {}", event);
    }

    /**
     * 處理系統命令
     */
    private Message handleCommand(String userId, String command) {
        String[] parts = command.substring(1).split("\\s+", 2);
        String action = parts[0].toLowerCase();

        // 處理普通用戶命令
        switch (action) {
            case "help":
                return new TextMessage(getHelpMessage());

            case "about":
                return new TextMessage(getAboutMessage());

            case "setai":
                if (parts.length < 2) {
                    return new TextMessage("請指定 AI 提供者 (openai 或 gemini)。例如：/setai openai");
                }
                String provider = parts[1].toLowerCase();
                String resultSetAi = translationService.setPreferredProvider(userId, provider);
                return new TextMessage(resultSetAi);
                
            case "setmodel":
                if (parts.length < 2) {
                    return new TextMessage("請指定 AI 模型名稱。例如：/setmodel gpt-4o");
                }
                String modelName = parts[1].trim();
                return handleSetModelCommand(userId, modelName);                                                                                                                                                    
                
            case "models":
                return new TextMessage(getAvailableModelsMessage());

            case "外文翻譯":
                if (parts.length < 2) {
                    return new TextMessage("請指定語言代碼或名稱。例如：/外文翻譯 en 或 /外文翻譯 日文");
                }
                String language = parts[1];
                String resultSetLang = translationService.setPreferredLanguage(userId, language);
                return new TextMessage(resultSetLang);

            case "profile":
                return new TextMessage(lineUserProfileService.getUserProfileInfo(userId));
                
            case "status":
                return new TextMessage(translationService.getUserStatus(userId));

            case "lang":
                return createLanguageSelectionMessage(userId);
                
            case "中文翻譯":
                if (parts.length < 2) {
                    return new TextMessage("請指定中文翻譯的預設目標語言。例如：/中文翻譯 vi 或 /中文翻譯 越南文");
                }
                String targetLanguage = parts[1];
                String resultSetC2Lang = translationService.setPreferredChineseTargetLanguage(userId, targetLanguage);
                return new TextMessage(resultSetC2Lang);

            // 處理管理員命令
            case "adminhelp":
                // 確保只有管理員可以執行 /adminhelp 命令
                if (!adminService.isAdmin(userId)) {
                    return new TextMessage("您沒有管理員權限。");
                }
                return adminController.handleCommand(userId, "help");
                
            case "admin":
                // 確保只有管理員可以執行 /admin 命令
                if (!adminService.isAdmin(userId)) {
                    return new TextMessage("您沒有管理員權限。");
                }
                // 如果有多個參數，將後面的參數合併為一個字符串
                String adminCommand = "";
                if (parts.length > 1) {
                    adminCommand = parts[1];
                }
                return adminController.handleCommand(userId, adminCommand);
                
            case "isadmin":
                // 將 /isadmin 命令也視為管理員命令，需要管理員權限
                if (!adminService.isAdmin(userId)) {
                    return new TextMessage("您沒有管理員權限。");
                }
                return adminController.handleCommand(userId, "isadmin");

            default:
                return new TextMessage("未知命令。發送 /help 獲取可用命令列表。");
        }
    }

    /**
     * 處理快速翻譯請求
     * 
     * @param userId 用戶 ID
     * @param receivedText 收到的文本 (格式: "快速翻譯:[語言代碼] [文本]")
     * @return 翻譯結果消息
     */
    private Message handleQuickTranslation(String userId, String receivedText) {
        // 移除前綴
        String content = receivedText.startsWith("快速翻譯:") ? 
                receivedText.substring("快速翻譯:".length()) : 
                receivedText.substring("快速翻譯：".length());
        
        // 分割語言代碼和文本
        String[] parts = content.trim().split("\\s+", 2);
        if (parts.length < 2) {
            return new TextMessage("快速翻譯格式錯誤。正確格式：快速翻譯:[語言代碼] [文本]\n例如：快速翻譯:en 你好");
        }
        
        String languageCode = parts[0].trim();
        String text = parts[1].trim();
        
        // 檢查語言代碼是否有效
        if (!LanguageUtils.isSupported(languageCode)) {
            return new TextMessage("不支持的語言代碼：" + languageCode + "\n請使用有效的語言代碼，例如：en, ja, zh-tw 等");
        }
        
        // 進行翻譯
        String result = translationService.quickTranslate(userId, text, languageCode);
        return new TextMessage(result);
    }

    /**
     * 創建語言選擇消息
     * 
     * @param userId 用戶 ID
     * @return 包含語言選擇按鈕的消息
     */
    private Message createLanguageSelectionMessage(String userId) {
        // 由於 LINE Bot API 的限制，這裡只返回文本消息
        StringBuilder sb = new StringBuilder();
        sb.append("🌐 語言選擇\n\n");
        sb.append("請使用以下命令設置您偏好的語言：\n");
        sb.append("/外文翻譯 [語言代碼]\n\n");
        
        sb.append("常用語言代碼：\n");
        sb.append("🇺🇸 英文: en\n");
        sb.append("🇯🇵 日文: ja\n");
        sb.append("🇰🇷 韓文: ko\n");
        sb.append("🇨🇳 簡體中文: zh-cn\n");
        sb.append("🇹🇼 繁體中文: zh-tw\n");
        sb.append("🇫🇷 法文: fr\n");
        sb.append("🇩🇪 德文: de\n");
        sb.append("🇪🇸 西班牙文: es\n");
        sb.append("🇮🇹 義大利文: it\n");
        sb.append("🇷🇺 俄文: ru\n");
        sb.append("🇵🇹 葡萄牙文: pt\n");
        sb.append("🇹🇭 泰文: th\n");
        sb.append("🇻🇳 越南文: vi\n");
        sb.append("🇮🇩 印尼文: id\n");
        
        return new TextMessage(sb.toString());
    }

    /**
     * 處理設置模型命令
     * 
     * @param userId 用戶ID
     * @param modelName 模型名稱
     * @return 設置結果消息
     */
    private Message handleSetModelCommand(String userId, String modelName) {
        // 獲取用戶當前的 AI 提供者
        UserProfile userProfile = lineUserProfileService.getUserProfile(userId);
        String provider = userProfile.getPreferredAiProvider();
        if (provider == null) {
            provider = "openai"; // 默認使用 OpenAI
        }
        
        // 設置提供者
        translationService.setPreferredProvider(userId, provider);
        
        // 設置模型
        String result = translationService.setPreferredModel(userId, modelName);
        return new TextMessage(result);
    }

    /**
     * 獲取可用模型列表消息
     * 
     * @return 可用模型列表消息
     */
    private String getAvailableModelsMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("🤖 可用的 AI 模型\n\n");
        
        // OpenAI 模型
        sb.append("OpenAI 模型：\n");
        List<String> openaiModels = openAiConfig.getAvailableModels();
        if (openaiModels != null && !openaiModels.isEmpty()) {
            for (String model : openaiModels) {
                sb.append("• ").append(model).append("\n");
            }
        } else {
            sb.append("• ").append(openAiConfig.getModelName()).append(" (默認)\n");
        }
        
        sb.append("\n");
        
        // Gemini 模型
        sb.append("Google Gemini 模型：\n");
        List<String> geminiModels = geminiConfig.getAvailableModels();
        if (geminiModels != null && !geminiModels.isEmpty()) {
            for (String model : geminiModels) {
                sb.append("• ").append(model).append("\n");
            }
        } else {
            sb.append("• ").append(geminiConfig.getModelName()).append(" (默認)\n");
        }
        
        sb.append("\n使用 /setmodel [模型名稱] 設置您偏好的模型");
        
        return sb.toString();
    }

    /**
     * 獲取幫助信息
     */
    private String getHelpMessage() {
        return "🤖 LINE 翻譯機器人幫助\n\n" +
                "[💬 基本使用]\n" +
                "• 直接發送文字 → 自動檢測語言並翻譯\n" +
                "• 發送圖片 → 識別圖片中的文字並翻譯\n" +
                "• 快速翻譯:[語言代碼] [文本] → 翻譯到指定語言\n\n" +
                
                "[⚙️ 設置命令]\n" +
                "🔄 /setai [提供者] - 設置 AI 提供者 (openai 或 gemini)\n" +
                "🔠 /外文翻譯 [語言] - 設置偏好的目標語言\n" +
                "🀄 /中文翻譯 [語言] - 設置中文翻譯的目標語言\n" +
                "🤖 /setmodel [模型] - 設置 AI 模型\n" +
                "📋 /models - 顯示可用的 AI 模型\n\n" +
                
                "[ℹ️ 其他命令]\n" +
                "❓ /help - 顯示此幫助信息\n" +
                "ℹ️ /about - 關於此機器人\n" +
                "🔤 /lang - 顯示語言選擇菜單\n" +
                "📈 /status - 顯示您的所有設定\n" +
                "👤 /profile - 查看您的用戶資料";
    }
    


    /**
     * 獲取關於信息
     */
    private String getAboutMessage() {
        return "🚀 LINE 翻譯機器人\n\n" +
                "這是一個使用先進 AI 技術進行即時翻譯的 LINE 機器人。\n" +
                "支持 OpenAI 和 Google Gemini 模型。\n\n" +
                "功能：\n" +
                "• 🌐 自動語言檢測\n" +
                "• 💬 多語言翻譯\n" +
                "• 📚 批量多行文本翻譯\n" +
                "• ⭐ 語言偏好設定\n" +
                "• ⚡ 快速翻譯\n" +
                "• 📸 圖片文字識別與翻譯";
    }
    

}
