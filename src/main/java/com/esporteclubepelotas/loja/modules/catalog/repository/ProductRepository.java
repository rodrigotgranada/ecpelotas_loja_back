package com.esporteclubepelotas.loja.modules.catalog.repository;

import com.esporteclubepelotas.loja.modules.catalog.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<Product> findWithDetailsById(String id);

    @EntityGraph(attributePaths = {"category", "variants"})
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    @Query(value = "SELECT p.id FROM products p " +
            "WHERE (:activeOnly = false OR p.active = true) " +
            "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
            "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND " +
            "(LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(COALESCE(v.ean_barcode, '')) LIKE LOWER(CONCAT('%', :search, '%')))))",
            countQuery = "SELECT COUNT(p.id) FROM products p " +
                    "WHERE (:activeOnly = false OR p.active = true) " +
                    "AND (:categoryId IS NULL OR p.category_id = :categoryId) " +
                    "AND (:search IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
                    "OR EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND " +
                    "(LOWER(v.sku) LIKE LOWER(CONCAT('%', :search, '%')) " +
                    "OR LOWER(COALESCE(v.ean_barcode, '')) LIKE LOWER(CONCAT('%', :search, '%')))))",
            nativeQuery = true)
    Page<String> findFilteredProductIds(
            @Param("activeOnly") boolean activeOnly,
            @Param("categoryId") String categoryId,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"category", "variants"})
    java.util.List<Product> findAllByIdIn(java.util.Collection<String> ids);
}
