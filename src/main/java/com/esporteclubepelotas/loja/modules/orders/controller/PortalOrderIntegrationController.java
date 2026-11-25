package com.esporteclubepelotas.loja.modules.orders.controller;

import com.esporteclubepelotas.loja.common.security.StoreInternalKeyValidator;
import com.esporteclubepelotas.loja.modules.orders.dto.CancelOrderDto;
import com.esporteclubepelotas.loja.modules.orders.dto.OrderResponseDto;
import com.esporteclubepelotas.loja.modules.orders.service.CustomerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/integrations/portal/orders")
@RequiredArgsConstructor
@Tag(name = "Portal Orders S2S", description = "Consulta e cancelamento S2S de pedidos para o Perfil do usuário no Portal")
public class PortalOrderIntegrationController {

    private final StoreInternalKeyValidator internalKeyValidator;
    private final CustomerOrderService customerOrderService;

    @GetMapping
    @Operation(summary = "Lista pedidos da Loja pertencentes a um usuário do Portal (S2S)")
    public ResponseEntity<List<OrderResponseDto>> listOrdersForPortalUser(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @RequestParam String portalUserId,
            @RequestParam(required = false) String cpf
    ) {
        internalKeyValidator.validateOrThrow(apiKey);
        return ResponseEntity.ok(customerOrderService.listOrdersForCustomer(null, portalUserId, cpf));
    }

    @PostMapping("/{orderCode}/cancel")
    @Operation(summary = "Cancela pedido em aberto a pedido do torcedor no Portal (S2S)")
    public ResponseEntity<OrderResponseDto> cancelOrderForPortalUser(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @PathVariable String orderCode,
            @RequestBody CancelOrderDto request
    ) {
        internalKeyValidator.validateOrThrow(apiKey);
        return ResponseEntity.ok(customerOrderService.cancelOrderForCustomer(
                orderCode, null, request.getPortalUserId(), request.getCpf(), request.getReason()
        ));
    }
}
