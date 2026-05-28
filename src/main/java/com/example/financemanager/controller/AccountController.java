package com.example.financemanager.controller;

import com.example.financemanager.dto.AccountDTO;
import com.example.financemanager.service.AccountService;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountDTO> getAccounts(@AuthenticationPrincipal CustomUserDetails user) {
        return accountService.getAccounts(user.getUserId());
    }

    @PostMapping
    public AccountDTO createAccount(@AuthenticationPrincipal CustomUserDetails user, @RequestBody AccountDTO dto) {
        return accountService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public AccountDTO updateAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @RequestBody AccountDTO dto) {
        return accountService.update(user.getUserId(), id, dto);
    }

    @PostMapping("/{id}/recalculate")
    public AccountDTO recalculateBalance(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        return accountService.recalculateBalance(user.getUserId(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        accountService.delete(user.getUserId(), id);
        return ResponseEntity.ok().build();
    }
}
