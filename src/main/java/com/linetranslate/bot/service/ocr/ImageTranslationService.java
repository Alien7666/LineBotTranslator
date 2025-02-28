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
            OcrService ocrService,
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

        // 檢查 OCR 服務是否可用
        if (ocrService == null) {
            log.warn("OCR 服務未正確初始化，圖片翻譯功能將不可用");
            ocrEnabled = false;
        }
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

            // 使用 OCR 識別圖片中的文字
            String recognizedText = ocrService.recognizeText(imageStream);

            if (recognizedText == null || recognizedText.trim().isEmpty()) {
                return "未能識別到圖片中的文字。請確保圖片中包含清晰可見的文字。";
            }

            log.info("識別到的文字: {}", recognizedText);

            // 檢測文字語言
            String sourceLanguage = languageDetectionService.detectLanguage(recognizedText);

            // 確定目標語言
            String targetLanguage;
            if (userProfile.getPreferredLanguage() != null && !userProfile.getPreferredLanguage().isEmpty()) {
                targetLanguage = userProfile.getPreferredLanguage();
            } else {
                targetLanguage = ("zh".equals(sourceLanguage))
                        ? appConfig.getDefaultTargetLanguageForChinese()
                        : appConfig.getDefaultTargetLanguageForOthers();
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
     * 處理圖片翻譯並替換原圖中的文字
     *
     * @param userId 用戶 ID
     * @param messageId 圖片消息 ID
     * @return 翻譯結果
     */
    public byte[] processImageTranslationWithOverlay(String userId, String messageId, String targetLanguage) {
        if (!ocrEnabled) {
            return null;
        }

        Instant start = Instant.now();
        log.info("處理用戶 {} 的圖片翻譯替換請求, 圖片 ID: {}", userId, messageId);

        try {
            // 獲取用戶資料
            UserProfile userProfile = ensureUserProfileExists(userId);

            // 獲取圖片內容
            MessageContentResponse response = lineBlobClient.getMessageContent(messageId).get();

            // 讀取圖片
            BufferedImage originalImage = ImageIO.read(response.getStream());

            // 再次獲取圖片內容用於 OCR
            response = lineBlobClient.getMessageContent(messageId).get();

            // 使用 OCR 識別圖片中的文字和位置
            List<TextBlock> textBlocks = ocrService.recognizeTextWithLocations(response.getStream());

            if (textBlocks.isEmpty()) {
                return null; // 沒有識別到文字，返回 null
            }

            // 將所有文字塊組合成一個文本進行翻譯
            Map<String, String> translationMap = new HashMap<>();
            StringBuilder allText = new StringBuilder();

            for (TextBlock block : textBlocks) {
                if (allText.length() > 0) {
                    allText.append("\n");
                }
                allText.append(block.getText());
            }

            // 檢測文字語言
            String sourceLanguage = languageDetectionService.detectLanguage(allText.toString());

            // 確定目標語言
            if (targetLanguage == null || targetLanguage.isEmpty()) {
                if (userProfile.getPreferredLanguage() != null && !userProfile.getPreferredLanguage().isEmpty()) {
                    targetLanguage = userProfile.getPreferredLanguage();
                } else {
                    targetLanguage = ("zh".equals(sourceLanguage))
                            ? appConfig.getDefaultTargetLanguageForChinese()
                            : appConfig.getDefaultTargetLanguageForOthers();
                }
            }

            // 選擇 AI 服務
            AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

            // 翻譯文字
            for (TextBlock block : textBlocks) {
                String originalText = block.getText();
                String translatedText = translationService.translateWithService(aiService, originalText, targetLanguage);
                translationMap.put(originalText, translatedText);
            }

            // 創建新圖片，覆蓋原始文字並繪製翻譯後的文字
            BufferedImage resultImage = new BufferedImage(
                    originalImage.getWidth(),
                    originalImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);

            // 繪製原始圖片
            Graphics2D g2d = resultImage.createGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            // 繪製翻譯後的文字
            for (TextBlock block : textBlocks) {
                String translatedText = translationMap.get(block.getText());

                // 繪製白色背景覆蓋原始文字
                g2d.setColor(Color.WHITE);
                g2d.fill(new Rectangle(block.getX(), block.getY(), block.getWidth(), block.getHeight()));

                // 繪製翻譯後的文字
                g2d.setColor(Color.BLACK);
                g2d.setFont(new Font("SansSerif", Font.PLAIN, Math.max(12, block.getHeight() / 2)));
                g2d.drawString(translatedText, block.getX(), block.getY() + block.getHeight() / 2);
            }

            g2d.dispose();

            // 將圖片轉為字節數組
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(resultImage, "jpg", outputStream);
            byte[] imageData = outputStream.toByteArray();

            // 計算處理時間
            long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

            // 保存翻譯記錄
            saveTranslationRecord(userId, allText.toString(), sourceLanguage, targetLanguage,
                    String.join("\n", translationMap.values()), aiService.getProviderName(), aiService.getModelName(),
                    processingTimeMs, true, null);

            // 更新用戶資料
            updateUserProfileAfterImageTranslation(userProfile);

            return imageData;

        } catch (Exception e) {
            log.error("圖片文字替換失敗: {}", e.getMessage(), e);
            return null;
        }
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