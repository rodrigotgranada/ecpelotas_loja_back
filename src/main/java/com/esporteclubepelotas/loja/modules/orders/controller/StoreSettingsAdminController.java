package com.esporteclubepelotas.loja.modules.orders.controller;

import com.esporteclubepelotas.loja.modules.orders.dto.StoreSettingsDto;
import com.esporteclubepelotas.loja.modules.orders.service.StoreSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/store-settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER')")
@Tag(name = "PDV Settings Admin", description = "Configurações de WhatsApp, expediente e regras de reserva da Loja")
public class StoreSettingsAdminController {

    private final StoreSettingsService storeSettingsService;

    @GetMapping
    @Operation(summary = "Retorna configurações operacionais atuais da Loja")
    public ResponseEntity<StoreSettingsDto> getSettings() {
        return ResponseEntity.ok(storeSettingsService.getAdminSettings());
    }

    @PutMapping
    @Operation(summary = "Atualiza WhatsApp oficial, horário comercial e TTL de reserva")
    public ResponseEntity<StoreSettingsDto> updateSettings(@Valid @RequestBody StoreSettingsDto dto) {
        return ResponseEntity.ok(storeSettingsService.updateAdminSettings(dto));
    }
}
