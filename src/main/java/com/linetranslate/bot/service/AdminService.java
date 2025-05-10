package com.linetranslate.bot.service;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;

import com.linetranslate.bot.config.AppConfig;
import com.linetranslate.bot.config.GeminiConfig;
import com.linetranslate.bot.config.OpenAiConfig;
import com.linetranslate.bot.model.TranslationRecord;
import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.TranslationRecordRepository;
import com.linetranslate.bot.repository.UserProfileRepository;
import com.linetranslate.bot.service.line.LineUserProfileService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AdminService {

    @Value("${admin.users:}")
    private List<String> adminUsers;
    
    // 測試模式，避免訊息真的發送給客戶
    @Value("${APP_BROADCAST_TEST_MODE:false}")
    private boolean broadcastTestMode;

    private final TranslationRecordRepository translationRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final LineMessagingClient lineMessagingClient;
    private final DateTimeFormatter dateTimeFormatter;
    private final AppConfig appConfig;
    private final OpenAiConfig openAiConfig;
    private final GeminiConfig geminiConfig;
    private final LineUserProfileService lineUserProfileService;
    
    @Autowired
    public AdminService(
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            LineMessagingClient lineMessagingClient,
            AppConfig appConfig,
            OpenAiConfig openAiConfig,
            GeminiConfig geminiConfig,
            LineUserProfileService lineUserProfileService) {
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.lineMessagingClient = lineMessagingClient;
        this.appConfig = appConfig;
        this.openAiConfig = openAiConfig;
        this.geminiConfig = geminiConfig;
        this.lineUserProfileService = lineUserProfileService;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 檢查用戶是否是管理員
     *
     * @param userId 用戶 ID
     * @return 是否是管理員
     */
    public boolean isAdmin(String userId) {
        return adminUsers.contains(userId);
    }
    
    /**
     * 獲取管理員用戶列表
     *
     * @return 管理員用戶列表
     */
    public List<String> getAdminUsers() {
        return adminUsers;
    }
    
    /**
     * 添加管理員
     *
     * @param userId 要添加為管理員的用戶 ID
     * @return 操作結果訊息
     */
    public String addAdmin(String userId) {
        log.info("嘗試添加管理員: {}", userId);
        
        // 檢查用戶 ID 是否有效
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        if (!userOpt.isPresent()) {
            log.warn("找不到用戶: {}", userId);
            return "添加管理員失敗：找不到用戶 " + userId;
        }
        
        // 檢查用戶是否已經是管理員
        if (adminUsers.contains(userId)) {
            log.info("用戶 {} 已經是管理員", userId);
            return "用戶 " + userId + " 已經是管理員";
        }
        
        // 添加到管理員列表
        adminUsers.add(userId);
        log.info("成功添加管理員: {}", userId);
        
        UserProfile user = userOpt.get();
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "用戶" + userId.substring(Math.max(0, userId.length() - 6));
        
        return "成功添加管理員：" + displayName + " (ID: " + userId + ")";
    }
    
    /**
     * 移除管理員
     *
     * @param userId 要移除管理員權限的用戶 ID
     * @return 操作結果訊息
     */
    public String removeAdmin(String userId) {
        log.info("嘗試移除管理員: {}", userId);
        
        // 檢查用戶 ID 是否有效
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        if (!userOpt.isPresent()) {
            log.warn("找不到用戶: {}", userId);
            return "移除管理員失敗：找不到用戶 " + userId;
        }
        
        // 檢查用戶是否為管理員
        if (!adminUsers.contains(userId)) {
            log.info("用戶 {} 不是管理員", userId);
            return "用戶 " + userId + " 不是管理員";
        }
        
        // 檢查是否為最後一個管理員
        if (adminUsers.size() <= 1) {
            log.warn("無法移除最後一個管理員");
            return "移除管理員失敗：無法移除最後一個管理員";
        }
        
        // 從管理員列表中移除
        adminUsers.remove(userId);
        log.info("成功移除管理員: {}", userId);
        
        UserProfile user = userOpt.get();
        String displayName = user.getDisplayName() != null ? user.getDisplayName() : "用戶" + userId.substring(Math.max(0, userId.length() - 6));
        
        return "成功移除管理員：" + displayName + " (ID: " + userId + ")";
    }

    /**
     * 向所有用戶廣播消息
     * 
     * @param message 消息內容
     * @return 發送成功的用戶數量
     */
    public int broadcastMessage(String message) {
        log.info("開始廣播消息: {}", message);
        log.info("廣播測試模式: {}", broadcastTestMode ? "已啟用" : "未啟用");
        
        // 獲取所有用戶
        List<UserProfile> allUsers = userProfileRepository.findAll();
        log.info("總用戶數: {}", allUsers.size());
        
        // 輸出所有用戶的 ID 和暱稱，以便調試
        log.info("所有用戶列表：");
        for (UserProfile user : allUsers) {
            log.info("- 用戶 ID: {}, 暱稱: {}", 
                    user.getUserId() != null ? user.getUserId() : "null", 
                    user.getDisplayName() != null ? user.getDisplayName() : "null");
        }
        
        // 過濾掉無效的用戶 ID
        List<UserProfile> validUsers = allUsers.stream()
                .filter(user -> user.getUserId() != null && !user.getUserId().isEmpty())
                .collect(Collectors.toList());
        log.info("有效用戶數: {}", validUsers.size());
        
        int successCount = 0;
        TextMessage textMessage = new TextMessage(message);
        
        for (UserProfile user : validUsers) {
            try {
                log.info("嘗試向用戶 {} (暱稱: {}) 發送廣播消息", 
                        user.getUserId(), 
                        user.getDisplayName() != null ? user.getDisplayName() : "無暱稱");
                
                if (broadcastTestMode) {
                    // 測試模式，不實際發送消息
                    log.info("測試模式啟用，模擬向用戶 {} 發送廣播消息成功", user.getUserId());
                    successCount++;
                } else {
                    // 使用 LINE Messaging API 實際發送消息
                    PushMessage pushMessage = new PushMessage(user.getUserId(), textMessage);
                    lineMessagingClient.pushMessage(pushMessage).get();
                    log.info("向用戶 {} 發送廣播消息成功", user.getUserId());
                    successCount++;
                }
            } catch (Exception e) {
                log.error("向用戶 {} 發送廣播消息時發生錯誤: {}", user.getUserId(), e.getMessage(), e);
            }
        }
        
        // 返回實際的用戶數量
        log.info("廣播消息完成，成功發送給 {} 個用戶，共 {} 個有效用戶", successCount, validUsers.size());
        return validUsers.size(); // 返回有效用戶數，而不是成功發送數
    }
    
    /**
     * 獲取最近活躍的用戶
     * 
     * @param limit 限制數量
     * @return 用戶列表
     */
    public List<Map<String, Object>> getRecentUsers(int limit) {
        log.info("獲取最近活躍的用戶，限制數量: {}", limit);
        
        // 按最後互動時間排序獲取用戶
        List<UserProfile> recentUsers = userProfileRepository.findAll().stream()
                .sorted((u1, u2) -> {
                    LocalDateTime time1 = u1.getLastInteractionAt() != null ? u1.getLastInteractionAt() : u1.getFirstInteractionAt();
                    LocalDateTime time2 = u2.getLastInteractionAt() != null ? u2.getLastInteractionAt() : u2.getFirstInteractionAt();
                    return time2.compareTo(time1);
                })
                .limit(limit)
                .collect(Collectors.toList());
        
        // 轉換為 Map 列表
        List<Map<String, Object>> result = new ArrayList<>();
        for (UserProfile user : recentUsers) {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("userId", user.getUserId());
            userMap.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "用戶" + user.getUserId().substring(0, 6));
            LocalDateTime lastActive = user.getLastInteractionAt() != null ? user.getLastInteractionAt() : user.getFirstInteractionAt();
            userMap.put("lastActiveTime", lastActive != null ? dateTimeFormatter.format(lastActive) : "N/A");
            userMap.put("totalTranslations", user.getTotalTranslations());
            result.add(userMap);
        }
        
        return result;
    }
    
    /**
     * 獲取用戶詳細信息
     * 
     * @param userId 用戶ID
     * @return 用戶詳細信息
     */
    public Map<String, Object> getUserInfo(String userId) {
        log.info("獲取用戶詳細信息: {}", userId);
        
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        if (!userOpt.isPresent()) {
            log.warn("找不到用戶: {}", userId);
            return null;
        }
        
        UserProfile user = userOpt.get();
        Map<String, Object> userInfo = new HashMap<>();
        
        // 基本信息
        userInfo.put("userId", user.getUserId());
        userInfo.put("displayName", user.getDisplayName() != null ? user.getDisplayName() : "用戶" + user.getUserId().substring(0, 6));
        userInfo.put("registrationTime", user.getFirstInteractionAt() != null ? dateTimeFormatter.format(user.getFirstInteractionAt()) : "N/A");
        userInfo.put("lastActiveTime", user.getLastInteractionAt() != null ? dateTimeFormatter.format(user.getLastInteractionAt()) : "N/A");
        
        // 統計信息
        userInfo.put("translationCount", user.getTotalTranslations());
        userInfo.put("textTranslationCount", user.getTextTranslations());
        userInfo.put("imageTranslationCount", user.getImageTranslations());
        
        // 用戶設置
        userInfo.put("preferredLanguage", user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "N/A");
        userInfo.put("preferredChineseTargetLanguage", user.getPreferredChineseTargetLanguage() != null ? user.getPreferredChineseTargetLanguage() : "N/A");
        userInfo.put("preferredAiProvider", user.getPreferredAiProvider() != null ? user.getPreferredAiProvider() : "N/A");
        userInfo.put("openaiPreferredModel", user.getOpenaiPreferredModel() != null ? user.getOpenaiPreferredModel() : "N/A");
        userInfo.put("geminiPreferredModel", user.getGeminiPreferredModel() != null ? user.getGeminiPreferredModel() : "N/A");
        
        return userInfo;
    }
    
    /**
     * 獲取系統統計信息
     *
     * @return 統計信息字符串
     */
    public String getSystemStats() {
        log.info("獲取系統統計信息");
        
        // 統計基本數據
        long totalUsers = userProfileRepository.count();
        long totalTranslations = translationRecordRepository.count();
        long imageTranslations = translationRecordRepository.countByIsImageTranslation(true);
        long textTranslations = totalTranslations - imageTranslations;
        
        // 獲取過去24小時的活躍用戶
        LocalDateTime yesterday = LocalDateTime.now().minusDays(1);
        long activeUsersLast24h = userProfileRepository.findByLastInteractionAtAfter(yesterday).size();
        
        // 統計每個 AI 提供商的使用情況
        long openaiCount = translationRecordRepository.countByAiProvider("openai");
        long geminiCount = translationRecordRepository.countByAiProvider("gemini");
        
        // 統計過去7天的翻譯量
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        List<TranslationRecord> recentTranslations = translationRecordRepository.findByCreatedAtBetween(weekAgo, LocalDateTime.now());
        
        // 計算每天的翻譯量
        Map<String, Long> dailyTranslations = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        
        for (TranslationRecord record : recentTranslations) {
            if (record.getCreatedAt() != null) {
                String day = record.getCreatedAt().format(formatter);
                dailyTranslations.put(day, dailyTranslations.getOrDefault(day, 0L) + 1);
            }
        }
        
        // 格式化輸出
        StringBuilder stats = new StringBuilder();
        stats.append("系統統計\n");
        stats.append("-------------------\n");
        stats.append("總用戶數: ").append(totalUsers).append("\n");
        stats.append("總翻譯次數: ").append(totalTranslations).append("\n");
        stats.append("  文字翻譯: ").append(textTranslations).append("\n");
        stats.append("  圖片翻譯: ").append(imageTranslations).append("\n");
        stats.append("過去24小時活躍用戶: ").append(activeUsersLast24h).append("\n");
        stats.append("\nAI 提供商使用情況\n");
        stats.append("-------------------\n");
        
        if (totalTranslations > 0) {
            stats.append("OpenAI: ").append(openaiCount).append(" (").append(String.format("%.1f%%", (double)openaiCount/totalTranslations*100)).append(")\n");
            stats.append("Gemini: ").append(geminiCount).append(" (").append(String.format("%.1f%%", (double)geminiCount/totalTranslations*100)).append(")\n");
        } else {
            stats.append("OpenAI: 0 (0.0%)\n");
            stats.append("Gemini: 0 (0.0%)\n");
        }
        
        stats.append("\n過去7天翻譯量\n");
        stats.append("-------------------\n");
        
        // 按日期排序顯示每天的翻譯量
        List<String> sortedDays = new ArrayList<>(dailyTranslations.keySet());
        Collections.sort(sortedDays);
        
        for (String day : sortedDays) {
            stats.append(day).append(": ").append(dailyTranslations.get(day)).append("\n");
        }
        
        return stats.toString();
    }
    
    /**
     * 獲取今日統計信息
     *
     * @return 今日統計信息
     */
    public String getTodayStats() {
        // 獲取今日開始和結束時間
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 獲取今日翻譯記錄
        List<TranslationRecord> todayRecords = translationRecordRepository.findByCreatedAtBetween(todayStart, todayEnd);

        // 計算統計數據
        long totalTranslations = todayRecords.size();
        long textTranslations = todayRecords.stream().filter(r -> !r.isImageTranslation()).count();
        long imageTranslations = todayRecords.stream().filter(TranslationRecord::isImageTranslation).count();

        // 計算平均處理時間
        double avgProcessingTime = todayRecords.stream()
                .mapToDouble(TranslationRecord::getProcessingTimeMs)
                .average()
                .orElse(0.0);

        // 獲取 AI 提供者使用統計
        Map<String, Long> providerStats = new HashMap<>();
        for (TranslationRecord record : todayRecords) {
            String provider = record.getAiProvider();
            if (provider != null) {
                providerStats.put(provider, providerStats.getOrDefault(provider, 0L) + 1);
            }
        }

        // 生成統計信息字符串
        StringBuilder statsBuilder = new StringBuilder();
        statsBuilder.append("【今日統計】\n\n");

        statsBuilder.append("今日總翻譯次數：").append(totalTranslations).append("\n");
        statsBuilder.append("文字翻譯：").append(textTranslations).append(" 次\n");
        statsBuilder.append("圖片翻譯：").append(imageTranslations).append(" 次\n");
        statsBuilder.append("平均處理時間：").append(String.format("%.2f", avgProcessingTime / 1000)).append(" 秒\n\n");

        statsBuilder.append("【AI 提供者使用情況】\n");
        for (Map.Entry<String, Long> entry : providerStats.entrySet()) {
            statsBuilder.append(entry.getKey()).append("：").append(entry.getValue()).append(" 次\n");
        }
        
        return statsBuilder.toString();
    }
    
    /**
     * 獲取系統配置信息
     *
     * @return 系統配置信息字符串
     */
    public String getSystemConfig() {
        StringBuilder configBuilder = new StringBuilder();
        configBuilder.append("⚙️ 系統配置信息\n\n");
        
        // 翻譯相關配置
        configBuilder.append("【翻譯設定】\n");
        configBuilder.append("• 中文翻譯默認目標語言: ").append(appConfig.getDefaultTargetLanguageForChinese()).append("\n");
        configBuilder.append("• 其他語言翻譯默認目標語言: ").append(appConfig.getDefaultTargetLanguageForOthers()).append("\n");
        configBuilder.append("• OCR 功能: ").append(appConfig.isOcrEnabled() ? "已啟用" : "已禁用").append("\n\n");
        
        // AI 提供者配置
        configBuilder.append("【AI 提供者設定】\n");
        configBuilder.append("• 默認 AI 提供者: ").append(appConfig.getDefaultAiProvider()).append("\n\n");
        
        // OpenAI 配置
        configBuilder.append("【OpenAI 設定】\n");
        configBuilder.append("• 默認模型: ").append(openAiConfig.getModelName()).append("\n");
        configBuilder.append("• 可用模型: ").append(String.join(", ", openAiConfig.getAvailableModels())).append("\n");
        configBuilder.append("• API 狀態: ").append(openAiConfig.getApiKey() != null && !openAiConfig.getApiKey().isEmpty() ? "已配置" : "未配置").append("\n\n");
        
        // Gemini 配置
        configBuilder.append("【Gemini 設定】\n");
        configBuilder.append("• 默認模型: ").append(geminiConfig.getModelName()).append("\n");
        configBuilder.append("• 可用模型: ").append(String.join(", ", geminiConfig.getAvailableModels())).append("\n");
        configBuilder.append("• API 狀態: ").append(geminiConfig.getApiKey() != null && !geminiConfig.getApiKey().isEmpty() ? "已配置" : "未配置").append("\n\n");
        
        // 管理員設定
        configBuilder.append("【管理員設定】\n");
        configBuilder.append("• 管理員數量: ").append(adminUsers.size()).append("\n");
        configBuilder.append("• 管理員列表: \n");
        
        for (String adminId : adminUsers) {
            Optional<UserProfile> userOpt = userProfileRepository.findByUserId(adminId);
            String displayName = userOpt.map(UserProfile::getDisplayName).orElse("未知用戶");
            configBuilder.append("  - ").append(displayName).append(" (ID: ").append(adminId).append(")\n");
        }
        
        return configBuilder.toString();
    }
    
    /**
     * 設置中文翻譯默認目標語言
     *
     * @param language 語言代碼
     * @return 操作結果訊息
     */
    public String setDefaultTargetLanguageForChinese(String language) {
        try {
            // 使用反射修改 AppConfig 中的屬性值
            Field field = AppConfig.class.getDeclaredField("defaultTargetLanguageForChinese");
            field.setAccessible(true);
            String oldValue = (String) field.get(appConfig);
            field.set(appConfig, language);
            
            log.info("已將中文翻譯默認目標語言從 {} 修改為 {}", oldValue, language);
            return "✅ 已將中文翻譯默認目標語言設置為: " + language;
        } catch (Exception e) {
            log.error("設置中文翻譯默認目標語言失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
     * 設置其他語言翻譯默認目標語言
     *
     * @param language 語言代碼
     * @return 操作結果訊息
     */
    public String setDefaultTargetLanguageForOthers(String language) {
        try {
            // 使用反射修改 AppConfig 中的屬性值
            Field field = AppConfig.class.getDeclaredField("defaultTargetLanguageForOthers");
            field.setAccessible(true);
            String oldValue = (String) field.get(appConfig);
            field.set(appConfig, language);
            
            log.info("已將其他語言翻譯默認目標語言從 {} 修改為 {}", oldValue, language);
            return "✅ 已將其他語言翻譯默認目標語言設置為: " + language;
        } catch (Exception e) {
            log.error("設置其他語言翻譯默認目標語言失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
     * 設置默認 AI 提供者
     *
     * @param provider AI 提供者 (openai 或 gemini)
     * @return 操作結果訊息
     */
    public String setDefaultAiProvider(String provider) {
        if (!"openai".equalsIgnoreCase(provider) && !"gemini".equalsIgnoreCase(provider)) {
            return "❌ 無效的 AI 提供者。有效值為: openai, gemini";
        }
        
        try {
            // 使用反射修改 AppConfig 中的屬性值
            Field field = AppConfig.class.getDeclaredField("defaultAiProvider");
            field.setAccessible(true);
            String oldValue = (String) field.get(appConfig);
            field.set(appConfig, provider.toLowerCase());
            
            log.info("已將默認 AI 提供者從 {} 修改為 {}", oldValue, provider.toLowerCase());
            return "✅ 已將默認 AI 提供者設置為: " + provider.toLowerCase();
        } catch (Exception e) {
            log.error("設置默認 AI 提供者失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
     * 設置 OpenAI 默認模型
     *
     * @param model 模型名稱
     * @return 操作結果訊息
     */
    public String setOpenAiDefaultModel(String model) {
        // 檢查模型是否在可用列表中
        if (!openAiConfig.getAvailableModels().contains(model)) {
            return "❌ 無效的 OpenAI 模型。可用模型: " + String.join(", ", openAiConfig.getAvailableModels());
        }
        
        try {
            // 使用反射修改 OpenAiConfig 中的屬性值
            Field field = OpenAiConfig.class.getDeclaredField("modelName");
            field.setAccessible(true);
            String oldValue = (String) field.get(openAiConfig);
            field.set(openAiConfig, model);
            
            // 同時更新 AppConfig 中的默認模型
            Field appField = AppConfig.class.getDeclaredField("openaiDefaultModel");
            appField.setAccessible(true);
            appField.set(appConfig, model);
            
            log.info("已將 OpenAI 默認模型從 {} 修改為 {}", oldValue, model);
            return "✅ 已將 OpenAI 默認模型設置為: " + model;
        } catch (Exception e) {
            log.error("設置 OpenAI 默認模型失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
     * 設置 Gemini 默認模型
     *
     * @param model 模型名稱
     * @return 操作結果訊息
     */
    public String setGeminiDefaultModel(String model) {
        // 檢查模型是否在可用列表中
        if (!geminiConfig.getAvailableModels().contains(model)) {
            return "❌ 無效的 Gemini 模型。可用模型: " + String.join(", ", geminiConfig.getAvailableModels());
        }
        
        try {
            // 使用反射修改 GeminiConfig 中的屬性值
            Field field = GeminiConfig.class.getDeclaredField("modelName");
            field.setAccessible(true);
            String oldValue = (String) field.get(geminiConfig);
            field.set(geminiConfig, model);
            
            // 同時更新 AppConfig 中的默認模型
            Field appField = AppConfig.class.getDeclaredField("geminiDefaultModel");
            appField.setAccessible(true);
            appField.set(appConfig, model);
            
            log.info("已將 Gemini 默認模型從 {} 更改為 {}", oldValue, model);
            return "✅ 已將 Gemini 默認模型設置為: " + model;
        } catch (Exception e) {
            log.error("設置 Gemini 默認模型失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
     * 設置 OCR 功能開關
     *
     * @param enabled 是否啟用 OCR
     * @return 操作結果訊息
     */
    public String setOcrEnabled(boolean enabled) {
        try {
            // 使用反射修改 AppConfig 中的屬性值
            Field field = AppConfig.class.getDeclaredField("ocrEnabled");
            field.setAccessible(true);
            boolean oldValue = (boolean) field.get(appConfig);
            field.set(appConfig, enabled);
            
            log.info("已將 OCR 功能從 {} 更改為 {}", oldValue, enabled);
            return "✅ 已" + (enabled ? "啟用" : "禁用") + " OCR 功能";
        } catch (Exception e) {
            log.error("設置 OCR 功能失敗", e);
            return "❌ 設置失敗: " + e.getMessage();
        }
    }
    
    /**
 * 獲取當前月份的 API 使用量和費用統計
 *
 * @return API 使用量和費用統計信息
 */
public String getApiUsageStats() {
    // 獲取當前年月
    LocalDate now = LocalDate.now();
    String yearMonth = now.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    
    return getApiUsageStatsByMonth(yearMonth);
}

/**
 * 獲取指定月份的 API 使用量和費用統計
 *
 * @param month 月份，格式為 YYYY-MM
 * @return API 使用量和費用統計信息
 */
public String getApiUsageStatsByMonth(String month) {
    try {
        // 解析年月
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        LocalDate startDate = LocalDate.parse(month + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        
        // 計算該月的開始和結束日期
        LocalDate endDate = startDate.plusMonths(1).minusDays(1);
        
        // 查詢該月的翻譯記錄
        List<TranslationRecord> records = translationRecordRepository.findByCreatedAtBetween(
                startDate.atStartOfDay(),
                endDate.atTime(LocalTime.MAX)
        );
        
        // 如果沒有記錄，返回提示信息
        if (records.isEmpty()) {
            return "💰 " + month + " 的 API 使用量和費用\n\n該月沒有任何 API 使用記錄。";
        }
        
        // 統計使用量和費用
        return calculateApiUsageStats(records, month);
        
    } catch (Exception e) {
        log.error("獲取 API 使用量和費用統計失敗", e);
        return "❌ 獲取 API 使用量和費用統計失敗: " + e.getMessage();
    }
}

/**
 * 按 AI 提供者獲取 API 使用量和費用統計
 *
 * @param provider AI 提供者 (openai 或 gemini)
 * @return API 使用量和費用統計信息
 */
public String getApiUsageStatsByProvider(String provider) {
    if (!"openai".equalsIgnoreCase(provider) && !"gemini".equalsIgnoreCase(provider)) {
        return "❌ 無效的 AI 提供者。請使用 'openai' 或 'gemini'。";
    }
    
    try {
        // 查詢該提供者的所有翻譯記錄
        List<TranslationRecord> records = translationRecordRepository.findByAiProvider(provider.toLowerCase());
        
        // 如果沒有記錄，返回提示信息
        if (records.isEmpty()) {
            return "💰 " + provider + " 的 API 使用量和費用\n\n沒有任何 " + provider + " 的 API 使用記錄。";
        }
        
        // 統計使用量和費用
        return calculateApiUsageStats(records, provider + " 提供者");
        
    } catch (Exception e) {
        log.error("獲取 API 使用量和費用統計失敗", e);
        return "❌ 獲取 API 使用量和費用統計失敗: " + e.getMessage();
    }
}

/**
 * 獲取所有時間的 API 使用量和費用摘要
 *
 * @return API 使用量和費用摘要信息
 */
public String getApiUsageSummary() {
    try {
        // 查詢所有翻譯記錄
        List<TranslationRecord> allRecords = translationRecordRepository.findAll();
        
        // 如果沒有記錄，返回提示信息
        if (allRecords.isEmpty()) {
            return "💰 API 使用量和費用摘要\n\n沒有任何 API 使用記錄。";
        }
        
        // 按月份分組統計
        Map<String, List<TranslationRecord>> recordsByMonth = allRecords.stream()
                .collect(Collectors.groupingBy(record -> 
                        record.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"))));
        
        // 按提供者分組統計
        Map<String, List<TranslationRecord>> recordsByProvider = allRecords.stream()
                .collect(Collectors.groupingBy(TranslationRecord::getAiProvider));
        
        // 生成摘要信息
        StringBuilder summary = new StringBuilder();
        summary.append("💰 API 使用量和費用摘要\n\n");
        
        // 月份摘要
        summary.append("【按月份統計】\n");
        recordsByMonth.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String month = entry.getKey();
                    List<TranslationRecord> records = entry.getValue();
                    int totalRequests = records.size();
                    double totalCost = calculateTotalCost(records);
                    summary.append(month).append(": ")
                           .append(totalRequests).append(" 次請求, ")
                           .append(String.format("$%.2f", totalCost)).append("\n");
                });
        
        summary.append("\n");
        
        // 提供者摘要
        summary.append("【按提供者統計】\n");
        recordsByProvider.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String provider = entry.getKey();
                    List<TranslationRecord> records = entry.getValue();
                    int totalRequests = records.size();
                    double totalCost = calculateTotalCost(records);
                    summary.append(provider).append(": ")
                           .append(totalRequests).append(" 次請求, ")
                           .append(String.format("$%.2f", totalCost)).append("\n");
                });
        
        summary.append("\n");
        
        // 總計
        int totalRequests = allRecords.size();
        double totalCost = calculateTotalCost(allRecords);
        summary.append("【總計】\n");
        summary.append("總請求次數: ").append(totalRequests).append("\n");
        summary.append("總費用: $").append(String.format("%.2f", totalCost)).append("\n");
        summary.append("平均每次請求費用: $").append(String.format("%.4f", totalCost / totalRequests)).append("\n");
        
        return summary.toString();
        
    } catch (Exception e) {
        log.error("獲取 API 使用量和費用摘要失敗", e);
        return "❌ 獲取 API 使用量和費用摘要失敗: " + e.getMessage();
    }
}

/**
 * 計算 API 使用量和費用統計
 *
 * @param records 翻譯記錄列表
 * @param title 統計標題
 * @return API 使用量和費用統計信息
 */
private String calculateApiUsageStats(List<TranslationRecord> records, String title) {
    // 統計使用量
    int totalRequests = records.size();
    long textTranslations = records.stream().filter(r -> !r.isImageTranslation()).count();
    long imageTranslations = records.stream().filter(TranslationRecord::isImageTranslation).count();
    
    // 統計 Vision API 的使用量
    long visionApiUsage = records.stream()
            .filter(TranslationRecord::isImageTranslation)
            .count();
    
    // 按提供者和模型分組
    Map<String, Long> providerStats = records.stream()
            .collect(Collectors.groupingBy(TranslationRecord::getAiProvider, Collectors.counting()));
    
    Map<String, Long> modelStats = records.stream()
            .collect(Collectors.groupingBy(TranslationRecord::getModelName, Collectors.counting()));
    
    // 計算每個提供者的費用
    Map<String, Double> providerCosts = new HashMap<>();
    for (String provider : providerStats.keySet()) {
        double cost = records.stream()
                .filter(r -> provider.equals(r.getAiProvider()))
                .mapToDouble(this::calculateRecordCost)
                .sum();
        providerCosts.put(provider, cost);
    }
    
    // 計算總費用
    double totalCost = records.stream()
            .mapToDouble(this::calculateRecordCost)
            .sum();
    
    // 生成統計信息
    StringBuilder statsBuilder = new StringBuilder();
    statsBuilder.append("💰 ").append(title).append(" 的 API 使用量和費用\n\n");
    
    statsBuilder.append("【使用量統計】\n");
    statsBuilder.append("總請求次數: ").append(totalRequests).append("\n");
    statsBuilder.append("文字翻譯: ").append(textTranslations).append(" 次\n");
    statsBuilder.append("圖片翻譯: ").append(imageTranslations).append(" 次\n");
    statsBuilder.append("Vision API 使用量: ").append(visionApiUsage).append(" 次\n\n");
    
    statsBuilder.append("【提供者使用情況】\n");
    for (Map.Entry<String, Long> entry : providerStats.entrySet()) {
        String provider = entry.getKey();
        long count = entry.getValue();
        double cost = providerCosts.getOrDefault(provider, 0.0);
        statsBuilder.append(provider).append(": ")
               .append(count).append(" 次, ")
               .append(String.format("$%.2f", cost)).append("\n");
    }
    statsBuilder.append("\n");
    
    statsBuilder.append("【模型使用情況】\n");
    for (Map.Entry<String, Long> entry : modelStats.entrySet()) {
        statsBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append(" 次\n");
    }
    statsBuilder.append("\n");
    
    // 添加圖片翻譯的詳細統計
    if (imageTranslations > 0) {
        statsBuilder.append("【圖片翻譯統計】\n");
        // 按提供者分組圖片翻譯
        Map<String, Long> imageProviderStats = records.stream()
                .filter(TranslationRecord::isImageTranslation)
                .collect(Collectors.groupingBy(TranslationRecord::getAiProvider, Collectors.counting()));
        
        for (Map.Entry<String, Long> entry : imageProviderStats.entrySet()) {
            statsBuilder.append(entry.getKey()).append(" 圖片翻譯: ").append(entry.getValue()).append(" 次\n");
        }
        statsBuilder.append("\n");
    }
    
    statsBuilder.append("【費用統計】\n");
    statsBuilder.append("總費用: $").append(String.format("%.2f", totalCost)).append("\n");
    statsBuilder.append("平均每次請求費用: $").append(String.format("%.4f", totalCost / totalRequests)).append("\n");
    
    return statsBuilder.toString();
}

/**
 * 計算單條翻譯記錄的費用
 *
 * @param record 翻譯記錄
 * @return 該記錄的費用
 */
private double calculateRecordCost(TranslationRecord record) {
    String provider = record.getAiProvider();
    String model = record.getModelName();
    boolean isImageTranslation = record.isImageTranslation();
    
    // 估算輸入和輸出的令牌數
    // 我們沒有實際的令牌數，所以根據文本長度估算
    int inputTokens = 0;
    int outputTokens = 0;
    
    if (record.getSourceText() != null) {
        // 大約每 4 個字符為 1 個令牌
        inputTokens = record.getSourceText().length() / 4 + 1;
    }
    
    if (record.getTranslatedText() != null) {
        // 大約每 4 個字符為 1 個令牌
        outputTokens = record.getTranslatedText().length() / 4 + 1;
    }
    
    double cost = 0.0;
    
    // 根據不同的提供者和模型計算費用
    if ("openai".equals(provider)) {
        // OpenAI 的費用計算 (價格單位: $/1K tokens)
        switch (model) {
            // 最新模型價格
            case "gpt-4.1":
            case "gpt-4.1-2025-04-14":
                cost += (inputTokens / 1000.0) * 2.00 + (outputTokens / 1000.0) * 8.00;
                break;
            case "gpt-4.1-mini":
            case "gpt-4.1-mini-2025-04-14":
                cost += (inputTokens / 1000.0) * 0.40 + (outputTokens / 1000.0) * 1.60;
                break;
            case "gpt-4.1-nano":
            case "gpt-4.1-nano-2025-04-14":
                cost += (inputTokens / 1000.0) * 0.10 + (outputTokens / 1000.0) * 0.40;
                break;
            case "gpt-4.5-preview":
            case "gpt-4.5-preview-2025-02-27":
                cost += (inputTokens / 1000.0) * 75.00 + (outputTokens / 1000.0) * 150.00;
                break;
            case "gpt-4o":
            case "gpt-4o-2024-08-06":
                cost += (inputTokens / 1000.0) * 2.50 + (outputTokens / 1000.0) * 10.00;
                break;
            case "gpt-4o-mini":
            case "gpt-4o-mini-2024-07-18":
                cost += (inputTokens / 1000.0) * 0.15 + (outputTokens / 1000.0) * 0.60;
                break;
            case "o1":
            case "o1-2024-12-17":
                cost += (inputTokens / 1000.0) * 15.00 + (outputTokens / 1000.0) * 60.00;
                break;
            case "o1-pro":
            case "o1-pro-2025-03-19":
                cost += (inputTokens / 1000.0) * 150.00 + (outputTokens / 1000.0) * 600.00;
                break;
            case "o3":
            case "o3-2025-04-16":
                cost += (inputTokens / 1000.0) * 10.00 + (outputTokens / 1000.0) * 40.00;
                break;
            case "o4-mini":
            case "o4-mini-2025-04-16":
                cost += (inputTokens / 1000.0) * 1.10 + (outputTokens / 1000.0) * 4.40;
                break;
            case "o3-mini":
            case "o3-mini-2025-01-31":
                cost += (inputTokens / 1000.0) * 1.10 + (outputTokens / 1000.0) * 4.40;
                break;
            case "o1-mini":
            case "o1-mini-2024-09-12":
                cost += (inputTokens / 1000.0) * 1.10 + (outputTokens / 1000.0) * 4.40;
                break;
            // 舊模型
            case "gpt-4":
                cost += (inputTokens / 1000.0) * 0.03 + (outputTokens / 1000.0) * 0.06;
                break;
            case "gpt-3.5-turbo":
                cost += (inputTokens / 1000.0) * 0.0005 + (outputTokens / 1000.0) * 0.0015;
                break;
            default:
                // 默認使用 gpt-4o 的費用
                cost += (inputTokens / 1000.0) * 2.50 + (outputTokens / 1000.0) * 10.00;
                break;
        }
        
        // 如果是圖片翻譯，加上圖片計算費用
        if (isImageTranslation) {
            // 使用 gpt-image-1 的價格: $5.00/1K tokens
            cost += 5.00 / 1000.0 * 500; // 估算每張圖片約 500 tokens
        }
    } else if ("gemini".equals(provider)) {
        // Gemini 的費用計算 (Gemini 目前免費)
        cost = 0.0;
        
        // 雖然 Gemini 免費，但我們仍然記錄使用量
        // 這裡可以添加 Vision API 的使用量統計
        // 注意：這裡只是記錄使用量，不計算費用
    }
    
    return cost;
}

/**
 * 計算翻譯記錄的總費用
 *
 * @param records 翻譯記錄列表
 * @return 總費用
 */
private double calculateTotalCost(List<TranslationRecord> records) {
    double totalCost = 0.0;
    
    for (TranslationRecord record : records) {
        totalCost += calculateRecordCost(record);
    }
    
    return totalCost;
}

/**
 * 設置用戶的顯示名稱
 *
 * @param userId 用戶 ID
 * @param displayName 新的顯示名稱
 * @return 操作結果訊息
 */
public String setUserDisplayName(String userId, String displayName) {
    return lineUserProfileService.setUserDisplayName(userId, displayName);
}

}
