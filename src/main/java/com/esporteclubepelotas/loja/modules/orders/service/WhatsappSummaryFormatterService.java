package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.modules.orders.entity.OrderItem;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.Locale;

@Service
public class WhatsappSummaryFormatterService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public String buildSummaryText(StoreOrder order) {
        NumberFormat brl = NumberFormat.getCurrencyInstance(PT_BR);
        StringBuilder sb = new StringBuilder();
        sb.append("Olá! Quero finalizar minha reserva na Loja Oficial Alcateia:%0A".replace("%0A", "\n"));
        sb.append("*Pedido:* ").append(order.getOrderCode()).append("\n");
        sb.append("*Torcedor:* ").append(order.getCustomerName());
        if (order.isSocioDiscountApplied()) {
            sb.append(" (Sócio em dia ✅)");
        }
        sb.append("\n\n*Itens reservados:*\n");
        for (OrderItem item : order.getItems()) {
            sb.append("- ").append(item.getQuantity()).append("x ")
                    .append(item.getProductName())
                    .append(" (Tam: ").append(item.getSizeLabel()).append(", SKU: ").append(item.getSku()).append(") — ")
                    .append(brl.format(item.getLineTotal())).append("\n");
        }
        sb.append("\n*Total estimado:* ").append(brl.format(order.getTotalAmount()));
        if (order.getNotes() != null && !order.getNotes().isBlank()) {
            sb.append("\n*Observação:* ").append(order.getNotes());
        }
        return sb.toString();
    }
}
