package com.linetranslate.bot.service.ocr;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

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
import com.linetranslate.bot.service.ocr.OcrService.TextBlock;
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

    @Value("${app.ocr.enabled:true}")
    private boolean ocrEnabled;

    @Autowired
    public ImageTranslationService(
            @Autowired(required = false) OcrService ocrService,
            TranslationService translationService,
            LanguageDetectionService languageDetectionService,
            AiServiceFactory aiServiceFactory,
            LineBlobClient lineBlobClient,
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            AppConfig appConfig) {
        this.ocrService = ocrService;
        this.translationService = translationService;
        this.languageDetectionService = languageDetectionService;
        this.aiServiceFactory = aiServiceFactory;
        this.lineBlobClient = lineBlobClient;
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.appConfig = appConfig;
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

            // 獲取圖片內容
            MessageContentResponse response = lineBlobClient.getMessageContent(messageId).get();
            InputStream imageStream = response.getStream();

            // 準備OCR識別文字
            String recognizedText;

            // 如果Google Vision可用，使用它
            if (ocrService != null) {
                recognizedText = ocrService.recognizeText(imageStream);
            } else {
                // 使用AI服務進行圖像識別
                recognizedText = processImageWithAiService(userId, messageId, userProfile);
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
                    targetLanguage = getDefaultTargetLanguage(sourceLanguage);
                } else {
                    targetLanguage = userProfile.getPreferredLanguage();
                }
            } else {
                targetLanguage = getDefaultTargetLanguage(sourceLanguage);
            }

            // 選擇 AI 服務
            AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

            // 翻譯文字
            String translatedText = translationService.translateWithService(aiService, recognizedText, targetLanguage);

            // 計算處理時間
            long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

            // 保存翻譯記錄
            saveTranslationRecord(userId, recognizedText, sourceLanguage, targetLanguage,
                    translatedText, aiService.getProviderName(), aiService.getModelName(),
                    processingTimeMs, true, null);

            // 更新用戶資料
            updateUserProfileAfterImageTranslation(userProfile);

            // 構建響應消息
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("【圖片文字辨識結果】\n\n");
            resultBuilder.append("識別的文字：\n").append(recognizedText).append("\n\n");
            resultBuilder.append("翻譯結果：\n").append(translatedText);

            return resultBuilder.toString();

        } catch (Exception e) {
            log.error("圖片翻譯失敗: {}", e.getMessage(), e);
            return "圖片翻譯處理失敗: " + e.getMessage();
        }
    }

    /**
     * 使用AI服務處理圖片OCR
     */
    private String processImageWithAiService(String userId, String messageId, UserProfile userProfile) {
        try {
            // 重新獲取圖片內容 (因為流只能讀取一次)
            MessageContentResponse response = lineBlobClient.getMessageContent(messageId).get();

            // 將圖片轉換為Base64
            byte[] imageBytes = response.getStream().readAllBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 選擇AI服務
            AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

            // 構建提示詞，請AI服務識別圖片中的文字
            String prompt = "請識別這張圖片中的所有文字，只返回文字內容，不要添加任何其他描述或解釋。";

            // 處理圖片
            String result = aiService.processImage(prompt, "data:image/jpeg;base64," + base64Image);

            return result;
        } catch (Exception e) {
            log.error("AI圖片OCR處理失敗: {}", e.getMessage(), e);
            return "圖片OCR處理失敗，請稍後再試。";
        }
    }

    /**
     * 根據源語言選擇默認的目標語言
     */
    private String getDefaultTargetLanguage(String sourceLanguage) {
        return ("zh".equals(sourceLanguage))
                ? appConfig.getDefaultTargetLanguageForChinese()
                : appConfig.getDefaultTargetLanguageForOthers();
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