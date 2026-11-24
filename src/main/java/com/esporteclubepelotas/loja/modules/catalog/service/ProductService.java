package com.esporteclubepelotas.loja.modules.catalog.service;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.storage.MediaUploadSanitizer;
import com.esporteclubepelotas.loja.modules.catalog.dto.ProductUpsertDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.VariantRequestDto;
import com.esporteclubepelotas.loja.modules.catalog.entity.Product;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductCategory;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductCategoryRepository;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductRepository;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public PageResponseDto<ProductUpsertDto.ProductResponseDto> listProducts(
            boolean activeOnly, String categoryId, String search, int page, int limit, boolean includeCost
    ) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        String cleanCat = (categoryId != null && !categoryId.isBlank()) ? categoryId.trim() : null;
        String cleanSearch = (search != null && !search.isBlank()) ? search.trim() : null;

        Page<String> idPage = productRepository.findFilteredProductIds(
                activeOnly,
                cleanCat,
                cleanSearch,
                PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "created_at"))
        );

        List<String> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return PageResponseDto.<ProductUpsertDto.ProductResponseDto>builder()
                    .data(List.of())
                    .total(0)
                    .page(safePage)
                    .limit(safeLimit)
                    .build();
        }

        Map<String, Product> productMap = productRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<ProductUpsertDto.ProductResponseDto> items = ids.stream()
                .map(productMap::get)
                .filter(Objects::nonNull)
                .map(p -> ProductUpsertDto.ProductResponseDto.fromEntity(p, includeCost))
                .toList();

        return PageResponseDto.<ProductUpsertDto.ProductResponseDto>builder()
                .data(items)
                .total(idPage.getTotalElements())
                .page(safePage)
                .limit(safeLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductUpsertDto.ProductResponseDto getPublicBySlug(String slug) {
        Product product = productRepository.findBySlugAndActiveTrue(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
        return ProductUpsertDto.ProductResponseDto.fromEntity(product, false);
    }

    @Transactional(readOnly = true)
    public ProductUpsertDto.ProductResponseDto getAdminById(String id) {
        Product product = productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));
        return ProductUpsertDto.ProductResponseDto.fromEntity(product, true);
    }

    @Transactional(readOnly = true)
    public List<VariantRequestDto.VariantResponseDto> searchVariants(String query) {
        String clean = query != null ? query.trim() : "";
        return variantRepository.searchActiveVariants(clean).stream()
                .limit(30)
                .map(VariantRequestDto.VariantResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public ProductUpsertDto.ProductResponseDto create(ProductUpsertDto dto) {
        String baseSlug = CategoryService.toSlug(
                dto.getSlug() != null && !dto.getSlug().isBlank() ? dto.getSlug() : dto.getName()
        );
        String uniqueSlug = resolveUniqueSlug(baseSlug, null);

        ProductCategory category = resolveCategory(dto.getCategoryId());
        String cover = MediaUploadSanitizer.sanitizeNullableUrl(dto.getCoverImageUrl());
        List<String> gallery = MediaUploadSanitizer.sanitizeUrlList(dto.getGalleryUrls());

        Product product = Product.builder()
                .category(category)
                .name(dto.getName().trim())
                .slug(uniqueSlug)
                .description(dto.getDescription())
                .brand(dto.getBrand() != null && !dto.getBrand().isBlank()
                        ? dto.getBrand().trim()
                        : "Alcateia / E.C. Pelotas")
                .coverImageUrl(cover)
                .galleryUrls(gallery)
                .featured(Boolean.TRUE.equals(dto.getFeatured()))
                .active(dto.getActive() == null || dto.getActive())
                .variants(new ArrayList<>())
                .build();

        syncVariants(product, dto.getVariants());
        Product saved = productRepository.save(product);
        return ProductUpsertDto.ProductResponseDto.fromEntity(saved, true);
    }

    @Transactional
    public ProductUpsertDto.ProductResponseDto update(String id, ProductUpsertDto dto) {
        Product product = productRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado."));

        String requestedSlug = dto.getSlug() != null && !dto.getSlug().isBlank()
                ? CategoryService.toSlug(dto.getSlug())
                : product.getSlug();
        product.setSlug(resolveUniqueSlug(requestedSlug, product.getId()));
        product.setCategory(resolveCategory(dto.getCategoryId()));
        product.setName(dto.getName().trim());
        product.setDescription(dto.getDescription());
        if (dto.getBrand() != null && !dto.getBrand().isBlank()) {
            product.setBrand(dto.getBrand().trim());
        }
        if (dto.getCoverImageUrl() != null && !MediaUploadSanitizer.isBlobUrl(dto.getCoverImageUrl())) {
            product.setCoverImageUrl(MediaUploadSanitizer.sanitizeNullableUrl(dto.getCoverImageUrl()));
        }
        if (dto.getGalleryUrls() != null) {
            product.setGalleryUrls(MediaUploadSanitizer.sanitizeUrlList(dto.getGalleryUrls()));
        }
        if (dto.getFeatured() != null) {
            product.setFeatured(dto.getFeatured());
        }
        if (dto.getActive() != null) {
            product.setActive(dto.getActive());
        }

        syncVariants(product, dto.getVariants());
        Product saved = productRepository.save(product);
        return ProductUpsertDto.ProductResponseDto.fromEntity(saved, true);
    }

    private void syncVariants(Product product, List<VariantRequestDto> incoming) {
        Map<String, ProductVariant> existingById = product.getVariants().stream()
                .filter(v -> v.getId() != null)
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        Set<String> seenSkus = new HashSet<>();
        Set<String> seenEans = new HashSet<>();
        List<ProductVariant> updatedList = new ArrayList<>();

        for (VariantRequestDto req : incoming) {
            String normalizedSku = req.getSku().trim().toUpperCase(Locale.ROOT);
            if (!seenSkus.add(normalizedSku)) {
                throw new BusinessException("SKU duplicado no mesmo produto: " + normalizedSku);
            }

            String normalizedEan = req.getEanBarcode() != null && !req.getEanBarcode().isBlank()
                    ? req.getEanBarcode().trim()
                    : null;
            if (normalizedEan != null && !seenEans.add(normalizedEan)) {
                throw new BusinessException("Código de barras EAN duplicado no mesmo produto: " + normalizedEan);
            }

            ProductVariant target = (req.getId() != null && existingById.containsKey(req.getId()))
                    ? existingById.get(req.getId())
                    : ProductVariant.builder().product(product).physicalStock(0).reservedStock(0).build();

            validateUniqueGlobalSkuAndEan(normalizedSku, normalizedEan, target.getId());

            target.setProduct(product);
            target.setSku(normalizedSku);
            target.setEanBarcode(normalizedEan);
            target.setSizeLabel(req.getSizeLabel().trim());
            target.setColorLabel(req.getColorLabel() != null ? req.getColorLabel().trim() : null);
            target.setCostPrice(req.getCostPrice());
            target.setSalePrice(req.getSalePrice());
            target.setMemberPrice(req.getMemberPrice());
            target.setMinStock(req.getMinStock() != null ? req.getMinStock() : 2);
            target.setActive(req.getActive() == null || req.getActive());
            updatedList.add(target);
        }

        // Mantém variações antigas que já possuem estoque/histórico como inativas se removidas da lista?
        // Para segurança de Kardex/FK, não remove variações que já tenham saldo físico > 0:
        Set<String> keptIds = updatedList.stream()
                .map(ProductVariant::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (ProductVariant oldVar : existingById.values()) {
            if (!keptIds.contains(oldVar.getId()) && oldVar.getPhysicalStock() > 0) {
                throw new BusinessException(
                        "A variação " + oldVar.getSku() + " ainda possui estoque físico ("
                                + oldVar.getPhysicalStock() + "). Zere o estoque antes de removê-la."
                );
            }
        }

        product.getVariants().clear();
        product.getVariants().addAll(updatedList);
    }

    private void validateUniqueGlobalSkuAndEan(String sku, String ean, String currentVariantId) {
        variantRepository.findBySkuIgnoreCase(sku).ifPresent(found -> {
            if (!Objects.equals(found.getId(), currentVariantId)) {
                throw new BusinessException("Já existe outra variação cadastrada com o SKU: " + sku);
            }
        });
        if (ean != null) {
            variantRepository.findByEanBarcode(ean).ifPresent(found -> {
                if (!Objects.equals(found.getId(), currentVariantId)) {
                    throw new BusinessException("Já existe outra variação cadastrada com o EAN: " + ean);
                }
            });
        }
    }

    private ProductCategory resolveCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return categoryRepository.findById(categoryId.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria selecionada não encontrada."));
    }

    private String resolveUniqueSlug(String baseSlug, String currentId) {
        if (currentId != null) {
            Product current = productRepository.findById(currentId).orElse(null);
            if (current != null && baseSlug.equals(current.getSlug())) {
                return baseSlug;
            }
        }
        String candidate = baseSlug;
        int counter = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = baseSlug + "-" + counter++;
        }
        return candidate;
    }
}
