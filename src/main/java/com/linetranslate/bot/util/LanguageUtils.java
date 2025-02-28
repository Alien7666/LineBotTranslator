package com.linetranslate.bot.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 語言工具類，用於語言代碼和名稱的轉換和映射
 */
public class LanguageUtils {

    // 語言名稱到語言代碼的映射
    private static final Map<String, String> LANGUAGE_NAME_TO_CODE = new HashMap<>();

    // 語言代碼到正式名稱的映射
    private static final Map<String, String> LANGUAGE_CODE_TO_NAME = new HashMap<>();

    static {
        // 初始化常見語言的映射
        addLanguageMapping("中文", "zh", "Chinese");
        addLanguageMapping("英文", "en", "English");
        addLanguageMapping("日文", "ja", "Japanese");
        addLanguageMapping("韓文", "ko", "Korean");
        addLanguageMapping("法文", "fr", "French");
        addLanguageMapping("德文", "de", "German");
        addLanguageMapping("西班牙文", "es", "Spanish");
        addLanguageMapping("葡萄牙文", "pt", "Portuguese");
        addLanguageMapping("義大利文", "it", "Italian");
        addLanguageMapping("俄文", "ru", "Russian");
        addLanguageMapping("阿拉伯文", "ar", "Arabic");
        addLanguageMapping("泰文", "th", "Thai");
        addLanguageMapping("越南文", "vi", "Vietnamese");
        addLanguageMapping("印尼文", "id", "Indonesian");
        addLanguageMapping("馬來文", "ms", "Malay");
        addLanguageMapping("繁體中文", "zh-TW", "Traditional Chinese");
        addLanguageMapping("簡體中文", "zh-CN", "Simplified Chinese");
        addLanguageMapping("荷蘭文", "nl", "Dutch");
        addLanguageMapping("希臘文", "el", "Greek");
        addLanguageMapping("波蘭文", "pl", "Polish");
        addLanguageMapping("土耳其文", "tr", "Turkish");
        addLanguageMapping("捷克文", "cs", "Czech");
        addLanguageMapping("瑞典文", "sv", "Swedish");
        addLanguageMapping("丹麥文", "da", "Danish");
        addLanguageMapping("芬蘭文", "fi", "Finnish");
        addLanguageMapping("印地文", "hi", "Hindi");

        // 添加別名
        LANGUAGE_NAME_TO_CODE.put("英語", "en");
        LANGUAGE_NAME_TO_CODE.put("日語", "ja");
        LANGUAGE_NAME_TO_CODE.put("韓語", "ko");
        LANGUAGE_NAME_TO_CODE.put("法語", "fr");
        LANGUAGE_NAME_TO_CODE.put("德語", "de");
        LANGUAGE_NAME_TO_CODE.put("西班牙語", "es");
        LANGUAGE_NAME_TO_CODE.put("葡萄牙語", "pt");
        LANGUAGE_NAME_TO_CODE.put("義大利語", "it");
        LANGUAGE_NAME_TO_CODE.put("俄語", "ru");
        LANGUAGE_NAME_TO_CODE.put("阿拉伯語", "ar");
        LANGUAGE_NAME_TO_CODE.put("泰語", "th");
        LANGUAGE_NAME_TO_CODE.put("越南語", "vi");
        LANGUAGE_NAME_TO_CODE.put("印尼語", "id");
        LANGUAGE_NAME_TO_CODE.put("馬來語", "ms");
        LANGUAGE_NAME_TO_CODE.put("荷蘭語", "nl");
        LANGUAGE_NAME_TO_CODE.put("希臘語", "el");
        LANGUAGE_NAME_TO_CODE.put("波蘭語", "pl");
        LANGUAGE_NAME_TO_CODE.put("土耳其語", "tr");
        LANGUAGE_NAME_TO_CODE.put("捷克語", "cs");
        LANGUAGE_NAME_TO_CODE.put("瑞典語", "sv");
        LANGUAGE_NAME_TO_CODE.put("丹麥語", "da");
        LANGUAGE_NAME_TO_CODE.put("芬蘭語", "fi");
        LANGUAGE_NAME_TO_CODE.put("印地語", "hi");
        LANGUAGE_NAME_TO_CODE.put("jp", "ja");
        LANGUAGE_NAME_TO_CODE.put("jap", "ja");
        LANGUAGE_NAME_TO_CODE.put("japan", "ja");
        LANGUAGE_NAME_TO_CODE.put("cn", "zh-CN");
        LANGUAGE_NAME_TO_CODE.put("tw", "zh-TW");
        LANGUAGE_NAME_TO_CODE.put("kr", "ko");

    }

    /**
     * 添加語言映射關係
     *
     * @param chineseName 中文名稱
     * @param code 語言代碼
     * @param englishName 英文名稱
     */
    private static void addLanguageMapping(String chineseName, String code, String englishName) {
        LANGUAGE_NAME_TO_CODE.put(chineseName, code);
        LANGUAGE_CODE_TO_NAME.put(code, chineseName);
    }

    /**
     * 將語言名稱或代碼轉換為標準語言代碼
     *
     * @param languageNameOrCode 語言名稱或代碼
     * @return 標準語言代碼，如果無法識別則返回原始輸入
     */
    public static String toLanguageCode(String languageNameOrCode) {
        if (languageNameOrCode == null || languageNameOrCode.trim().isEmpty()) {
            return "unknown";
        }

        String normalizedInput = languageNameOrCode.trim().toLowerCase();

        // 如果輸入的是標準語言代碼
        if (LANGUAGE_CODE_TO_NAME.containsKey(normalizedInput)) {
            return normalizedInput;
        }

        // 嘗試從大小寫不敏感的映射中找到對應的語言代碼
        for (Map.Entry<String, String> entry : LANGUAGE_NAME_TO_CODE.entrySet()) {
            if (entry.getKey().toLowerCase().equals(normalizedInput)) {
                return entry.getValue();
            }
        }

        // 如果無法識別，則返回原始輸入
        return languageNameOrCode;
    }

    /**
     * 將語言代碼轉換為中文名稱
     *
     * @param languageCode 語言代碼
     * @return 中文名稱，如果無法識別則返回語言代碼
     */
    public static String toChineseName(String languageCode) {
        if (languageCode == null || languageCode.trim().isEmpty()) {
            return "未知語言";
        }

        String normalizedCode = languageCode.trim().toLowerCase();
        return LANGUAGE_CODE_TO_NAME.getOrDefault(normalizedCode, languageCode);
    }

    /**
     * 檢查是否支持該語言
     *
     * @param languageNameOrCode 語言名稱或代碼
     * @return 是否支持
     */
    public static boolean isSupported(String languageNameOrCode) {
        if (languageNameOrCode == null || languageNameOrCode.trim().isEmpty()) {
            return false;
        }

        String normalizedInput = languageNameOrCode.trim().toLowerCase();

        // 檢查代碼是否存在
        if (LANGUAGE_CODE_TO_NAME.containsKey(normalizedInput)) {
            return true;
        }

        // 檢查名稱是否存在
        for (String name : LANGUAGE_NAME_TO_CODE.keySet()) {
            if (name.toLowerCase().equals(normalizedInput)) {
                return true;
            }
        }

        return false;
    }
}