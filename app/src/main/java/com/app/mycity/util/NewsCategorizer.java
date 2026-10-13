package com.app.mycity.util;

import com.app.mycity.data.model.NewsItem;

import java.util.Locale;

public final class NewsCategorizer {

    private static final String[] DANGEROUS = new String[] {
            "пожар", "пожара", "чп", "дтп", "опасн", "погиб",
            "взрыв", "авари", "ранен", "трагед", "несчаст"
    };

    private static final String[] IMPORTANT = new String[] {
            "власт", "губернатор", "указ", "закон", "выборы",
            "правительств", "лукашенко", "облисполком", "горисполком",
            "минист", "президент"
    };

    private NewsCategorizer() { }

    public static String classify(String title, String body) {
        String t = title == null ? "" : title;
        String b = body == null ? "" : body;
        String haystack = (t + " " + b).toLowerCase(Locale.ROOT);

        for (String kw : DANGEROUS) {
            if (haystack.contains(kw)) {
                return NewsItem.CATEGORY_DANGEROUS;
            }
        }
        for (String kw : IMPORTANT) {
            if (haystack.contains(kw)) {
                return NewsItem.CATEGORY_IMPORTANT;
            }
        }
        return NewsItem.CATEGORY_INTERESTING;
    }
}
