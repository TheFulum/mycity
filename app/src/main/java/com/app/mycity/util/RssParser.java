package com.app.mycity.util;

import android.text.Html;
import android.util.Log;
import android.util.Xml;

import com.app.mycity.data.model.NewsItem;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class RssParser {

    private static final String TAG = "RssParser";
    private static final String RFC822 = "EEE, dd MMM yyyy HH:mm:ss Z";
    private static final String RFC822_ALT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private RssParser() { }

    public static List<NewsItem> parse(InputStream input, String sourceName) throws IOException, XmlPullParserException {
        List<NewsItem> results = new ArrayList<>();
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(input, "UTF-8");

        int event = parser.getEventType();
        boolean insideItem = false;
        NewsItem current = null;
        StringBuilder text = new StringBuilder();
        String currentTag = null;
        String pendingImage = null;

        Date now = new Date();

        while (event != XmlPullParser.END_DOCUMENT) {
            switch (event) {
                case XmlPullParser.START_TAG: {
                    String name = parser.getName();
                    if (name == null) break;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if ("item".equals(lower)) {
                        insideItem = true;
                        current = new NewsItem();
                        current.setSourceType(NewsItem.SOURCE_TYPE_RSS);
                        current.setSourceName(sourceName);
                        current.setFetchedAt(now);
                        current.setLat(null);
                        current.setLng(null);
                        current.setAddress(null);
                        pendingImage = null;
                    } else if (insideItem && current != null) {
                        currentTag = lower;
                        text.setLength(0);
                        if ("enclosure".equals(lower)) {
                            String url = parser.getAttributeValue(null, "url");
                            String type = parser.getAttributeValue(null, "type");
                            if (url != null && (type == null || type.startsWith("image"))) {
                                if (current.getImageUrl() == null || current.getImageUrl().isEmpty()) {
                                    current.setImageUrl(url);
                                }
                            }
                        } else if ("media:content".equals(lower) || "media:thumbnail".equals(lower)
                                || lower.endsWith(":content") || lower.endsWith(":thumbnail")) {
                            String url = parser.getAttributeValue(null, "url");
                            if (url != null && (current.getImageUrl() == null || current.getImageUrl().isEmpty())) {
                                current.setImageUrl(url);
                            }
                        }
                    }
                    break;
                }
                case XmlPullParser.TEXT: {
                    if (insideItem && currentTag != null) {
                        text.append(parser.getText());
                    }
                    break;
                }
                case XmlPullParser.END_TAG: {
                    String name = parser.getName();
                    if (name == null) break;
                    String lower = name.toLowerCase(Locale.ROOT);
                    if ("item".equals(lower)) {
                        if (current != null) {
                            try {
                                finalizeItem(current, results);
                            } catch (Exception ex) {
                                Log.w(TAG, "Skipping malformed item: " + ex.getMessage());
                            }
                        }
                        insideItem = false;
                        current = null;
                        currentTag = null;
                        text.setLength(0);
                    } else if (insideItem && current != null && currentTag != null) {
                        String value = text.toString().trim();
                        try {
                            applyField(current, currentTag, value);
                        } catch (Exception ex) {
                            Log.w(TAG, "Field parse error on <" + currentTag + ">: " + ex.getMessage());
                        }
                        currentTag = null;
                        text.setLength(0);
                    }
                    break;
                }
                default:
                    break;
            }
            event = parser.next();
        }

        return results;
    }

    private static void applyField(NewsItem item, String tag, String value) {
        if (value == null || value.isEmpty()) return;
        switch (tag) {
            case "title":
                item.setTitle(value);
                break;
            case "link":
                item.setSourceUrl(value);
                break;
            case "description":
            case "content:encoded":
                String stripped = stripHtml(value);
                if (item.getBody() == null || item.getBody().isEmpty()) {
                    item.setBody(stripped);
                }
                if (item.getImageUrl() == null || item.getImageUrl().isEmpty()) {
                    String img = extractFirstImg(value);
                    if (img != null) item.setImageUrl(img);
                }
                break;
            case "pubdate":
            case "dc:date":
            case "published": {
                Date d = parseDate(value);
                if (d != null) item.setPublishedAt(d);
                break;
            }
            case "guid":
                item.setExternalId(value);
                break;
            default:
                break;
        }
    }

    private static void finalizeItem(NewsItem item, List<NewsItem> out) {
        if ((item.getTitle() == null || item.getTitle().isEmpty())
                && (item.getSourceUrl() == null || item.getSourceUrl().isEmpty())) {
            return;
        }
        if (item.getExternalId() == null || item.getExternalId().isEmpty()) {
            if (item.getSourceUrl() != null && !item.getSourceUrl().isEmpty()) {
                item.setExternalId(item.getSourceUrl());
            }
        }
        if (item.getPublishedAt() == null) {
            item.setPublishedAt(item.getFetchedAt());
        }
        out.add(item);
    }

    private static String stripHtml(String input) {
        if (input == null) return "";
        try {
            String s = Html.fromHtml(input, Html.FROM_HTML_MODE_COMPACT).toString();
            return s.trim();
        } catch (Throwable t) {
            return input.replaceAll("<[^>]+>", "").trim();
        }
    }

    private static String extractFirstImg(String html) {
        if (html == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static Date parseDate(String value) {
        if (value == null || value.isEmpty()) return null;
        SimpleDateFormat[] formats = new SimpleDateFormat[] {
                new SimpleDateFormat(RFC822, Locale.US),
                new SimpleDateFormat(RFC822_ALT, Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        };
        for (SimpleDateFormat fmt : formats) {
            try {
                return fmt.parse(value);
            } catch (ParseException ignored) {
            }
        }
        return null;
    }
}
