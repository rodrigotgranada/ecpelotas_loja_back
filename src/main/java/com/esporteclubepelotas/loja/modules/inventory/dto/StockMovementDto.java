package com.esporteclubepelotas.loja.modules.inventory.dto;

import com.esporteclubepelotas.loja.modules.inventory.entity.StockMovement;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockMovementDto {
    private String id;
    private String variantId;
    private String sku;
    private String productName;
    private String sizeLabel;
    private String documentId;
    private String movementType;
    private int quantityDelta;
    private int balanceBefore;
    private int balanceAfter;
    private BigDecimal unitCost;
    private String reason;
    private String actorName;
    private Instant createdAt;

    public static StockMovementDto fromEntity(StockMovement m) {
        return StockMovementDto.builder()
                .id(m.getId())
                .variantId(m.getVariant() != null ? m.getVariant().getId() : null)
                .sku(m.getVariant() != null ? m.getVariant().getSku() : null)
                .productName(m.getVariant() != null && m.getVariant().getProduct() != null
                        ? m.getVariant().getProduct().getName() : null)
                .sizeLabel(m.getVariant() != null ? m.getVariant().getSizeLabel() : null)
                .documentId(m.getDocumentId())
                .movementType(m.getMovementType().name())
                .quantityDelta(m.getQuantityDelta())
                .balanceBefore(m.getBalanceBefore())
                .balanceAfter(m.getBalanceAfter())
                .unitCost(m.getUnitCost())
                .reason(m.getReason())
                .actorName(m.getActorName())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
