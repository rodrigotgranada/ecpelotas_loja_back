package com.esporteclubepelotas.loja.modules.inventory.repository;

import com.esporteclubepelotas.loja.modules.inventory.entity.StockDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockDocumentRepository extends JpaRepository<StockDocument, String> {
    boolean existsByAccessKey44(String accessKey44);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<StockDocument> findWithItemsById(String id);

    Page<StockDocument> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
