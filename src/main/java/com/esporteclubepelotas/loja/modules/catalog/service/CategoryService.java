package com.esporteclubepelotas.loja.modules.catalog.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.modules.catalog.dto.CategoryDto;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductCategory;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDto> listPublic() {
        return categoryRepository.findAllByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> listAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(CategoryDto::fromEntity)
                .toList();
    }

    @Transactional
    public CategoryDto create(CategoryDto dto) {
        String slug = toSlug(dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : dto.getName());
        if (categoryRepository.existsBySlug(slug)) {
            throw new BusinessException("Já existe uma categoria com este slug: " + slug);
        }
        ProductCategory category = ProductCategory.builder()
                .name(dto.getName().trim())
                .slug(slug)
                .description(dto.getDescription())
                .sortOrder(dto.getSortOrder())
                .active(true)
                .build();
        return CategoryDto.fromEntity(categoryRepository.save(category));
    }

    @Transactional
    public CategoryDto update(String id, CategoryDto dto) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
        String targetSlug = toSlug(dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : dto.getName());
        if (!targetSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(targetSlug)) {
            throw new BusinessException("Já existe outra categoria com o slug: " + targetSlug);
        }
        category.setName(dto.getName().trim());
        category.setSlug(targetSlug);
        category.setDescription(dto.getDescription());
        category.setSortOrder(dto.getSortOrder());
        category.setActive(dto.isActive());
        return CategoryDto.fromEntity(categoryRepository.save(category));
    }

    public static String toSlug(String raw) {
        if (raw == null) return "item";
        String normalized = Normalizer.normalize(raw.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "item" : normalized;
    }
}
