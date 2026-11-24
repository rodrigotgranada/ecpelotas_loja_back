package com.esporteclubepelotas.loja.modules.catalog.repository;

import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {

    Optional<ProductVariant> findBySkuIgnoreCase(String sku);
    Optional<ProductVariant> findByEanBarcode(String eanBarcode);

    @EntityGraph(attributePaths = {"product"})
    @Query("SELECT v FROM ProductVariant v WHERE v.active = true AND " +
            "(LOWER(v.sku) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(COALESCE(v.eanBarcode, '')) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "OR LOWER(v.product.name) LIKE LOWER(CONCAT('%', :q, '%'))) " +
            "ORDER BY v.product.name ASC, v.sizeLabel ASC")
    List<ProductVariant> searchActiveVariants(@Param("q") String query);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id = :id")
    Optional<ProductVariant> findByIdForUpdate(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id IN :ids ORDER BY v.id ASC")
    List<ProductVariant> findAllByIdInForUpdateOrdered(@Param("ids") Collection<String> ids);
}
