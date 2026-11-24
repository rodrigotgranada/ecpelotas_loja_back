package com.esporteclubepelotas.loja.modules.inventory.repository;

import com.esporteclubepelotas.loja.modules.inventory.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, String> {

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Page<StockMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Page<StockMovement> findAllByVariantIdOrderByCreatedAtDesc(String variantId, Pageable pageable);
}
