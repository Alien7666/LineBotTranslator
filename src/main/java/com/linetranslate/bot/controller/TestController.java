package com.linetranslate.bot.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linetranslate.bot.model.TranslationRecord;
import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.TranslationRecordRepository;
import com.linetranslate.bot.repository.UserProfileRepository;
import com.linetranslate.bot.service.storage.MinioStorageService;

import lombok.extern.slf4j.Slf4j;

/**
 * 測試控制器，用於驗證資料庫連接和儲存功能
 * 僅用於開發和測試環境
 */
@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {

    private final TranslationRecordRepository translationRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final MinioStorageService minioStorageService;

    @Autowired
    public TestController(
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            MinioStorageService minioStorageService) {
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 測試資料庫連接和儲存功能
     */
    @GetMapping("/db")
    public Map<String, Object> testDatabase() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 測試用戶資料儲存
            String testUserId = "test_user_" + System.currentTimeMillis();
            UserProfile userProfile = UserProfile.builder()
                    .userId(testUserId)
                    .firstInteractionAt(LocalDateTime.now())
                    .lastInteractionAt(LocalDateTime.now())
                    .totalTranslations(1)
                    .textTranslations(1)
                    .build();
            
            UserProfile savedUserProfile = userProfileRepository.save(userProfile);
            result.put("userProfileSaved", savedUserProfile != null);
            result.put("userProfileId", savedUserProfile != null ? savedUserProfile.getUserId() : "unknown");
            
            // 測試翻譯記錄儲存
            TranslationRecord record = TranslationRecord.builder()
                    .userId(testUserId)
                    .sourceText("測試文本")
                    .sourceLanguage("zh-TW")
                    .targetLanguage("en")
                    .translatedText("Test text")
                    .aiProvider("openai")
                    .modelName("gpt-4o")
                    .createdAt(LocalDateTime.now())
                    .processingTimeMs(100)
                    .isImageTranslation(false)
                    .build();
            
            TranslationRecord savedRecord = translationRecordRepository.save(record);
            result.put("translationRecordSaved", savedRecord != null);
            result.put("translationRecordId", savedRecord != null ? savedRecord.getId() : "unknown");
            
            // 測試查詢功能
            long recordCount = translationRecordRepository.count();
            result.put("totalRecords", recordCount);
            
            result.put("status", "success");
            result.put("message", "資料庫連接和儲存功能正常");
            
            log.info("資料庫測試成功: {}", result);
        } catch (Exception e) {
            log.error("資料庫測試失敗: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "資料庫測試失敗: " + e.getMessage());
            result.put("error", e.toString());
        }
        
        return result;
    }
    
    /**
     * 測試 MinIO 連接和儲存功能
     */
    @GetMapping("/minio")
    public Map<String, Object> testMinio() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 創建一個簡單的測試圖片（1x1 像素的黑色 PNG）
            byte[] testImage = {
                (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47, (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0D, (byte) 0x49, (byte) 0x48, (byte) 0x44, (byte) 0x52,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x01,
                (byte) 0x08, (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x90, (byte) 0x77, (byte) 0x53,
                (byte) 0xDE, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x0C, (byte) 0x49, (byte) 0x44, (byte) 0x41,
                (byte) 0x54, (byte) 0x08, (byte) 0xD7, (byte) 0x63, (byte) 0x60, (byte) 0x00, (byte) 0x00, (byte) 0x00,
                (byte) 0x02, (byte) 0x00, (byte) 0x01, (byte) 0xE2, (byte) 0x21, (byte) 0xBC, (byte) 0x33, (byte) 0x00,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x49, (byte) 0x45, (byte) 0x4E, (byte) 0x44, (byte) 0xAE,
                (byte) 0x42, (byte) 0x60, (byte) 0x82
            };
            
            // 上傳測試圖片到 MinIO
            String imageUrl = minioStorageService.uploadImage(testImage, "image/png");
            result.put("imageUploaded", imageUrl != null && !imageUrl.isEmpty());
            result.put("imageUrl", imageUrl);
            
            result.put("status", "success");
            result.put("message", "MinIO 連接和儲存功能正常");
            
            log.info("MinIO 測試成功: {}", result);
        } catch (Exception e) {
            log.error("MinIO 測試失敗: {}", e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "MinIO 測試失敗: " + e.getMessage());
            result.put("error", e.toString());
        }
        
        return result;
    }
}
