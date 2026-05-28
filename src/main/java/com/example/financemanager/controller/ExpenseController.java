package com.example.financemanager.controller;

import com.example.financemanager.dto.ExpenseDTO;
import com.example.financemanager.dto.ExpenseFilterDTO;
import com.example.financemanager.entities.ExpenseEntity;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.service.ExpenseService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseEntity> getExpenses(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String search) {
        return expenseService.getExpenses(user.getUserId(), month, year, fromDate, toDate, categoryId, accountId,
                search);
    }

    /** Paginated variant of the expense list. Returns a Spring Data Page (content + paging metadata). */
    @GetMapping("/page")
    public Page<ExpenseEntity> getExpensesPaged(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return expenseService.getExpensesPaged(user.getUserId(), month, year, fromDate, toDate, categoryId, accountId,
                search, page, size);
    }

    @PostMapping("/filter")
    public List<ExpenseEntity> getFilteredExpenses(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ExpenseFilterDTO filter) {
        return expenseService.getExpenses(user.getUserId(), filter.getMonth(), filter.getYear(),
                filter.getFromDate(), filter.getToDate(), filter.getCategoryId(), filter.getAccountId(),
                filter.getSearch());
    }

    @PostMapping
    public ExpenseEntity createExpense(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ExpenseDTO dto) {
        return expenseService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public ExpenseEntity updateExpense(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ExpenseDTO dto) {
        return expenseService.update(user.getUserId(), id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteExpense(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails user) {
        expenseService.delete(user.getUserId(), id);
    }
}
