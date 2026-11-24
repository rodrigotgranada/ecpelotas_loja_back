package com.esporteclubepelotas.loja.modules.catalog.dto;

import com.esporteclubepelotas.loja.modules.catalog.entity.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpsertDto {

    @NotBlank(message = "Nome do produto é obrigatório")
    private String name;

    private String slug;
    private String categoryId;
    private String description;
    private String brand;
    private String coverImageUrl;
    private List<String> galleryUrls;
    private Boolean featured;
    private Boolean active;

    @Valid
    @NotEmpty(message = "Informe pelo menos uma variação/SKU")
    private List<VariantRequestDto> variants;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductResponseDto {
        private String id;
        private String categoryId;
        private String categoryName;
        private String name;
        private String slug;
        private String description;
        private String brand;
        private String coverImageUrl;
        private List<String> galleryUrls;
        private boolean featured;
        private boolean active;
        private int totalAvailableStock;
        private List<VariantRequestDto.VariantResponseDto> variants;
        private Instant createdAt;
        private Instant updatedAt;

        public static ProductResponseDto fromEntity(Product product, boolean includeCost) {
            List<VariantRequestDto.VariantResponseDto> variantDtos = product.getVariants().stream()
                    .map(v -> {
                        VariantRequestDto.VariantResponseDto dto = VariantRequestDto.VariantResponseDto.fromEntity(v);
                        if (!includeCost) {
                            dto.setCostPrice(null);
                        }
                        return dto;
                    })
                    .toList();

            int totalAvailable = variantDtos.stream()
                    .filter(VariantRequestDto.VariantResponseDto::isActive)
                    .mapToInt(VariantRequestDto.VariantResponseDto::getAvailableStock)
                    .sum();

            return ProductResponseDto.builder()
                    .id(product.getId())
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .name(product.getName())
                    .slug(product.getSlug())
                    .description(product.getDescription())
                    .brand(product.getBrand())
                    .coverImageUrl(product.getCoverImageUrl())
                    .galleryUrls(product.getGalleryUrls())
                    .featured(product.isFeatured())
                    .active(product.isActive())
                    .totalAvailableStock(totalAvailable)
                    .variants(variantDtos)
                    .createdAt(product.getCreatedAt())
                    .updatedAt(product.getUpdatedAt())
                    .build();
        }
    }
}
