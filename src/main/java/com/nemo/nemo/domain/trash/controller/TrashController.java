package com.nemo.nemo.domain.trash.controller;

import com.nemo.nemo.common.dto.ApiResponse;
import com.nemo.nemo.domain.trash.dto.TrashResponse;
import com.nemo.nemo.domain.trash.service.TrashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/trash")
@RequiredArgsConstructor
public class TrashController {

    private final TrashService trashService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<TrashResponse>>> getTrash(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.ok(trashService.getTrash(UUID.fromString(userId))));
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        trashService.restore(id, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> permanentDelete(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId) {
        trashService.permanentDelete(id, UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
