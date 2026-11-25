package com.esporteclubepelotas.loja.modules.orders.controller;

import com.esporteclubepelotas.loja.modules.orders.dto.StorePublicConfigDto;
import com.esporteclubepelotas.loja.modules.orders.service.StoreSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Storefront Config", description = "Configurações públicas da Loja Alcateia (WhatsApp e horários)")
public class StoreSettingsPublicController {

    private final StoreSettingsService settingsService;

    @GetMapping("/store-config")
    @Operation(summary = "Retorna configurações públicas de atendimento WhatsApp e expediente da loja")
    public ResponseEntity<StorePublicConfigDto> getPublicConfig() {
        return ResponseEntity.ok(settingsService.getPublicConfig());
    }
}
