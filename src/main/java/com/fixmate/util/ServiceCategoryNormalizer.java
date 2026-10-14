package com.fixmate.util;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resolves AI labels to an existing catalog name; never invents a database category. */
public final class ServiceCategoryNormalizer {
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("computer", "laptop"), Map.entry("pc", "laptop"),
            Map.entry("notebook", "laptop"), Map.entry("phone", "mobile"),
            Map.entry("smartphone", "mobile"), Map.entry("electrician", "electrical"),
            Map.entry("plumber", "plumbing"), Map.entry("air conditioning", "ac"),
            Map.entry("air conditioner", "ac"), Map.entry("fridge", "refrigerator"),
            Map.entry("appliance", "home appliances"), Map.entry("appliances", "home appliances"),
            Map.entry("car", "vehicle"), Map.entry("automobile", "vehicle"));

    private ServiceCategoryNormalizer() { }

    public static String resolve(String label, List<String> catalog) {
        if (label == null || label.isBlank()) return null;
        // Prefer the exact catalog entry when multiple equivalent categories exist.
        for (String name : catalog) if (name.equalsIgnoreCase(label.trim())) return name;
        String key = key(label);
        List<String> equivalents = catalog.stream().filter(name -> key(name).equals(key)).toList();
        return equivalents.size() == 1 ? equivalents.get(0) : null;
    }

    private static String key(String label) {
        String cleaned = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\b(repairs?|services?|technicians?)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        return ALIASES.getOrDefault(cleaned, cleaned);
    }
}
