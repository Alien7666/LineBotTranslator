package com.linetranslate.bot.service;

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
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.PushMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linetranslate.bot.model.TranslationRecord;
import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.TranslationRecordRepository;
import com.linetranslate.bot.repository.UserProfileRepository;

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
    
    @Autowired
    public AdminService(
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            LineMessagingClient lineMessagingClient) {
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.lineMessagingClient = lineMessagingClient;
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
}
