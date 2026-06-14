package com.example.financemanager.controller;

import com.example.financemanager.dto.GroupDTO;
import com.example.financemanager.service.CustomUserDetails;
import com.example.financemanager.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    public List<GroupDTO> getGroups(@AuthenticationPrincipal CustomUserDetails user) {
        return groupService.getGroups(user.getUserId());
    }

    @PostMapping
    public GroupDTO createGroup(@AuthenticationPrincipal CustomUserDetails user, @RequestBody GroupDTO dto) {
        return groupService.create(user.getUserId(), dto);
    }

    @PutMapping("/{id}")
    public GroupDTO updateGroup(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id,
            @RequestBody GroupDTO dto) {
        return groupService.update(user.getUserId(), id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteGroup(@AuthenticationPrincipal CustomUserDetails user, @PathVariable UUID id) {
        groupService.delete(user.getUserId(), id);
        return ResponseEntity.ok().build();
    }
}
