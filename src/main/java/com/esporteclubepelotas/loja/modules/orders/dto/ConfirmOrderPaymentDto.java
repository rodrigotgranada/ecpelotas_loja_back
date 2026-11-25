package com.esporteclubepelotas.loja.modules.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmOrderPaymentDto {

    @Valid
    @NotEmpty(message = "Informe pelo menos uma forma de pagamento")
    private List<PaymentLineDto> payments;

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentLineDto {
        @NotBlank(message = "Informe a forma de pagamento (PIX, CREDIT, DEBIT, CASH, COMPLIMENTARY)")
        private String paymentMethod;

        @NotNull(message = "Informe o valor da parcela")
        @DecimalMin(value = "0.00")
        private BigDecimal amount;
    }
}
