package com.esporteclubepelotas.loja.modules.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWhatsappReservationDto {

    @NotBlank(message = "Informe o número de WhatsApp para atendimento")
    private String customerWhatsapp;

    private String notes;

    @Valid
    @NotEmpty(message = "Adicione pelo menos 1 item no carrinho")
    private List<ReservationLineDto> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationLineDto {
        @NotBlank(message = "Variação (SKU) é obrigatória")
        private String variantId;

        @Min(value = 1, message = "Quantidade mínima é 1")
        private int quantity;
    }
}
