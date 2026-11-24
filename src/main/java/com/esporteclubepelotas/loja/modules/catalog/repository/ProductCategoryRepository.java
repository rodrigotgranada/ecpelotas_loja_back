package com.esporteclubepelotas.loja.modules.catalog.repository;

import com.esporteclubepelotas.loja.modules.catalog.entity.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, String> {
    List<ProductCategory> findAllByActiveTrueOrderBySortOrderAscNameAsc();
    List<ProductCategory> findAllByOrderBySortOrderAscNameAsc();
    Optional<ProductCategory> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
