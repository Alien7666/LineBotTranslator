package com.linetranslate.bot.service.ocr;

/**
 * 用於在處理圖片翻譯過程中傳遞圖片 URL 的上下文類
 */
public class ImageContext {
    
    private static final ThreadLocal<String> currentImageUrl = new ThreadLocal<>();
    
    /**
     * 設置當前處理的圖片 URL
     * 
     * @param imageUrl 圖片 URL
     */
    public static void setCurrentImageUrl(String imageUrl) {
        currentImageUrl.set(imageUrl);
    }
    
    /**
     * 獲取當前處理的圖片 URL
     * 
     * @return 圖片 URL，如果沒有則返回 null
     */
    public static String getCurrentImageUrl() {
        return currentImageUrl.get();
    }
    
    /**
     * 清除當前處理的圖片 URL
     */
    public static void clear() {
        currentImageUrl.remove();
    }
}
