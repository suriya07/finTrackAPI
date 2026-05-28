package com.example.financemanager.controller;

import com.example.financemanager.dto.BudgetStatusDTO;
import com.example.financemanager.dto.CategorySpendDTO;
import com.example.financemanager.dto.MonthlySummaryDTO;
import com.example.financemanager.dto.TrendPointDTO;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public MonthlySummaryDTO summary(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        YearMonth ym = resolveMonth(month, year);
        return dashboardService.summary(user.getUserId(), ym.getYear(), ym.getMonthValue());
    }

    @GetMapping("/categories")
    public List<CategorySpendDTO> categoryBreakdown(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "EXPENSE") String type) {
        YearMonth ym = resolveMonth(month, year);
        return dashboardService.categoryBreakdown(user.getUserId(), ym.getYear(), ym.getMonthValue(), type);
    }

    @GetMapping("/trends")
    public List<TrendPointDTO> trends(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(defaultValue = "6") int months) {
        return dashboardService.trends(user.getUserId(), months);
    }

    @GetMapping("/budget-status")
    public List<BudgetStatusDTO> budgetStatus(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        YearMonth ym = resolveMonth(month, year);
        return dashboardService.budgetStatus(user.getUserId(), ym.getYear(), ym.getMonthValue());
    }

    private YearMonth resolveMonth(Integer month, Integer year) {
        if (month != null && year != null) {
            return YearMonth.of(year, month);
        }
        return YearMonth.now();
    }
}
