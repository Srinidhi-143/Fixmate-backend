package com.fixmate.service;

import com.fixmate.dto.CategoryRequest;
import com.fixmate.dto.CategoryResponse;
import com.fixmate.entity.ServiceCategory;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.ServiceCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;

    public ServiceCategoryService(ServiceCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(Long id) {
        return toResponse(findCategory(id));
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        String name = request.name().trim();
        String slug = createSlug(name);
        ensureNameAvailable(name, null);
        ensureSlugAvailable(slug, null);

        ServiceCategory category = categoryRepository.save(
                new ServiceCategory(name, slug, cleanDescription(request.description()))
        );
        return toResponse(category);
    }

    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryRequest request) {
        ServiceCategory category = findCategory(id);
        String name = request.name().trim();
        String slug = createSlug(name);
        ensureNameAvailable(name, id);
        ensureSlugAvailable(slug, id);

        category.updateDetails(name, slug, cleanDescription(request.description()));
        return toResponse(category);
    }

    @Transactional
    public void deleteCategory(Long id) {
        ServiceCategory category = findCategory(id);
        categoryRepository.delete(category);
    }

    private ServiceCategory findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private void ensureNameAvailable(String name, Long currentId) {
        boolean exists = currentId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (exists) {
            throw new IllegalArgumentException("A category with this name already exists");
        }
    }

    private void ensureSlugAvailable(String slug, Long currentId) {
        boolean exists = currentId == null
                ? categoryRepository.existsBySlug(slug)
                : categoryRepository.existsBySlugAndIdNot(slug, currentId);
        if (exists) {
            throw new IllegalArgumentException("A category with this name already exists");
        }
    }

    private String createSlug(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Category name must contain letters or numbers");
        }
        return slug;
    }

    private String cleanDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private CategoryResponse toResponse(ServiceCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}