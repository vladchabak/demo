package com.localpro.listing;

import com.localpro.listing.dto.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> getTopLevelCategories() {
        List<Category> all = categoryRepository.findAll(Sort.by("sortOrder"));
        // Group children by their parent's ID.
        // Calling getParent().getId() on a lazy proxy is safe — Hibernate resolves
        // the ID from the FK column without initialising the full parent proxy.
        Map<UUID, List<Category>> byParent = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(c -> toResponse(c, byParent))
                .toList();
    }

    private CategoryResponse toResponse(Category category, Map<UUID, List<Category>> byParent) {
        List<CategoryResponse> children = byParent.getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> toResponse(child, byParent))
                .toList();
        return new CategoryResponse(category.getId(), category.getName(), category.getIcon(), children);
    }
}
