package com.esporteclubepelotas.loja.modules.auth.controller;

import com.esporteclubepelotas.loja.common.security.CurrentUser;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.auth.dto.StoreStaffRequestDto;
import com.esporteclubepelotas.loja.modules.auth.dto.StoreUserDto;
import com.esporteclubepelotas.loja.modules.auth.service.StoreStaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER')")
@Tag(name = "Store Staff Admin", description = "Gestão de equipe e perfis próprios da Loja")
public class StoreStaffAdminController {

    private final StoreStaffService storeStaffService;

    @GetMapping
    @Operation(summary = "Lista colaboradores e usuários vinculados à Loja")
    public ResponseEntity<List<StoreUserDto>> list() {
        return ResponseEntity.ok(storeStaffService.listStaff());
    }

    @PostMapping
    @Operation(summary = "Cadastra colaborador exclusivo da Loja (sem necessidade de conta no Portal)")
    public ResponseEntity<StoreUserDto> create(
            @Valid @RequestBody StoreStaffRequestDto request,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(storeStaffService.createLocalStaff(request, actor));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualiza perfil operacional, status ou senha do colaborador")
    public ResponseEntity<StoreUserDto> update(
            @PathVariable String id,
            @Valid @RequestBody StoreStaffRequestDto request,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(storeStaffService.updateStaff(id, request, actor));
    }
}
