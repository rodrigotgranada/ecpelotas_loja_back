package com.esporteclubepelotas.loja.modules.orders.controller;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.security.CurrentUser;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.orders.dto.*;
import com.esporteclubepelotas.loja.modules.orders.service.MemberLookupService;
import com.esporteclubepelotas.loja.modules.orders.service.OrderCheckoutService;
import com.esporteclubepelotas.loja.modules.orders.service.OrderNegotiationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'SELLER')")
@Tag(name = "PDV Orders & Checkout", description = "Fila WhatsApp, edição de pedidos, balcão físico e múltiplos pagamentos")
public class AdminOrderController {

    private final OrderNegotiationService orderNegotiationService;
    private final OrderCheckoutService orderCheckoutService;
    private final MemberLookupService memberLookupService;

    @GetMapping
    @Operation(summary = "Lista pedidos para fila de atendimento e histórico administrativo")
    public ResponseEntity<PageResponseDto<OrderResponseDto>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(orderNegotiationService.listAdminOrders(status, search, page, limit));
    }

    @GetMapping("/{orderCode}")
    @Operation(summary = "Detalha um pedido com itens, pagamentos e linha do tempo")
    public ResponseEntity<OrderResponseDto> getOrder(@PathVariable String orderCode) {
        return ResponseEntity.ok(orderNegotiationService.getAdminOrder(orderCode));
    }

    @GetMapping("/member-check")
    @Operation(summary = "Consulta CPF de Sócio no Portal (com fallback cache) para venda no Balcão")
    public ResponseEntity<MemberLookupDto> lookupMember(@RequestParam String cpf) {
        return ResponseEntity.ok(memberLookupService.lookupByCpf(cpf));
    }

    @PostMapping("/{orderCode}/negotiate")
    @Operation(summary = "Assume atendimento de reserva WhatsApp (muda para NEGOTIATING)")
    public ResponseEntity<OrderResponseDto> startNegotiating(
            @PathVariable String orderCode,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(orderNegotiationService.startNegotiating(orderCode, actor));
    }

    @PutMapping("/{orderCode}")
    @Operation(summary = "Edita itens, quantidades e desconto de pedido em negociação ajustando estoque reservado")
    public ResponseEntity<OrderResponseDto> updateOrderItems(
            @PathVariable String orderCode,
            @Valid @RequestBody UpdateOrderItemsDto dto,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(orderNegotiationService.updateOrderItems(orderCode, dto, actor));
    }

    @PostMapping("/{orderCode}/confirm")
    @Operation(summary = "Confirma pagamento múltiplo/único, baixa estoque físico e grava SALE_OUTBOUND no Kardex")
    public ResponseEntity<OrderResponseDto> confirmOrder(
            @PathVariable String orderCode,
            @Valid @RequestBody ConfirmOrderPaymentDto dto,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(orderCheckoutService.confirmWhatsappOrderSale(orderCode, dto, actor));
    }

    @PostMapping("/{orderCode}/cancel")
    @Operation(summary = "Cancela pedido aberto/negociação e devolve estoque reservado")
    public ResponseEntity<OrderResponseDto> cancelOrder(
            @PathVariable String orderCode,
            @RequestBody(required = false) CancelOrderDto body,
            @CurrentUser UserPrincipal actor
    ) {
        String reason = body != null ? body.getReason() : null;
        return ResponseEntity.ok(orderNegotiationService.cancelByStaff(orderCode, reason, actor));
    }

    @PostMapping("/{orderCode}/reactivate")
    @Operation(summary = "Reativa reserva expirada caso o estoque ainda esteja disponível")
    public ResponseEntity<OrderResponseDto> reactivateOrder(
            @PathVariable String orderCode,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(orderNegotiationService.reactivateExpiredOrder(orderCode, actor));
    }

    @PostMapping("/counter-sale")
    @Operation(summary = "Registra venda direta no Balcão (Consumidor Final anônimo ou identificado por CPF)")
    public ResponseEntity<OrderResponseDto> createCounterSale(
            @Valid @RequestBody CreateCounterSaleDto dto,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderCheckoutService.createCounterSale(dto, actor));
    }
}
