package com.example.financemanager.dto;

public record ApiResponse(boolean success,
                          String status,
                          String message) {
}
