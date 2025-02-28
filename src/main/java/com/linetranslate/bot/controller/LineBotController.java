package com.linetranslate.bot.controller;

import java.util.ArrayList;
import java.util.List;

import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import com.linetranslate.bot.service.ocr.ImageTranslationService;
import com.linetranslate.bot.service.translation.TranslationService;
import com.linetranslate.bot.service.line.LineUserProfileService;
import com.linetranslate.bot.service.AdminService;
import com.linetranslate.bot.util.LanguageUtils;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;

@LineMessageHandler
@Slf4j
public class LineBotController {

    private final TranslationService translationService;
    private final LineUserProfileService lineUserProfileService;
    private final AdminService adminService;
    private final ImageTranslationService imageTranslationService;

    @Autowired
    public LineBotController(
            TranslationService translationService,
            LineUserProfileService lineUserProfileService,
            AdminService adminService,
            ImageTranslationService imageTranslationService) {
        this.translationService = translationService;
        this.lineUserProfileService = lineUserProfileService;
        this.adminService = adminService;
        this.imageTranslationService = imageTranslationService;
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

            case "setlang":
                if (parts.length < 2) {
                    return new TextMessage("請指定語言代碼或名稱。例如：/setlang en 或 /setlang 日文");
                }
                String language = parts[1];
                String resultSetLang = translationService.setPreferredLanguage(userId, language);
                return new TextMessage(resultSetLang);

            case "profile":
                return new TextMessage(lineUserProfileService.getUserProfileInfo(userId));

            case "lang":
                return createLanguageSelectionMessage(userId);

            // 處理管理員命令
            case "admin":
                if (adminService.isAdmin(userId)) {
                    return handleAdminCommand(userId, parts.length > 1 ? parts[1] : "");
                } else {
                    return new TextMessage("您沒有管理員權限。");
                }

            default:
                return new TextMessage("未知命令。發送 /help 獲取可用命令列表。");
        }
    }

    /**
     * 處理管理員命令
     */
    private Message handleAdminCommand(String userId, String subCommand) {
        if (subCommand.isEmpty()) {
            return new TextMessage("管理員命令列表：\n" +
                    "/admin stats - 顯示系統統計信息\n" +
                    "/admin today - 顯示今日統計信息");
        }

        switch (subCommand.toLowerCase()) {
            case "stats":
                return new TextMessage(adminService.getSystemStats());

            case "today":
                return new TextMessage(adminService.getTodayStats());

            default:
                return new TextMessage("未知的管理員命令。可用命令：stats, today");
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
        // 解析快速翻譯格式
        String content = receivedText.substring(receivedText.indexOf(":") + 1).trim();

        if (content.isEmpty()) {
            return new TextMessage("快速翻譯格式：快速翻譯:[語言代碼] [文本]");
        }

        String[] parts = content.split("\\s+", 2);
        if (parts.length < 2) {
            return new TextMessage("請提供語言代碼和要翻譯的文本。例如：快速翻譯:en 你好");
        }

        String targetLanguage = parts[0];
        String textToTranslate = parts[1];

        String translatedText = translationService.quickTranslate(userId, textToTranslate, targetLanguage);
        return new TextMessage(translatedText);
    }

    /**
     * 創建語言選擇消息
     *
     * @param userId 用戶 ID
     * @return 包含語言選擇按鈕的消息
     */
    private Message createLanguageSelectionMessage(String userId) {
        List<String> recentLanguages = translationService.getRecentLanguages(userId);

        // 如果用戶沒有最近使用的語言，提供默認選項
        if (recentLanguages.isEmpty()) {
            recentLanguages = new ArrayList<>();
            recentLanguages.add("en");
            recentLanguages.add("ja");
            recentLanguages.add("ko");
            recentLanguages.add("zh-TW");
            recentLanguages.add("fr");
        }

        // 由於 Flex Message 相關類型不匹配，我們使用簡單的文本消息代替
        StringBuilder sb = new StringBuilder();
        sb.append("【選擇翻譯語言】\n\n");
        sb.append("請複製並使用以下格式進行翻譯：\n\n");

        for (String langCode : recentLanguages) {
            String langName = LanguageUtils.toChineseName(langCode);
            sb.append("翻譯成").append(langCode).append(" [文字]\n");
            sb.append("翻譯成").append(langName).append(" [文字]\n\n");
        }

        sb.append("您也可以使用 /setlang 命令設置默認翻譯語言");

        return new TextMessage(sb.toString());
    }


    /**
     * 獲取幫助信息
     */
    private String getHelpMessage() {
        return "LINE 翻譯機器人使用指南：\n\n" +
                "【自動翻譯】\n" +
                "直接輸入文字，機器人會自動檢測語言並翻譯：\n" +
                "• 中文 → 英文\n" +
                "• 其他語言 → 中文\n\n" +
                "【指定翻譯】\n" +
                "方式1：使用「翻譯成[語言] [文字]」\n" +
                "例如：翻譯成日文 你好\n" +
                "     翻譯成英文 這是測試\n\n" +
                "方式2：使用「翻譯成[語言代碼] [文字]」\n" +
                "例如：翻譯成ja 你好\n" +
                "     翻譯成en 這是測試\n\n" +
                "方式3：使用快速翻譯\n" +
                "例如：快速翻譯:en 你好\n\n" +
                "【命令列表】\n" +
                "/help - 顯示此幫助信息\n" +
                "/about - 關於此機器人\n" +
                "/setai openai|gemini - 設置偏好的 AI 引擎\n" +
                "/setlang [語言] - 設置默認翻譯語言\n" +
                "/lang - 顯示語言選擇菜單\n" +
                "/profile - 查看您的用戶資料";
    }

    /**
     * 獲取關於信息
     */
    private String getAboutMessage() {
        return "LINE 翻譯機器人\n\n" +
                "這是一個使用先進 AI 技術進行即時翻譯的 LINE 機器人。\n" +
                "支持 OpenAI GPT-4o 和 Google Gemini 模型。\n\n" +
                "功能：\n" +
                "• 自動語言檢測\n" +
                "• 多語言翻譯\n" +
                "• 批量多行文本翻譯\n" +
                "• 語言偏好設定\n" +
                "• 快速翻譯\n" +
                "• 圖片文字識別與翻譯（即將推出）";
    }
}