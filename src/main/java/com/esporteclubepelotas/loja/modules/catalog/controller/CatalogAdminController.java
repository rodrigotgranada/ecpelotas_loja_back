package com.esporteclubepelotas.loja.modules.catalog.controller;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.CategoryDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.ProductUpsertDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.VariantRequestDto;
import com.esporteclubepelotas.loja.modules.catalog.service.CategoryService;
import com.esporteclubepelotas.loja.modules.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK', 'SELLER')")
@Tag(name = "PDV Catalog Admin", description = "Gestão de categorias, produtos e variações (SKU/EAN)")
public class CatalogAdminController {

    private final CategoryService categoryService;
    private final ProductService productService;

    @GetMapping("/categories")
    @Operation(summary = "Lista todas as categorias para gestão")
    public ResponseEntity<List<CategoryDto>> listCategories() {
        return ResponseEntity.ok(categoryService.listAdmin());
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
    @Operation(summary = "Cria uma nova categoria")
    public ResponseEntity<CategoryDto> createCategory(@Valid @RequestBody CategoryDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(dto));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
    @Operation(summary = "Atualiza uma categoria")
    public ResponseEntity<CategoryDto> updateCategory(@PathVariable String id, @Valid @RequestBody CategoryDto dto) {
        return ResponseEntity.ok(categoryService.update(id, dto));
    }

    @GetMapping("/products")
    @Operation(summary = "Lista produtos com preços de custo e variações completas")
    public ResponseEntity<PageResponseDto<ProductUpsertDto.ProductResponseDto>> listProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "false") boolean activeOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(productService.listProducts(activeOnly, categoryId, search, page, limit, true));
    }

    @GetMapping("/products/{id}")
    @Operation(summary = "Detalha produto por ID")
    public ResponseEntity<ProductUpsertDto.ProductResponseDto> getProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.getAdminById(id));
    }

    @GetMapping("/variants/search")
    @Operation(summary = "Busca rápida de variações por SKU, EAN ou nome do produto")
    public ResponseEntity<List<VariantRequestDto.VariantResponseDto>> searchVariants(
            @RequestParam(defaultValue = "") String q
    ) {
        return ResponseEntity.ok(productService.searchVariants(q));
    }

    @PostMapping("/products")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
    @Operation(summary = "Cadastra novo produto com variações")
    public ResponseEntity<ProductUpsertDto.ProductResponseDto> createProduct(@Valid @RequestBody ProductUpsertDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(dto));
    }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
    @Operation(summary = "Atualiza produto e suas variações")
    public ResponseEntity<ProductUpsertDto.ProductResponseDto> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductUpsertDto dto
    ) {
        return ResponseEntity.ok(productService.update(id, dto));
    }
}
