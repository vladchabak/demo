package com.localpro.listing;

import com.localpro.listing.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @InjectMocks CategoryService categoryService;

    @Test
    void getTopLevelCategories_flatList_returnsAllAsRoots() {
        List<Category> cats = List.of(
                cat("Cleaning", null),
                cat("Plumbing", null)
        );
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(cats);

        List<CategoryResponse> result = categoryService.getTopLevelCategories();

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(r -> assertThat(r.children()).isEmpty());
    }

    @Test
    void getTopLevelCategories_withChildren_buildsTree() {
        Category parent = cat("Cleaning", null);
        Category child1 = cat("Regular Cleaning", parent);
        Category child2 = cat("Deep Cleaning", parent);
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(parent, child1, child2));

        List<CategoryResponse> result = categoryService.getTopLevelCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Cleaning");
        assertThat(result.get(0).children()).hasSize(2);
    }

    @Test
    void getTopLevelCategories_emptyRepo_returnsEmptyList() {
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(categoryService.getTopLevelCategories()).isEmpty();
    }

    @Test
    void getTopLevelCategories_childrenExcludedFromTopLevel() {
        Category root = cat("IT & Tech", null);
        Category child = cat("Web Dev", root);
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(root, child));

        List<CategoryResponse> roots = categoryService.getTopLevelCategories();

        assertThat(roots).hasSize(1);
        assertThat(roots).noneMatch(r -> r.name().equals("Web Dev"));
    }

    private Category cat(String name, Category parent) {
        return Category.builder()
                .id(UUID.randomUUID())
                .name(name)
                .icon(name.toLowerCase())
                .parent(parent)
                .sortOrder(0)
                .build();
    }
}
