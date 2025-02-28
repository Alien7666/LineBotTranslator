package com.linetranslate.bot.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

    private final TranslationRecordRepository translationRecordRepository;
    private final UserProfileRepository userProfileRepository;

    @Autowired
    public AdminService(
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository) {
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
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
     * 獲取系統統計信息
     *
     * @return 統計信息
     */
    public String getSystemStats() {
        long totalUsers = userProfileRepository.count();
        long totalTranslations = translationRecordRepository.count();

        // 獲取今日開始和結束時間
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 獲取 AI 提供者使用統計
        Map<String, Long> providerStats = new HashMap<>();
        List<TranslationRecord> allRecords = translationRecordRepository.findAll();

        for (TranslationRecord record : allRecords) {
            String provider = record.getAiProvider();
            providerStats.put(provider, providerStats.getOrDefault(provider, 0L) + 1);
        }

        // 生成統計信息字符串
        StringBuilder statsBuilder = new StringBuilder();
        statsBuilder.append("【系統統計】\n\n");

        statsBuilder.append("總用戶數：").append(totalUsers).append("\n");
        statsBuilder.append("總翻譯次數：").append(totalTranslations).append("\n\n");

        statsBuilder.append("【AI 提供者使用情況】\n");
        for (Map.Entry<String, Long> entry : providerStats.entrySet()) {
            statsBuilder.append(entry.getKey()).append("：").append(entry.getValue()).append(" 次\n");
        }

        statsBuilder.append("\n【活躍用戶排行】\n");
        List<UserProfile> activeUsers = userProfileRepository.findTop10ByOrderByTotalTranslationsDesc();

        int rank = 1;
        for (UserProfile user : activeUsers) {
            statsBuilder.append(rank++).append(". ")
                    .append(user.getDisplayName() != null ? user.getDisplayName() : "用戶")
                    .append("：").append(user.getTotalTranslations()).append(" 次\n");
        }

        return statsBuilder.toString();
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
            providerStats.put(provider, providerStats.getOrDefault(provider, 0L) + 1);
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