package com.example.financemanager.dto;

import java.util.UUID;
import java.util.List;

public class CategoryDTO {
    private String name;
    private UUID parentId;
    private Integer allowedNestingDepth;
    private String type;
    private List<CategoryDTO> subCategories;

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public Integer getAllowedNestingDepth() {
        return allowedNestingDepth;
    }

    public void setAllowedNestingDepth(Integer allowedNestingDepth) {
        this.allowedNestingDepth = allowedNestingDepth;
    }

    public List<CategoryDTO> getSubCategories() {
        return subCategories;
    }

    public void setSubCategories(List<CategoryDTO> subCategories) {
        this.subCategories = subCategories;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
