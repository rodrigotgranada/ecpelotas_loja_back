package com.esporteclubepelotas.loja.modules.catalog.dto;

import com.esporteclubepelotas.loja.modules.catalog.entity.ProductCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryDto {
    private String id;
    @NotBlank(message = "Nome da categoria é obrigatório")
    @Size(max = 100)
    private String name;
    private String slug;
    private String description;
    private int sortOrder;
    private boolean active;

    public static CategoryDto fromEntity(ProductCategory category) {
        return CategoryDto.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .active(category.isActive())
                .build();
    }
}
