package com.esporteclubepelotas.loja.modules.orders.controller;

import com.esporteclubepelotas.loja.common.security.CurrentUser;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.orders.dto.CancelOrderDto;
import com.esporteclubepelotas.loja.modules.orders.dto.CreateWhatsappReservationDto;
import com.esporteclubepelotas.loja.modules.orders.dto.OrderResponseDto;
import com.esporteclubepelotas.loja.modules.orders.service.CustomerOrderService;
import com.esporteclubepelotas.loja.modules.orders.service.OrderReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customer Orders", description = "Reservas WhatsApp e histórico de pedidos do torcedor autenticado")
public class CustomerOrderController {

    private final OrderReservationService orderReservationService;
    private final CustomerOrderService customerOrderService;

    @PostMapping("/whatsapp-reservation")
    @Operation(summary = "Cria reserva WhatsApp para usuário autenticado com CPF")
    public ResponseEntity<OrderResponseDto> createWhatsappReservation(
            @Valid @RequestBody CreateWhatsappReservationDto request,
            @CurrentUser UserPrincipal principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderReservationService.createWhatsappReservation(request, principal));
    }

    @GetMapping("/my")
    @Operation(summary = "Lista todos os pedidos do usuário autenticado (em aberto, concluídos, cancelados)")
    public ResponseEntity<List<OrderResponseDto>> listMyOrders(@CurrentUser UserPrincipal principal) {
        return ResponseEntity.ok(customerOrderService.listOrdersForCustomer(
                principal.getId(), principal.getPortalUserId(), principal.getCpf()
        ));
    }

    @GetMapping("/my/{orderCode}")
    @Operation(summary = "Detalha um pedido específico do usuário autenticado")
    public ResponseEntity<OrderResponseDto> getMyOrder(
            @PathVariable String orderCode,
            @CurrentUser UserPrincipal principal
    ) {
        return ResponseEntity.ok(customerOrderService.getOrderForCustomer(
                orderCode, principal.getId(), principal.getPortalUserId(), principal.getCpf()
        ));
    }

    @PostMapping("/my/{orderCode}/cancel")
    @Operation(summary = "Cancela uma reserva em aberto do usuário autenticado")
    public ResponseEntity<OrderResponseDto> cancelMyOrder(
            @PathVariable String orderCode,
            @RequestBody(required = false) CancelOrderDto body,
            @CurrentUser UserPrincipal principal
    ) {
        String reason = body != null ? body.getReason() : null;
        return ResponseEntity.ok(customerOrderService.cancelOrderForCustomer(
                orderCode, principal.getId(), principal.getPortalUserId(), principal.getCpf(), reason
        ));
    }
}
