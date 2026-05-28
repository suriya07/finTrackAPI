package com.example.financemanager.service;

import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.repositories.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves a category id into the set of its own id plus all descendant ids,
 * so spend/income roll-ups include child categories. Shared by reporting code.
 */
@Service
public class CategoryHierarchyService {

    private final CategoryRepository categoryRepository;

    public CategoryHierarchyService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Set<UUID> selfAndDescendantIds(UUID categoryId) {
        Set<UUID> ids = new HashSet<>();
        if (categoryId == null) {
            return ids;
        }
        ids.add(categoryId);
        categoryRepository.findById(categoryId).ifPresent(category -> collect(category, ids));
        return ids;
    }

    private void collect(CategoryEntity category, Collection<UUID> ids) {
        if (category.getSubCategories() != null) {
            for (CategoryEntity sub : category.getSubCategories()) {
                ids.add(sub.getId());
                collect(sub, ids);
            }
        }
    }
}
