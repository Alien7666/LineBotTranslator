package com.linetranslate.bot.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    private final ImageTranslationService imageTranslationService;
    private final OpenAiConfig openAiConfig;
    private final GeminiConfig geminiConfig;
    private final UserProfileRepository userProfileRepository;

    @Autowired
    public LineBotController(
            TranslationService translationService,
            LineUserProfileService lineUserProfileService,
            AdminService adminService,
            ImageTranslationService imageTranslationService,
            OpenAiConfig openAiConfig,
            GeminiConfig geminiConfig,
            UserProfileRepository userProfileRepository) {
        this.translationService = translationService;
        this.lineUserProfileService = lineUserProfileService;
        this.adminService = adminService;
        this.imageTranslationService = imageTranslationService;
        this.openAiConfig = openAiConfig;
        this.geminiConfig = geminiConfig;
        this.userProfileRepository = userProfileRepository;
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

            case "setlang":
                if (parts.length < 2) {
                    return new TextMessage("請指定語言代碼或名稱。例如：/setlang en 或 /setlang 日文");
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
                
            case "c2lang":
                if (parts.length < 2) {
                    return new TextMessage("請指定中文翻譯的預設目標語言。例如：/c2lang vi 或 /c2lang 越南文");
                }
                String targetLanguage = parts[1];
                String resultSetC2Lang = translationService.setPreferredChineseTargetLanguage(userId, targetLanguage);
                return new TextMessage(resultSetC2Lang);

            // 處理管理員命令
            case "adminhelp":
                if (adminService.isAdmin(userId)) {
                    return new TextMessage(getAdminHelpMessage());
                } else {
                    return new TextMessage("您沒有管理員權限。");
                }
                
            case "admin":
                if (adminService.isAdmin(userId)) {
                    // 如果有多個參數，將後面的參數合併為一個字符串
                    String adminCommand = "";
                    if (parts.length > 1) {
                        adminCommand = parts[1];
                    }
                    return handleAdminCommand(userId, adminCommand);
                } else {
                    return new TextMessage("您沒有管理員權限。");
                }
                
            case "isadmin":
                if (adminService.isAdmin(userId)) {
                    return new TextMessage("您是管理員。");
                } else {
                    return new TextMessage("您不是管理員。");
                }

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
        sb.append("/setlang [語言代碼]\n\n");
        
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
                "【💬 基本使用】\n" +
                "• 直接發送文字 → 自動檢測語言並翻譯\n" +
                "• 發送圖片 → 識別圖片中的文字並翻譯\n" +
                "• 快速翻譯:[語言代碼] [文本] → 翻譯到指定語言\n\n" +
                
                "【⚙️ 設置命令】\n" +
                "🔄 /setai [提供者] - 設置 AI 提供者 (openai 或 gemini)\n" +
                "🔠 /setlang [語言] - 設置偏好的目標語言\n" +
                "🀄 /c2lang [語言] - 設置中文翻譯的目標語言\n" +
                "🤖 /setmodel [模型] - 設置 AI 模型\n" +
                "📋 /models - 顯示可用的 AI 模型\n\n" +
                
                "【ℹ️ 其他命令】\n" +
                "❓ /help - 顯示此幫助信息\n" +
                "ℹ️ /about - 關於此機器人\n" +
                "🔤 /lang - 顯示語言選擇菜單\n" +
                "📈 /status - 顯示您的所有設定\n" +
                "👤 /profile - 查看您的用戶資料";
    }
    
    /**
     * 獲取管理員幫助信息
     */
    private String getAdminHelpMessage() {
        return "🔐 LINE 翻譯機器人管理員命令 🔐\n\n" +
                "【💻 管理員命令列表】\n" +
                "📖 /adminhelp - 顯示管理員幫助信息\n" +
                "📢 /admin broadcast [消息] - 向所有用戶廣播消息\n" +
                "📊 /admin stats - 查看系統統計信息\n" +
                "🔍 /admin users - 查看用戶列表\n" +
                "🔎 /admin user [用戶ID] - 查看指定用戶詳細信息\n" +
                "🔧 /admin config - 查看系統配置\n" +
                "💰 /admin usage - 查看 API 使用量和費用\n" +
                "📅 /admin today - 查看今日統計信息\n" +
                "🔐 /admin isadmin - 檢查您是否是管理員";
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
    
    /**
     * 處理管理員命令
     * 
     * @param userId 用戶ID
     * @param command 命令內容
     * @return 回應消息
     */
    private Message handleAdminCommand(String userId, String command) {
        log.info("處理管理員命令: 用戶={}, 命令={}", userId, command);
        
        if (command.isEmpty()) {
            return new TextMessage(getAdminHelpMessage());
        }
        
        String[] parts = command.split(" ", 2);
        String subCommand = parts[0].toLowerCase();
        String param = parts.length > 1 ? parts[1] : "";
        
        switch (subCommand) {
            case "isadmin":
                return new TextMessage("您是管理員。");
                
            case "broadcast":
                if (param.isEmpty()) {
                    return new TextMessage("請指定要廣播的消息。例如：/admin broadcast 您好，這是一條廣播消息。");
                }
                // 實現廣播功能
                int successCount = adminService.broadcastMessage(param);
                
                // 獲取有效用戶列表及其暱稱
                List<UserProfile> validUsers = userProfileRepository.findAll().stream()
                        .filter(user -> user.getUserId() != null && !user.getUserId().isEmpty())
                        .collect(Collectors.toList());
                
                StringBuilder broadcastBuilder = new StringBuilder();
                broadcastBuilder.append("📢 廣播消息已發送\n\n");
                broadcastBuilder.append("成功發送給 ").append(successCount).append(" 個用戶\n");
                broadcastBuilder.append("用戶列表：\n");
                
                for (int i = 0; i < Math.min(validUsers.size(), 10); i++) { // 最多顯示10個用戶
                    UserProfile user = validUsers.get(i);
                    String displayName = user.getDisplayName() != null ? user.getDisplayName() : "無暱稱";
                    broadcastBuilder.append(i + 1).append(". ").append(displayName);
                    // 顯示用戶ID的後六位數字，以保護隱私
                    String userIdPart = user.getUserId();
                    if (userIdPart != null && userIdPart.length() > 6) {
                        broadcastBuilder.append(" (ID: ...").append(userIdPart.substring(userIdPart.length() - 6)).append(")\n");
                    } else {
                        broadcastBuilder.append(" (ID: ").append(userIdPart).append(")\n");
                    }
                }
                
                if (validUsers.size() > 10) {
                    broadcastBuilder.append("... 及其他 ").append(validUsers.size() - 10).append(" 個用戶\n");
                }
                
                broadcastBuilder.append("\n消息內容：\n").append(param);
                
                return new TextMessage(broadcastBuilder.toString());
                
            case "user":
                if (param.isEmpty()) {
                    return new TextMessage("請指定要查詢的用戶ID。例如：/admin user U123456789");
                }
                // 實現用戶詳細信息功能
                Map<String, Object> userInfo = adminService.getUserInfo(param);
                if (userInfo == null) {
                    return new TextMessage("找不到用戶: " + param);
                }
                
                StringBuilder userInfoBuilder = new StringBuilder();
                userInfoBuilder.append("👤 用戶詳細信息\n\n");
                userInfoBuilder.append("用戶ID: ").append(userInfo.get("userId")).append("\n");
                userInfoBuilder.append("顯示名稱: ").append(userInfo.get("displayName")).append("\n");
                userInfoBuilder.append("註冊時間: ").append(userInfo.get("registrationTime")).append("\n");
                userInfoBuilder.append("最後活躍: ").append(userInfo.get("lastActiveTime")).append("\n\n");
                
                userInfoBuilder.append("📊 統計信息\n");
                userInfoBuilder.append("總翻譯次數: ").append(userInfo.get("translationCount")).append("\n");
                userInfoBuilder.append("文字翻譯: ").append(userInfo.get("textTranslationCount")).append("\n");
                userInfoBuilder.append("圖片翻譯: ").append(userInfo.get("imageTranslationCount")).append("\n\n");
                
                userInfoBuilder.append("⚙️ 用戶設置\n");
                userInfoBuilder.append("偏好語言: ").append(userInfo.get("preferredLanguage")).append("\n");
                userInfoBuilder.append("中文翻譯目標語言: ").append(userInfo.get("preferredChineseTargetLanguage")).append("\n");
                userInfoBuilder.append("偏好 AI 提供者: ").append(userInfo.get("preferredAiProvider")).append("\n");
                userInfoBuilder.append("OpenAI 偏好模型: ").append(userInfo.get("openaiPreferredModel")).append("\n");
                userInfoBuilder.append("Gemini 偏好模型: ").append(userInfo.get("geminiPreferredModel")).append("\n");
                
                return new TextMessage(userInfoBuilder.toString());
                
            case "config":
                // 實現系統配置信息功能
                StringBuilder configBuilder = new StringBuilder();
                configBuilder.append("⚙️ 系統配置信息\n\n");
                
                configBuilder.append("OpenAI 配置:\n");
                configBuilder.append("• 默認模型: ").append(openAiConfig.getModelName()).append("\n");
                configBuilder.append("• 可用模型: ").append(String.join(", ", openAiConfig.getAvailableModels())).append("\n\n");
                
                configBuilder.append("Gemini 配置:\n");
                configBuilder.append("• 默認模型: ").append(geminiConfig.getModelName()).append("\n");
                configBuilder.append("• 可用模型: ").append(String.join(", ", geminiConfig.getAvailableModels())).append("\n\n");
                
                configBuilder.append("OCR 功能: ").append(imageTranslationService.isOcrEnabled() ? "已啟用" : "已禁用").append("\n");
                configBuilder.append("默認 AI 提供者: ").append(translationService.getDefaultProvider()).append("\n");
                // 顯示管理員用戶及其暱稱
                configBuilder.append("管理員用戶: ");
                List<String> adminUserIds = adminService.getAdminUsers();
                if (adminUserIds != null && !adminUserIds.isEmpty()) {
                    List<String> adminUsersWithNames = new ArrayList<>();
                    for (String adminId : adminUserIds) {
                        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(adminId);
                        if (userOpt.isPresent() && userOpt.get().getDisplayName() != null) {
                            adminUsersWithNames.add(adminId + " (" + userOpt.get().getDisplayName() + ")");
                        } else {
                            adminUsersWithNames.add(adminId);
                        }
                    }
                    configBuilder.append(String.join(", ", adminUsersWithNames));
                } else {
                    configBuilder.append("無");
                }
                configBuilder.append("\n");
                
                return new TextMessage(configBuilder.toString());
                
            case "usage":
                // 實現 API 使用量和費用功能
                return new TextMessage("💰 API 使用量和費用功能尚未實現\n\n此功能將顯示 API 的使用量和相關費用。");
                
            case "today":
                // 使用現有的 getTodayStats 方法
                return new TextMessage(adminService.getTodayStats());
                
            default:
                return new TextMessage("未知的管理員命令：" + subCommand + "\n\n" + getAdminHelpMessage());
        }
    }
}
