package com.example.financemanager.controller;

import com.example.financemanager.entities.CategoryEntity;
import com.example.financemanager.repositories.CategoryRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;

    public CategoryController(CategoryRepository categoryRepository, UserRepository userRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<CategoryEntity> getCategories(@AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) String type) {
        // Return only root categories to avoid duplication (subcategories are nested in
        // children)
        List<CategoryEntity> all = categoryRepository.findByUserId(user.getUserId());

        if (type != null && !type.isBlank()) {
            final String filterType = type.toUpperCase();
            return all.stream()
                    .filter(c -> c.getParent() == null && filterType.equals(c.getType()))
                    .toList();
        }

        return all.stream().filter(c -> c.getParent() == null).toList();
    }

    @PostMapping
    public CategoryEntity createCategory(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody com.example.financemanager.dto.CategoryDTO dto) {
        CategoryEntity category = new CategoryEntity();
        category.setName(dto.getName());
        category.setAllowedNestingDepth(dto.getAllowedNestingDepth());
        category.setUser(userRepository.getReferenceById(user.getUserId()));

        // Validate and set type
        if (dto.getType() == null || dto.getType().trim().isEmpty()) {
            throw new RuntimeException("Category type is required");
        }
        String type = dto.getType().toUpperCase();
        if (!type.equals("INCOME") && !type.equals("EXPENSE") && !type.equals("BUDGET")) {
            throw new RuntimeException("Invalid category type. Must be INCOME, EXPENSE, or BUDGET");
        }
        category.setType(type);

        if (dto.getParentId() != null) {
            CategoryEntity parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found"));

            // Validate: Parent must belong to same user
            if (!parent.getUser().getId().equals(user.getUserId())) {
                throw new RuntimeException("Invalid parent category");
            }

            // Validate: Parent type must match child type
            if (!parent.getType().equals(type)) {
                throw new RuntimeException("Child category type must match parent category type");
            }

            // Validate: Nesting Limit
            if (parent.getAllowedNestingDepth() != null && parent.getAllowedNestingDepth() <= 0) {
                throw new RuntimeException("Parent category does not allow further nesting");
            }

            category.setParent(parent);

            // Decrease depth limit for child
            if (parent.getAllowedNestingDepth() != null) {
                category.setAllowedNestingDepth(parent.getAllowedNestingDepth() - 1);
            }
        }

        return categoryRepository.save(category);
    }
}
