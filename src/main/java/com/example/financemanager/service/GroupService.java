package com.example.financemanager.service;

import com.example.financemanager.dto.GroupDTO;
import com.example.financemanager.entities.GroupEntity;
import com.example.financemanager.repositories.ExpenseRepository;
import com.example.financemanager.repositories.GroupRepository;
import com.example.financemanager.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CRUD for expense groups. Mirrors {@link AccountService}: every operation is
 * scoped to the owning user, and deleting a group first un-assigns its expenses
 * (set group_id = null) so member expenses are kept, just ungrouped.
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository,
            ExpenseRepository expenseRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupDTO> getGroups(UUID userId) {
        return groupRepository.findByUserId(userId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupDTO create(UUID userId, GroupDTO dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group name is required");
        }
        if (groupRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A group with this name already exists");
        }
        GroupEntity group = new GroupEntity();
        group.setUser(userRepository.getReferenceById(userId));
        group.setName(name);
        group.setDescription(emptyToNull(dto.getDescription()));
        return convertToDTO(groupRepository.save(group));
    }

    @Transactional
    public GroupDTO update(UUID userId, UUID id, GroupDTO dto) {
        GroupEntity group = requireOwnedGroup(userId, id);
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Group name is required");
        }
        // Only enforce uniqueness when the name actually changes (case-insensitive).
        if (!group.getName().equalsIgnoreCase(name)
                && groupRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A group with this name already exists");
        }
        group.setName(name);
        group.setDescription(emptyToNull(dto.getDescription()));
        return convertToDTO(groupRepository.save(group));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        GroupEntity group = requireOwnedGroup(userId, id);
        // Ungroup member expenses first so the FK doesn't block the delete.
        expenseRepository.clearGroupAssignments(id);
        groupRepository.delete(group);
    }

    private GroupEntity requireOwnedGroup(UUID userId, UUID id) {
        GroupEntity group = groupRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found"));
        if (!group.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized");
        }
        return group;
    }

    private GroupDTO convertToDTO(GroupEntity entity) {
        GroupDTO dto = new GroupDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        return dto;
    }

    private String emptyToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
