package com.esporteclubepelotas.loja.modules.catalog.controller;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.CategoryDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.ProductUpsertDto;
import com.esporteclubepelotas.loja.modules.catalog.service.CategoryService;
import com.esporteclubepelotas.loja.modules.catalog.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Tag(name = "Storefront Catalog", description = "Vitrine pública de categorias e produtos da Loja Alcateia")
public class CatalogPublicController {

    private final CategoryService categoryService;
    private final ProductService productService;

    @GetMapping("/categories")
    @Operation(summary = "Lista categorias ativas da loja")
    public ResponseEntity<List<CategoryDto>> listCategories() {
        return ResponseEntity.ok(categoryService.listPublic());
    }

    @GetMapping("/products")
    @Operation(summary = "Lista produtos ativos para a vitrine pública (sem preço de custo)")
    public ResponseEntity<PageResponseDto<ProductUpsertDto.ProductResponseDto>> listProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(productService.listProducts(true, categoryId, search, page, limit, false));
    }

    @GetMapping("/products/{slug}")
    @Operation(summary = "Detalhe público de um produto pelo slug")
    public ResponseEntity<ProductUpsertDto.ProductResponseDto> getProduct(@PathVariable String slug) {
        return ResponseEntity.ok(productService.getPublicBySlug(slug));
    }
}
