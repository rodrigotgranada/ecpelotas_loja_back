package com.esporteclubepelotas.loja.modules.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
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
public class CreateCounterSaleDto {

    private String customerType;
    private String customerCpf;
    private String customerName;

    @DecimalMin(value = "0.00")
    private BigDecimal discountAmount;

    private String notes;

    @Valid
    @NotEmpty(message = "Adicione pelo menos 1 item na venda do balcão")
    private List<UpdateOrderItemsDto.OrderLineInputDto> items;

    @Valid
    @NotEmpty(message = "Informe as formas de pagamento")
    private List<ConfirmOrderPaymentDto.PaymentLineDto> payments;
}
