package com.linetranslate.bot.service.translation;

import com.optimaize.langdetect.LanguageDetector;
import com.optimaize.langdetect.LanguageDetectorBuilder;
import com.optimaize.langdetect.i18n.LdLocale;
import com.optimaize.langdetect.ngram.NgramExtractors;
import com.optimaize.langdetect.profiles.LanguageProfile;
import com.optimaize.langdetect.profiles.LanguageProfileReader;
import com.optimaize.langdetect.text.CommonTextObjectFactories;
import com.optimaize.langdetect.text.TextObject;
import com.optimaize.langdetect.text.TextObjectFactory;

import jakarta.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class LanguageDetectionService {

    private LanguageDetector languageDetector;
    private TextObjectFactory textObjectFactory;

    @PostConstruct
    public void init() {
        try {
            // 加載預訓練的語言檔案
            List<LanguageProfile> languageProfiles = new LanguageProfileReader().readAllBuiltIn();

            // 建立語言檢測器
            languageDetector = LanguageDetectorBuilder.create(NgramExtractors.standard())
                    .withProfiles(languageProfiles)
                    .build();

            // 建立文本工廠
            textObjectFactory = CommonTextObjectFactories.forDetectingOnLargeText();

            log.info("語言檢測服務初始化成功");
        } catch (IOException e) {
            log.error("語言檢測服務初始化失敗: {}", e.getMessage());
        }
    }

    /**
     * 檢測文本的語言
     *
     * @param text 要檢測的文本
     * @return 檢測到的語言代碼，如果無法檢測則返回 "unknown"
     */
    public String detectLanguage(String text) {
        if (StringUtils.isBlank(text)) {
            return "unknown";
        }

        try {
            // 先檢查是否包含中文字符
            if (containsChineseCharacters(text)) {
                return "zh";
            }

            TextObject textObject = textObjectFactory.forText(text);
            String detectedLanguage = languageDetector.detect(textObject)
                    .or(LdLocale.fromString("en"))
                    .getLanguage();

            // 處理各種中文變體
            if (detectedLanguage.startsWith("zh")) {
                return "zh";
            }

            return detectedLanguage;
        } catch (Exception e) {
            log.error("語言檢測失敗: {}", e.getMessage());
            return "unknown";
        }
    }

    // 檢測中文字符的方法
    private boolean containsChineseCharacters(String text) {
        return text.codePoints().anyMatch(codepoint ->
                Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HAN);
    }

    /**
     * 確定給定文本應該翻譯成哪種語言
     *
     * @param text 要翻譯的文本
     * @param defaultTargetLanguageForChinese 中文的預設目標語言
     * @param defaultTargetLanguageForOthers 其他語言的預設目標語言
     * @return 目標語言代碼
     */
    public String determineTargetLanguage(String text, String defaultTargetLanguageForChinese, String defaultTargetLanguageForOthers) {
        String sourceLanguage = detectLanguage(text);

        if ("zh".equals(sourceLanguage)) {
            return defaultTargetLanguageForChinese;
        } else {
            return defaultTargetLanguageForOthers;
        }
    }
}