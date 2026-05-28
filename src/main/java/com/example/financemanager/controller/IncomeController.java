package com.example.financemanager.controller;

import com.example.financemanager.dto.IncomeDTO;
import com.example.financemanager.entities.IncomeEntity;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.service.IncomeService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/incomes")
public class IncomeController {

    private final IncomeService incomeService;

    public IncomeController(IncomeService incomeService) {
        this.incomeService = incomeService;
    }

    @GetMapping
    public List<IncomeEntity> getIncomes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID accountId,
            @RequestParam(required = false) String search) {
        return incomeService.getIncomes(user.getUserId(), month, year, fromDate, toDate, categoryId, accountId, search);
    }

    /** Paginated variant of the income list. Returns a Spring Data Page (content + paging metadata). */
    @GetMapping("/page")
    public Page<IncomeEntity> getIncomesPaged(
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
        return incomeService.getIncomesPaged(user.getUserId(), month, year, fromDate, toDate, categoryId, accountId,
                search, page, size);
    }

    @PostMapping
    public IncomeEntity createIncome(@AuthenticationPrincipal CustomUserDetails user, @RequestBody IncomeDTO dto) {
        return incomeService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public IncomeEntity updateIncome(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody IncomeDTO dto) {
        return incomeService.update(user.getUserId(), id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteIncome(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) {
        incomeService.delete(user.getUserId(), id);
    }
}
