package com.esporteclubepelotas.loja.modules.inventory.service;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.inventory.dto.StockMovementDto;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockMovement;
import com.esporteclubepelotas.loja.modules.inventory.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StockLedgerService {

    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public PageResponseDto<StockMovementDto> listMovements(String variantId, int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        Page<StockMovement> result = (variantId != null && !variantId.isBlank())
                ? stockMovementRepository.findAllByVariantIdOrderByCreatedAtDesc(variantId.trim(), PageRequest.of(safePage - 1, safeLimit))
                : stockMovementRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage - 1, safeLimit));

        List<StockMovementDto> items = result.getContent().stream()
                .map(StockMovementDto::fromEntity)
                .toList();

        return PageResponseDto.<StockMovementDto>builder()
                .data(items)
                .total(result.getTotalElements())
                .page(safePage)
                .limit(safeLimit)
                .build();
    }

    public StockMovement appendMovement(
            ProductVariant lockedVariant,
            String documentId,
            String orderId,
            StockMovement.MovementType type,
            int quantityDelta,
            BigDecimal unitCost,
            String reason,
            String actorId,
            String actorName
    ) {
        int before = lockedVariant.getPhysicalStock();
        int after = before + quantityDelta;
        if (after < 0) {
            throw new BusinessException(
                    "Saldo insuficiente para a variação " + lockedVariant.getSku()
                            + " (atual: " + before + ", delta: " + quantityDelta + ")."
            );
        }

        lockedVariant.setPhysicalStock(after);
        StockMovement movement = StockMovement.builder()
                .variant(lockedVariant)
                .documentId(documentId)
                .orderId(orderId)
                .movementType(type)
                .quantityDelta(quantityDelta)
                .balanceBefore(before)
                .balanceAfter(after)
                .unitCost(unitCost)
                .reason(reason)
                .actorUserId(actorId)
                .actorName(actorName)
                .build();
        return stockMovementRepository.save(movement);
    }
}
