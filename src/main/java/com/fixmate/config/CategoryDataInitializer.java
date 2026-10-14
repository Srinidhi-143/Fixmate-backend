package com.fixmate.config;

import com.fixmate.entity.ServiceCategory;
import com.fixmate.repository.ServiceCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CategoryDataInitializer implements CommandLineRunner {

    private static final List<String> DEFAULT_CATEGORIES = List.of(
            "Mobile Repair",
            "Laptop Repair",
            "Vehicle Repair",
            "Electrical",
            "Plumbing",
            "Home Appliances",
            "AC Repair",
            "Refrigerator Repair",
            "Washing Machine Repair",
            "Other"
    );

    private final ServiceCategoryRepository categoryRepository;

    public CategoryDataInitializer(ServiceCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) {
        for (String name : DEFAULT_CATEGORIES) {
            if (!categoryRepository.existsByNameIgnoreCase(name)) {
                categoryRepository.save(new ServiceCategory(name, toSlug(name), null));
            }
        }
    }

    private String toSlug(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }
}