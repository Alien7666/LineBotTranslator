package com.linetranslate.bot.service.translation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.linetranslate.bot.config.AppConfig;
import com.linetranslate.bot.model.TranslationRecord;
import com.linetranslate.bot.model.UserProfile;
import com.linetranslate.bot.repository.TranslationRecordRepository;
import com.linetranslate.bot.repository.UserProfileRepository;
import com.linetranslate.bot.service.ai.AiService;
import com.linetranslate.bot.service.ai.AiServiceFactory;
import com.linetranslate.bot.util.LanguageUtils;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TranslationService {

    private final LanguageDetectionService languageDetectionService;
    private final AiServiceFactory aiServiceFactory;
    private final TranslationRecordRepository translationRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final AppConfig appConfig;

    // 翻譯指令的正則表達式模式（中文語言名稱）
    private static final Pattern TRANSLATION_COMMAND_PATTERN_CN = Pattern.compile("翻譯成([\\u4e00-\\u9fa5]+)\\s*(.*)");

    // 翻譯指令的正則表達式模式（語言代碼）
    private static final Pattern TRANSLATION_COMMAND_PATTERN_CODE = Pattern.compile("翻譯成([a-zA-Z\\-]+)\\s*(.*)");

    // 多行翻譯指令的正則表達式模式（處理 "xxx\n翻譯成yyy" 的情況）
    private static final Pattern MULTILINE_TRANSLATION_PATTERN_CN = Pattern.compile("(.*?)\\n翻譯成([\\u4e00-\\u9fa5]+)\\s*(.*)");
    private static final Pattern MULTILINE_TRANSLATION_PATTERN_CODE = Pattern.compile("(.*?)\\n翻譯成([a-zA-Z\\-]+)\\s*(.*)");

    @Autowired
    public TranslationService(
            LanguageDetectionService languageDetectionService,
            AiServiceFactory aiServiceFactory,
            TranslationRecordRepository translationRecordRepository,
            UserProfileRepository userProfileRepository,
            AppConfig appConfig) {
        this.languageDetectionService = languageDetectionService;
        this.aiServiceFactory = aiServiceFactory;
        this.translationRecordRepository = translationRecordRepository;
        this.userProfileRepository = userProfileRepository;
        this.appConfig = appConfig;
    }

    /**
     * 處理用戶的翻譯請求
     *
     * @param userId 用戶 ID
     * @param text 用戶輸入的文本
     * @return 翻譯結果
     */
    public String processTranslationRequest(String userId, String text) {
        Instant start = Instant.now();
        log.info("收到用戶 {} 的翻譯請求: {}", userId, text);

        // 檢查用戶是否已存在，如果不存在則創建
        UserProfile userProfile = ensureUserProfileExists(userId);

        // 檢查是否是多行翻譯指令格式 (例如：你好\n翻譯成日文)
        Matcher multilineMatcherCN = MULTILINE_TRANSLATION_PATTERN_CN.matcher(text);
        Matcher multilineMatcherCode = MULTILINE_TRANSLATION_PATTERN_CODE.matcher(text);

        String sourceText;
        String targetLanguage;

        // 處理多行翻譯格式
        if (multilineMatcherCN.find()) {
            sourceText = multilineMatcherCN.group(1).trim();
            String languageName = multilineMatcherCN.group(2);
            targetLanguage = LanguageUtils.toLanguageCode(languageName);
            String additionalText = multilineMatcherCN.group(3).trim();

            // 如果有額外文本，添加到源文本
            if (!additionalText.isEmpty()) {
                sourceText += " " + additionalText;
            }

            if (sourceText.isEmpty()) {
                return "請提供要翻譯成" + languageName + "的文字。";
            }

            log.info("多行格式翻譯，用戶指定翻譯成: {} ({}), 原文: {}", languageName, targetLanguage, sourceText);
        }
        else if (multilineMatcherCode.find()) {
            sourceText = multilineMatcherCode.group(1).trim();
            String languageCode = multilineMatcherCode.group(2);
            targetLanguage = languageCode.toLowerCase();
            String additionalText = multilineMatcherCode.group(3).trim();

            // 如果有額外文本，添加到源文本
            if (!additionalText.isEmpty()) {
                sourceText += " " + additionalText;
            }

            if (sourceText.isEmpty()) {
                return "請提供要翻譯成" + languageCode + "的文字。";
            }

            log.info("多行格式翻譯，用戶指定翻譯成: {} ({}), 原文: {}", languageCode, LanguageUtils.toChineseName(targetLanguage), sourceText);
        }
        // 檢查是否是單行翻譯指令格式 (例如：翻譯成日文 你好)
        else if (text.startsWith("翻譯成")) {
            Matcher matcherCN = TRANSLATION_COMMAND_PATTERN_CN.matcher(text);
            Matcher matcherCode = TRANSLATION_COMMAND_PATTERN_CODE.matcher(text);

            if (matcherCN.find()) {
                // 使用用戶指定的中文語言名稱
                String languageName = matcherCN.group(1);
                targetLanguage = LanguageUtils.toLanguageCode(languageName);
                sourceText = matcherCN.group(2).trim();

                if (sourceText.isEmpty()) {
                    return "請在「翻譯成" + languageName + "」後面輸入要翻譯的文字。";
                }

                log.info("用戶指定翻譯成: {} ({}), 原文: {}", languageName, targetLanguage, sourceText);
            } else if (matcherCode.find()) {
                // 使用用戶指定的語言代碼
                String languageCode = matcherCode.group(1);
                targetLanguage = languageCode.toLowerCase();
                sourceText = matcherCode.group(2).trim();

                if (sourceText.isEmpty()) {
                    return "請在「翻譯成" + languageCode + "」後面輸入要翻譯的文字。";
                }

                log.info("用戶指定翻譯成: {} ({}), 原文: {}", languageCode, LanguageUtils.toChineseName(targetLanguage), sourceText);
            } else {
                // 如果格式不正確，使用默認翻譯處理
                return handleDefaultTranslation(userId, text, userProfile, start);
            }
        } else {
            // 使用默認翻譯處理
            return handleDefaultTranslation(userId, text, userProfile, start);
        }

        // 進行實際的翻譯處理
        return performTranslation(userId, userProfile, sourceText, targetLanguage, start);
    }

    /**
     * 處理默認的翻譯情況（無指定目標語言）
     */
    private String handleDefaultTranslation(String userId, String text, UserProfile userProfile, Instant start) {
        // 使用自動檢測語言並選擇目標語言
        String sourceText = text;
        String sourceLanguage = languageDetectionService.detectLanguage(sourceText);
        String targetLanguage;

        // 決定目標語言
        if (userProfile.getPreferredLanguage() != null && !userProfile.getPreferredLanguage().isEmpty()) {
            // 如果用戶有偏好設置，但輸入的語言與偏好語言相同，則使用默認規則
            if (sourceLanguage.equals(userProfile.getPreferredLanguage()) ||
                    (sourceLanguage.startsWith("zh") && userProfile.getPreferredLanguage().startsWith("zh"))) {
                targetLanguage = getDefaultTargetLanguage(sourceLanguage);
            } else {
                // 使用用戶的偏好語言
                targetLanguage = userProfile.getPreferredLanguage();
            }
        } else {
            // 如果沒有偏好，使用默認規則
            targetLanguage = getDefaultTargetLanguage(sourceLanguage);
        }

        log.info("自動檢測語言: {}, 目標語言: {}", sourceLanguage, targetLanguage);

        return performTranslation(userId, userProfile, sourceText, targetLanguage, start);
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
     * 執行翻譯並處理相關記錄
     */
    private String performTranslation(String userId, UserProfile userProfile, String sourceText, String targetLanguage, Instant start) {
        // 選擇 AI 服務
        AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

        // 執行翻譯
        String translatedText = translateWithService(aiService, sourceText, targetLanguage);

        // 計算處理時間
        long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

        // 保存翻譯記錄
        saveTranslationRecord(userId, sourceText, languageDetectionService.detectLanguage(sourceText),
                targetLanguage, translatedText, aiService.getProviderName(),
                aiService.getModelName(), processingTimeMs, false, null);

        // 更新用戶資料
        updateUserProfileAfterTranslation(userProfile, translatedText, targetLanguage);

        return translatedText;
    }

    /**
     * 快速翻譯到指定語言
     *
     * @param userId 用戶 ID
     * @param text 要翻譯的文本
     * @param targetLanguageCode 目標語言代碼
     * @return 翻譯結果
     */
    public String quickTranslate(String userId, String text, String targetLanguageCode) {
        if (!LanguageUtils.isSupported(targetLanguageCode)) {
            return "不支持的語言代碼：" + targetLanguageCode;
        }

        Instant start = Instant.now();
        UserProfile userProfile = ensureUserProfileExists(userId);

        String standardLanguageCode = LanguageUtils.toLanguageCode(targetLanguageCode);
        log.info("快速翻譯請求: 用戶 {}, 目標語言: {}, 文本長度: {}", userId, standardLanguageCode, text.length());

        // 選擇 AI 服務
        AiService aiService = aiServiceFactory.getService(userProfile.getPreferredAiProvider());

        // 執行翻譯
        String translatedText = translateWithService(aiService, text, standardLanguageCode);

        // 計算處理時間
        long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

        // 保存翻譯記錄
        saveTranslationRecord(userId, text, languageDetectionService.detectLanguage(text),
                standardLanguageCode, translatedText, aiService.getProviderName(),
                aiService.getModelName(), processingTimeMs, false, null);

        // 更新用戶資料
        updateUserProfileAfterTranslation(userProfile, translatedText, standardLanguageCode);

        return translatedText;
    }

    /**
     * 處理多行文本翻譯
     *
     * @param userId 用戶 ID
     * @param text 多行文本
     * @return 翻譯結果
     */
    public String processBatchTranslation(String userId, String text) {
        if (text == null || text.trim().isEmpty()) {
            return "請提供要翻譯的文本。";
        }

        // 檢查文本是否包含多行
        String[] lines = text.split("\n");
        if (lines.length <= 1) {
            // 如果只有一行，直接使用標準翻譯處理
            return processTranslationRequest(userId, text);
        }

        Instant start = Instant.now();
        UserProfile userProfile = ensureUserProfileExists(userId);

        // 檢測原文語言
        String sourceLanguage = languageDetectionService.detectLanguage(text);

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

        // 執行翻譯
        String translatedText = translateWithService(aiService, text, targetLanguage);

        // 計算處理時間
        long processingTimeMs = Duration.between(start, Instant.now()).toMillis();

        // 保存翻譯記錄
        saveTranslationRecord(userId, text, sourceLanguage, targetLanguage,
                translatedText, aiService.getProviderName(),
                aiService.getModelName(), processingTimeMs, false, null);

        // 更新用戶資料
        updateUserProfileAfterTranslation(userProfile, translatedText, targetLanguage);

        return translatedText;
    }

    /**
     * 使用指定的 AI 服務進行翻譯
     *
     * @param aiService AI 服務
     * @param text 要翻譯的文本
     * @param targetLanguage 目標語言
     * @return 翻譯結果
     */
    @Cacheable(value = "translations", key = "{#text, #targetLanguage, #aiService.providerName}")
    public String translateWithService(AiService aiService, String text, String targetLanguage) {
        log.info("使用 {} 翻譯成 {}", aiService.getProviderName(), targetLanguage);
        return aiService.translateText(text, targetLanguage);
    }

    /**
     * 確保用戶資料存在
     *
     * @param userId 用戶 ID
     * @return 用戶資料
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
        log.info("已保存用戶 {} 的翻譯記錄", userId);
    }

    /**
     * 更新用戶資料
     */
    private void updateUserProfileAfterTranslation(UserProfile userProfile, String translatedText, String targetLanguage) {
        userProfile.setLastInteractionAt(LocalDateTime.now());
        userProfile.setTotalTranslations(userProfile.getTotalTranslations() + 1);
        userProfile.setTextTranslations(userProfile.getTextTranslations() + 1);

        // 更新最近的翻譯
        userProfile.getRecentTranslations().add(0, translatedText);
        if (userProfile.getRecentTranslations().size() > 5) {
            userProfile.getRecentTranslations().remove(5);
        }

        // 更新最近使用的語言
        userProfile.addRecentLanguage(targetLanguage);

        userProfileRepository.save(userProfile);
    }

    /**
     * 設置用戶偏好的 AI 提供者
     *
     * @param userId 用戶 ID
     * @param provider AI 提供者
     * @return 更新結果信息
     */
    public String setPreferredProvider(String userId, String provider) {
        if (!provider.equals("openai") && !provider.equals("gemini")) {
            return "不支持的 AI 提供者。請選擇 'openai' 或 'gemini'。";
        }

        UserProfile userProfile = ensureUserProfileExists(userId);
        userProfile.setPreferredAiProvider(provider);
        userProfileRepository.save(userProfile);

        return "已將您的偏好 AI 設置為 " + provider;
    }

    /**
     * 設置用戶偏好的語言
     *
     * @param userId 用戶 ID
     * @param language 語言代碼或名稱
     * @return 更新結果信息
     */
    public String setPreferredLanguage(String userId, String language) {
        String languageCode = LanguageUtils.toLanguageCode(language);

        if (!LanguageUtils.isSupported(languageCode)) {
            return "不支持的語言：" + language +
                    "。請使用支援的語言代碼（如 en、ja、ko）或語言名稱（如 英文、日文、韓文）。";
        }

        UserProfile userProfile = ensureUserProfileExists(userId);
        userProfile.setPreferredLanguage(languageCode);
        userProfileRepository.save(userProfile);

        String languageName = LanguageUtils.toChineseName(languageCode);
        return "已將您的偏好語言設置為 " + languageName + " (" + languageCode + ")";
    }

    /**
     * 獲取用戶最近使用的語言列表
     *
     * @param userId 用戶 ID
     * @return 最近使用的語言列表
     */
    public List<String> getRecentLanguages(String userId) {
        UserProfile userProfile = ensureUserProfileExists(userId);
        return userProfile.getRecentLanguagesList();
    }
}