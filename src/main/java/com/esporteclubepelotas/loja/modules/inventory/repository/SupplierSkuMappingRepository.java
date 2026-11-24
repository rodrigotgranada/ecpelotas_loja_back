package com.esporteclubepelotas.loja.modules.inventory.repository;

import com.esporteclubepelotas.loja.modules.inventory.entity.SupplierSkuMapping;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierSkuMappingRepository extends JpaRepository<SupplierSkuMapping, String> {

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    Optional<SupplierSkuMapping> findBySupplierCnpjAndSupplierProductCode(
            String supplierCnpj,
            String supplierProductCode
    );
}
