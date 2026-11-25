package com.esporteclubepelotas.loja.modules.orders.service;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.modules.orders.dto.ConfirmOrderPaymentDto.PaymentLineDto;
import com.esporteclubepelotas.loja.modules.orders.entity.OrderPayment;
import com.esporteclubepelotas.loja.modules.orders.entity.StoreOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

@Service
public class OrderPaymentValidatorService {

    private static final Set<String> ALLOWED_METHODS = Set.of(
            "PIX", "CREDIT", "DEBIT", "CASH", "COMPLIMENTARY"
    );

    public List<OrderPayment> buildAndValidatePayments(StoreOrder order, List<PaymentLineDto> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("Informe pelo menos uma forma de pagamento.");
        }

        BigDecimal expectedTotal = order.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal sum = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        List<OrderPayment> result = new java.util.ArrayList<>();
        for (PaymentLineDto line : lines) {
            String method = line.getPaymentMethod() != null
                    ? line.getPaymentMethod().trim().toUpperCase()
                    : "";
            if (!ALLOWED_METHODS.contains(method)) {
                throw new BusinessException("Forma de pagamento inválida: " + line.getPaymentMethod());
            }

            BigDecimal amount = line.getAmount() != null
                    ? line.getAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Valor de pagamento não pode ser negativo.");
            }
            if (expectedTotal.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(BigDecimal.ZERO) == 0) {
                throw new BusinessException("Cada parcela informada deve ser maior que zero.");
            }

            sum = sum.add(amount);
            result.add(OrderPayment.builder()
                    .order(order)
                    .paymentMethod(method)
                    .amount(amount)
                    .build());
        }

        if (sum.compareTo(expectedTotal) != 0) {
            throw new BusinessException(
                    "Soma dos pagamentos (R$ " + sum + ") difere do total do pedido (R$ " + expectedTotal + ")."
            );
        }
        return result;
    }
}
