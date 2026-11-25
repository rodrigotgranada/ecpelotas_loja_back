package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.orders.entity.OrderEvent;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import com.esporteclubepelotas.loja.modules.orders.repository.StoreOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final StoreOrderRepository orderRepository;
    private final CustomerOrderService customerOrderService;

    @Scheduled(fixedDelay = 60_000L)
    @Transactional
    public void expireOverdueWhatsappReservations() {
        List<StoreOrder> overdue = orderRepository.findByStatusAndExpiresAtBefore("PENDING_WHATSAPP", Instant.now());
        if (overdue.isEmpty()) return;

        for (StoreOrder order : overdue) {
            customerOrderService.releaseReservedStock(order);
            order.setStatus("EXPIRED");
            order.getEvents().add(OrderEvent.builder()
                    .order(order)
                    .eventType("EXPIRED_TTL")
                    .actorName("Sistema")
                    .description("Reserva expirada automaticamente após o prazo comercial.")
                    .build());
            orderRepository.save(order);
        }
        log.info("Scheduler expirou {} reserva(s) WhatsApp vencida(s) e liberou estoque reservado.", overdue.size());
    }
}
