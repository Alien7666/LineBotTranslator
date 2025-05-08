package com.linetranslate.bot.service.ocr;


import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linecorp.bot.client.LineBlobClient;
import com.linecorp.bot.client.MessageContentResponse;
import com.linetranslate.bot.config.AppConfig;
import com.linetranslate.bot.model.TranslationRecord;
import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.TranslationRecordRepository;
import com.linetranslate.bot.repository.UserProfileRepository;
import com.linetranslate.bot.service.ai.AiService;
import com.linetranslate.bot.service.ai.AiServiceFactory;
import com.linetranslate.bot.service.storage.MinioStorageService;
import com.linetranslate.bot.service.translation.LanguageDetectionService;
import com.linetranslate.bot.service.translation.TranslationService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ImageTranslationService {

    private final OcrService ocrService;
    private final TranslationService translationService;
    private final LanguageDetectionService languageDetectionService;
    private final AiServiceFactory aiServiceFactory;
    private final LineBlobClient lineBlobClient;
    private final TranslationRecordRepository translationRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final AppConfig appConfig;
    private final MinioStorageService minioStorageService;

    @Value("${app.ocr.enabled:true}")
    private boolean ocrEnabled;
    
    /**
     * 檢查 OCR 功能是否啟用
     * 
     * @return OCR 功能是否啟用
     */
    public boolean isOcrEnabled() {
        return ocrEnabled;
    }

    @Autowired
    public ImageTranslationService(
            @Autowired(required = false) OcrService ocrService,
            TranslationService translationService,
            LanguageDetectionService languageDetectionService,
            AiServiceFactory aiServiceFactory,
            LineBlobClient lineBlobClient,
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            AppConfig appConfig,
            MinioStorageService minioStorageService) {
        this.ocrService = ocrService;
        this.translationService = translationService;
        this.languageDetectionService = languageDetectionService;
        this.aiServiceFactory = aiServiceFactory;
        this.lineBlobClient = lineBlobClient;
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.appConfig = appConfig;
        this.minioStorageService = minioStorageService;
    }

    /**
     * 處理圖片翻譯
     *
     * @param userId 用戶 ID
     * @param messageId 圖片消息 ID
     * @return 翻譯結果
     */
    public String processImageTranslation(String userId, String messageId) {
        if (!ocrEnabled) {
            return "OCR 功能目前已停用。請稍後再試。";
        }

        Instant start = Instant.now();
        log.info("處理用戶 {} 的圖片翻譯請求, 圖片 ID: {}", userId, messageId);

        try {
            // 獲取用戶資料
            UserProfile userProfile = ensureUserProfileExists(userId);

            // 獲取圖片內容並轉換為Base64
            String recognizedText;

            try {
                MessageContentResponse response = lineBlobClient.getMessageContent(messageId).get();
                byte[] imageBytes = response.getStream().readAllBytes();
                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                
                // 上傳圖片到 MinIO 並獲取 URL
                // LINE 平台的圖片通常是 JPEG 格式
                String contentType = "image/jpeg";
                String imageUrl = minioStorageService.uploadImage(imageBytes, contentType);
                log.info("圖片已上傳到 MinIO，URL: {}", imageUrl);

                // 準備OCR識別文字
                if (ocrService != null) {
                    // 如果Google Vision可用，使用它
                    recognizedText = ocrService.recognizeText(new java.io.ByteArrayInputStream(imageBytes));
                } else {
                    // 使用AI服務進行圖像識別
                    log.info("Google Vision不可用，使用AI模型識別圖片文字");

                    // 選擇AI服務
                    AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

                    // 構建提示詞
                    String prompt = "請識別這張圖片中的所有文字，只返回文字內容，不要添加任何其他描述或解釋。";

                    // 處理圖片
                    recognizedText = aiService.processImage(prompt, "data:image/jpeg;base64," + base64Image);
                }
                
                // 保存圖片 URL 到 ThreadLocal 變量，以便在保存翻譯記錄時使用
                ImageContext.setCurrentImageUrl(imageUrl);
            } catch (Exception e) {
                log.error("圖片處理失敗: {}", e.getMessage(), e);
                return "圖片處理失敗: " + e.getMessage();
            }

            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                return "未能識別到圖片中的文字。請確保圖片中包含清晰可見的文字。";
            }

            log.info("識別到的文字: {}", recognizedText);

            // 檢測文字語言
            String sourceLanguage = languageDetectionService.detectLanguage(recognizedText);

            // 確定目標語言
            String targetLanguage;
            if (userProfile.getPreferredLanguage() != null && !userProfile.getPreferredLanguage().isEmpty()) {
                // 如果用戶偏好與源語言相同，使用默認規則
                if (sourceLanguage.equals(userProfile.getPreferredLanguage()) ||
                        (sourceLanguage.startsWith("zh") && userProfile.getPreferredLanguage().startsWith("zh"))) {
                    targetLanguage = getDefaultTargetLanguage(sourceLanguage, userProfile);
                } else {
                    targetLanguage = userProfile.getPreferredLanguage();
                }
            } else {
                targetLanguage = getDefaultTargetLanguage(sourceLanguage, userProfile);
            }

            // 選擇 AI 服務
            AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

            // 翻譯文字
            String translatedText = translationService.translateWithService(aiService, recognizedText, targetLanguage);

            // 計算處理時間
            long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

            // 獲取圖片 URL
            String storedImageUrl = ImageContext.getCurrentImageUrl();
            
            // 保存翻譯記錄
            saveTranslationRecord(userId, recognizedText, sourceLanguage, targetLanguage,
                    translatedText, aiService.getProviderName(), aiService.getModelName(),
                    processingTimeMs, true, storedImageUrl);
            
            // 清除圖片上下文
            ImageContext.clear();

            // 更新用戶資料
            updateUserProfileAfterImageTranslation(userProfile);

            // 構建響應消息
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("【圖片文字辨識結果】\n\n");
            resultBuilder.append("識別的文字：\n").append(recognizedText).append("\n\n");
            resultBuilder.append("翻譯結果：\n").append(translatedText);
            
            // 添加偵測到的語言資訊和翻譯目標語言
            String sourceLanguageName = com.linetranslate.bot.util.LanguageUtils.toChineseName(sourceLanguage);
            String targetLanguageName = com.linetranslate.bot.util.LanguageUtils.toChineseName(targetLanguage);
            resultBuilder.append("\n\n[偵測到: ").append(sourceLanguageName)
                      .append(" | 翻譯成: ").append(targetLanguageName).append("]");

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("圖片翻譯失敗: {}", e.getMessage(), e);
            return "圖片翻譯處理失敗: " + e.getMessage();
        }
    }

    /**
     * 根據源語言和用戶資料選擇默認的目標語言
     */
    private String getDefaultTargetLanguage(String sourceLanguage, UserProfile userProfile) {
        log.info("源語言: {}, 檢查是否為中文", sourceLanguage);
        // 檢查源語言是否為中文（包括 zh, zh-CN, zh-TW 等）
        boolean isChinese = sourceLanguage != null && sourceLanguage.startsWith("zh");
        
        String targetLanguage;
        if (isChinese) {
            // 如果是中文，先檢查用戶是否設置了偏好的中文翻譯目標語言
            String preferredChineseTargetLanguage = userProfile.getPreferredChineseTargetLanguage();
            if (preferredChineseTargetLanguage != null && !preferredChineseTargetLanguage.isEmpty()) {
                targetLanguage = preferredChineseTargetLanguage;
                log.info("使用用戶偏好的中文翻譯目標語言: {}", targetLanguage);
            } else {
                targetLanguage = appConfig.getDefaultTargetLanguageForChinese();
                log.info("使用系統預設的中文翻譯目標語言: {}", targetLanguage);
            }
        } else {
            // 如果不是中文，使用系統預設的目標語言
            targetLanguage = appConfig.getDefaultTargetLanguageForOthers();
            log.info("源語言不是中文，使用系統預設的目標語言: {}", targetLanguage);
        }
        
        return targetLanguage;
    }

    /**
     * 確保用戶資料存在
     */
    private UserProfile ensureUserProfileExists(String userId) {
        Optional<UserProfile> userProfileOptional = userProfileRepository.findByUserId(userId);

        if (userProfileOptional.isPresent()) {
            UserProfile userProfile = userProfileOptional.get();
            userProfile.setLastInteractionAt(LocalDateTime.now());
            return userProfileRepository.save(userProfile);
        } else {
            UserProfile newUserProfile = UserProfile.builder()
                    .userId(userId)
                    .firstInteractionAt(LocalDateTime.now())
                    .lastInteractionAt(LocalDateTime.now())
                    .build();
            return userProfileRepository.save(newUserProfile);
        }
    }

    /**
     * 保存翻譯記錄
     */
    private void saveTranslationRecord(String userId, String sourceText, String sourceLanguage,
                                       String targetLanguage, String translatedText, String aiProvider,
                                       String modelName, double processingTimeMs, boolean isImageTranslation, String imageUrl) {

        TranslationRecord record = TranslationRecord.builder()
                .userId(userId)
                .sourceText(sourceText)
                .sourceLanguage(sourceLanguage)
                .targetLanguage(targetLanguage)
                .translatedText(translatedText)
                .aiProvider(aiProvider)
                .modelName(modelName)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(processingTimeMs)
                .isImageTranslation(isImageTranslation)
                .imageUrl(imageUrl)
                .build();

        translationRecordRepository.save(record);
        log.info("已保存用戶 {} 的圖片翻譯記錄", userId);
    }

    /**
     * 更新用戶資料
     */
    private void updateUserProfileAfterImageTranslation(UserProfile userProfile) {
        userProfile.setLastInteractionAt(LocalDateTime.now());
        userProfile.setTotalTranslations(userProfile.getTotalTranslations() + 1);
        userProfile.setImageTranslations(userProfile.getImageTranslations() + 1);
        userProfileRepository.save(userProfile);
    }
}