package com.app.mycity.util;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.app.mycity.R;

public final class IssueCategory {

    public static final String INFRASTRUCTURE = "INFRASTRUCTURE";
    public static final String LANDSCAPING = "LANDSCAPING";
    public static final String TRANSPORT = "TRANSPORT";
    public static final String SAFETY = "SAFETY";
    public static final String LOST_FOUND = "LOST_FOUND";
    public static final String OTHER = "OTHER";

    public static final String[] ALL = {
            INFRASTRUCTURE, LANDSCAPING, TRANSPORT, SAFETY, LOST_FOUND, OTHER
    };

    private IssueCategory() { }

    public static String normalize(@Nullable String key) {
        if (key == null) return OTHER;
        for (String c : ALL) if (c.equals(key)) return c;
        return OTHER;
    }

    public static String label(@Nullable String key) {
        if (key == null) return "Без категории";
        switch (key) {
            case INFRASTRUCTURE: return "Инфраструктура";
            case LANDSCAPING:    return "Благоустройство";
            case TRANSPORT:      return "Транспорт";
            case SAFETY:         return "Безопасность";
            case LOST_FOUND:     return "Потеряно/Найдено";
            case OTHER:          return "Прочее";
            default:             return "Без категории";
        }
    }

    public static String emoji(@Nullable String key) {
        if (key == null) return "";
        switch (key) {
            case INFRASTRUCTURE: return "🏗️"; // 🏗️
            case LANDSCAPING:    return "🌳";       // 🌳
            case TRANSPORT:      return "🚌";       // 🚌
            case SAFETY:         return "🛡️"; // 🛡️
            case LOST_FOUND:     return "🔍";       // 🔍
            case OTHER:          return "📌";       // 📌
            default:             return "";
        }
    }

    public static String labelWithEmoji(@Nullable String key) {
        String e = emoji(key);
        String l = label(key);
        return e.isEmpty() ? l : e + " " + l;
    }

    @ColorRes
    public static int colorRes(@Nullable String key) {
        if (key == null) return R.color.category_other;
        switch (key) {
            case INFRASTRUCTURE: return R.color.category_infrastructure;
            case LANDSCAPING:    return R.color.category_landscaping;
            case TRANSPORT:      return R.color.category_transport;
            case SAFETY:         return R.color.category_safety;
            case LOST_FOUND:     return R.color.category_lost_found;
            case OTHER:          return R.color.category_other;
            default:             return R.color.category_other;
        }
    }
}
