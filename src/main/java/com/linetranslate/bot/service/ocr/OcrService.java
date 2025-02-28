package com.linetranslate.bot.service.ocr;

import java.io.InputStream;
import java.util.List;

/**
 * OCR 服務介面，定義圖片文字識別的方法
 */
public interface OcrService {

    /**
     * 識別圖片中的文字
     *
     * @param imageStream 圖片輸入流
     * @return 識別到的文字
     */
    String recognizeText(InputStream imageStream);

    /**
     * 識別圖片中的文字並返回每個文字塊的位置信息
     *
     * @param imageStream 圖片輸入流
     * @return 文字塊列表，包含文字內容和位置信息
     */
    List<TextBlock> recognizeTextWithLocations(InputStream imageStream);

    /**
     * 表示文字塊的類，包含文字內容和位置信息
     */
    class TextBlock {
        private String text;
        private int x;
        private int y;
        private int width;
        private int height;
        private float confidence;

        public TextBlock(String text, int x, int y, int width, int height, float confidence) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        public String getText() {
            return text;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public float getConfidence() {
            return confidence;
        }

        @Override
        public String toString() {
            return "TextBlock{" +
                    "text='" + text + '\'' +
                    ", x=" + x +
                    ", y=" + y +
                    ", width=" + width +
                    ", height=" + height +
                    ", confidence=" + confidence +
                    '}';
        }
    }
}