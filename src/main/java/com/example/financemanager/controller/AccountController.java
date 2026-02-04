package com.example.financemanager.controller;

import com.example.financemanager.dto.AccountDTO;
import com.example.financemanager.entities.AccountEntity;
import com.example.financemanager.repositories.AccountRepository;
import com.example.financemanager.repositories.UserRepository;
import com.example.financemanager.service.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountController(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    public List<AccountDTO> getAccounts(@AuthenticationPrincipal CustomUserDetails user) {
        return accountRepository.findByUserId(user.getUserId()).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @PostMapping
    public AccountDTO createAccount(@AuthenticationPrincipal CustomUserDetails user, @RequestBody AccountDTO dto) {
        AccountEntity account = new AccountEntity();
        account.setUser(userRepository.getReferenceById(user.getUserId()));
        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @PutMapping("/{id}")
    public AccountDTO updateAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @RequestBody AccountDTO dto) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        mapDtoToEntity(dto, account);
        return convertToDTO(accountRepository.save(account));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        AccountEntity account = accountRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (!account.getUser().getId().equals(user.getUserId())) {
            throw new RuntimeException("Unauthorized");
        }

        accountRepository.delete(account);
        return ResponseEntity.ok().build();
    }

    private AccountDTO convertToDTO(AccountEntity entity) {
        AccountDTO dto = new AccountDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setBalance(entity.getBalance());
        dto.setBankName(entity.getBankName());
        dto.setBillingCycleStartDay(entity.getBillingCycleStartDay());
        dto.setBillDateDay(entity.getBillDateDay());
        dto.setDueDateDay(entity.getDueDateDay());
        return dto;
    }

    private void mapDtoToEntity(AccountDTO dto, AccountEntity entity) {
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setBalance(dto.getBalance());
        entity.setBankName(dto.getBankName());
        entity.setBillingCycleStartDay(dto.getBillingCycleStartDay());
        entity.setBillDateDay(dto.getBillDateDay());
        entity.setDueDateDay(dto.getDueDateDay());
    }
}
