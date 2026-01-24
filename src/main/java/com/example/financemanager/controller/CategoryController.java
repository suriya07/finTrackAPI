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
    public List<CategoryEntity> getCategories(@AuthenticationPrincipal CustomUserDetails user) {
        // Return only root categories to avoid duplication (subcategories are nested in
        // children)
        List<CategoryEntity> all = categoryRepository.findByUserId(user.getUserId());
        return all.stream().filter(c -> c.getParent() == null).toList();
    }

    @PostMapping
    public CategoryEntity createCategory(@AuthenticationPrincipal CustomUserDetails user,
            @RequestBody com.example.financemanager.dto.CategoryDTO dto) {
        CategoryEntity category = new CategoryEntity();
        category.setName(dto.getName());
        category.setAllowedNestingDepth(dto.getAllowedNestingDepth());
        category.setUser(userRepository.getReferenceById(user.getUserId()));

        if (dto.getParentId() != null) {
            CategoryEntity parent = categoryRepository.findById(dto.getParentId())
                    .orElseThrow(() -> new RuntimeException("Parent category not found"));

            // Validate: Parent must belong to same user
            if (!parent.getUser().getId().equals(user.getUserId())) {
                throw new RuntimeException("Invalid parent category");
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
