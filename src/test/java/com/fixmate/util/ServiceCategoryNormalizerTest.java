package com.fixmate.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ServiceCategoryNormalizerTest {
    @Test void resolvesCommonAliasesToExistingCatalogNames() {
        var catalog = List.of("Laptop Repair", "Mobile Repair", "Electrical", "Plumbing", "AC Repair", "Refrigerator Repair");
        for (String alias : List.of("Laptop Repair", "Computer Repair", "Laptop Service", " laptop-service ", "PC Repair"))
            assertEquals("Laptop Repair", ServiceCategoryNormalizer.resolve(alias, catalog));
        assertEquals("Mobile Repair", ServiceCategoryNormalizer.resolve("Phone Service", catalog));
        assertEquals("AC Repair", ServiceCategoryNormalizer.resolve("Air Conditioner Repair", catalog));
        assertEquals("Electrical", ServiceCategoryNormalizer.resolve("Electrician", catalog));
    }
    @Test void unknownAbsentAndAmbiguousCategoriesAreNotInvented() {
        assertNull(ServiceCategoryNormalizer.resolve("Laptop Repair", List.of("Plumbing")));
        assertNull(ServiceCategoryNormalizer.resolve("Imaginary Repair", List.of("Other")));
        assertNull(ServiceCategoryNormalizer.resolve(null, List.of("Other")));
        assertNull(ServiceCategoryNormalizer.resolve("Laptop Service", List.of("Laptop Repair", "Computer Repair")));
        assertEquals("Computer Repair", ServiceCategoryNormalizer.resolve("Computer Repair", List.of("Laptop Repair", "Computer Repair")));
    }
}
