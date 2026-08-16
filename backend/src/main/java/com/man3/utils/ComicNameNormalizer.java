package com.man3.utils;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import net.sourceforge.pinyin4j.PinyinHelper;

import java.text.Normalizer;
import java.util.Locale;

/** Creates a stable comparison key for comic names across Chinese writing systems. */
public final class ComicNameNormalizer {

    private ComicNameNormalizer() {
    }

    /** Converts traditional/variant Chinese to simplified Chinese, then to pinyin. */
    public static String toPinyin(String value) {
        if (value == null || value.trim().isEmpty()) return "";

        String simplified = ZhConverterUtil.toSimple(value);
        String normalized = Normalizer.normalize(simplified, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length() * 2);
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            String[] pinyin = PinyinHelper.toHanyuPinyinStringArray(ch);
            if (pinyin != null && pinyin.length > 0) {
                result.append(pinyin[0].replaceAll("[1-5]$", ""));
            } else if (Character.isLetterOrDigit(ch)) {
                result.append(ch);
            }
        }
        return result.toString();
    }
}
