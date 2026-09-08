package com.acooldog.toolbox.utils;

import android.icu.text.Transliterator;
import android.os.Build;
import androidx.annotation.RequiresApi;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SearchSortUtils {
    private static final Map<String, String> TRANSLITERATION_CACHE =
            new LinkedHashMap<String, String>(256, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 256;
                }
            };

    @RequiresApi(29)
    private static final class Api29 {
        private static Transliterator transliterator;

        static String transliterate(String input) {
            if (transliterator == null) {
                transliterator = Transliterator.getInstance("Han-Latin; Latin-ASCII");
            }
            return transliterator.transliterate(input);
        }
    }

    private SearchSortUtils() {
    }

    @NonNull
    public static String normalize(@NonNull String input) {
        String trimmed = input.trim().toLowerCase(Locale.ROOT);
        return trimmed.replaceAll("\\s+", " ");
    }

    @NonNull
    public static synchronized String transliterate(@NonNull String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return "";
        }
        String cached = TRANSLITERATION_CACHE.get(normalized);
        if (cached != null) return cached;
        String latin;
        try {
            latin = Build.VERSION.SDK_INT >= 29 ? Api29.transliterate(normalized) : normalized;
        } catch (Exception ignored) {
            latin = normalized;
        }
        String result = latin.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        TRANSLITERATION_CACHE.put(normalized, result);
        return result;
    }

    @NonNull
    public static String buildInitials(@NonNull String input) {
        String transliterated = transliterate(input);
        if (transliterated.isEmpty()) {
            return "";
        }
        String[] parts = transliterated.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                builder.append(part.charAt(0));
            }
        }
        return builder.toString();
    }

    public static boolean matches(@NonNull String query, @NonNull String target) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String normalizedTarget = normalize(target);
        if (normalizedTarget.contains(normalizedQuery)) {
            return true;
        }
        String transliterated = transliterate(target);
        if (transliterated.contains(normalizedQuery)) {
            return true;
        }
        return buildInitials(target).contains(normalizedQuery.replace(" ", ""));
    }

    @NonNull
    public static String buildSortKey(@NonNull String input) {
        String initials = buildInitials(input);
        String transliterated = transliterate(input);
        String normalized = normalize(input);
        return (initials.isEmpty() ? "~" : initials)
                + "|"
                + (transliterated.isEmpty() ? normalized : transliterated)
                + "|"
                + normalized;
    }
}
