package com.esporteclubepelotas.loja.modules.inventory.service;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.esporteclubepelotas.loja.common.exception.ResourceNotFoundException;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.catalog.dto.ProductUpsertDto;
import com.esporteclubepelotas.loja.modules.catalog.dto.VariantRequestDto;
import com.esporteclubepelotas.loja.modules.catalog.entity.ProductVariant;
import com.esporteclubepelotas.loja.modules.catalog.repository.ProductVariantRepository;
import com.esporteclubepelotas.loja.modules.catalog.service.ProductService;
import com.esporteclubepelotas.loja.modules.inventory.dto.CreateProductFromInvoiceItemDto;
import com.esporteclubepelotas.loja.modules.inventory.dto.ManualStockAdjustmentDto;
import com.esporteclubepelotas.loja.modules.inventory.dto.StockDocumentDto;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockDocument;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockDocumentItem;
import com.esporteclubepelotas.loja.modules.inventory.entity.StockMovement;
import com.esporteclubepelotas.loja.modules.inventory.entity.SupplierSkuMapping;
import com.esporteclubepelotas.loja.modules.inventory.repository.StockDocumentRepository;
import com.esporteclubepelotas.loja.modules.inventory.repository.SupplierSkuMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockDocumentService {

    private final NfeXmlParserService xmlParserService;
    private final StockDocumentRepository stockDocumentRepository;
    private final SupplierSkuMappingRepository mappingRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductService productService;
    private final StockLedgerService stockLedgerService;

    @Transactional(readOnly = true)
    public PageResponseDto<StockDocumentDto> listDocuments(int page, int limit) {
        int safePage = Math.max(1, page);
        int safeLimit = Math.min(100, Math.max(1, limit));
        Page<StockDocument> result = stockDocumentRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage - 1, safeLimit)
        );
        return PageResponseDto.<StockDocumentDto>builder()
                .data(result.getContent().stream().map(d -> StockDocumentDto.fromEntity(d, false)).toList())
                .total(result.getTotalElements())
                .page(safePage)
                .limit(safeLimit)
                .build();
    }

    @Transactional(readOnly = true)
    public StockDocumentDto getDocument(String id) {
        StockDocument doc = stockDocumentRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota/documento de estoque não encontrado."));
        return StockDocumentDto.fromEntity(doc, true);
    }

    @Transactional
    public StockDocumentDto importNfeXml(MultipartFile file, UserPrincipal actor) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Envie um arquivo XML de NF-e válido.");
        }
        NfeXmlParserService.ParsedNfe parsed;
        try {
            parsed = xmlParserService.parse(file.getBytes());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Falha ao ler arquivo XML.");
        }

        if (parsed.accessKey44() != null && stockDocumentRepository.existsByAccessKey44(parsed.accessKey44())) {
            throw new BusinessException("Esta NF-e (chave " + parsed.accessKey44() + ") já foi importada anteriormente.");
        }

        StockDocument doc = StockDocument.builder()
                .docType(StockDocument.DocType.INBOUND_INVOICE)
                .status(StockDocument.Status.DRAFT)
                .invoiceNumber(parsed.invoiceNumber())
                .invoiceSeries(parsed.invoiceSeries())
                .accessKey44(parsed.accessKey44())
                .supplierCnpj(parsed.supplierCnpj())
                .supplierName(parsed.supplierName())
                .totalCost(parsed.totalCost())
                .createdByUserId(actor != null ? actor.getId() : null)
                .items(new ArrayList<>())
                .build();

        for (NfeXmlParserService.ParsedNfeItem item : parsed.items()) {
            ProductVariant matchedVariant = resolveAutoMatchedVariant(
                    parsed.supplierCnpj(),
                    item.supplierProductCode(),
                    item.eanBarcode()
            );
            doc.getItems().add(StockDocumentItem.builder()
                    .document(doc)
                    .lineNumber(item.lineNumber())
                    .supplierProductCode(item.supplierProductCode())
                    .eanBarcode(item.eanBarcode())
                    .supplierProductName(item.productName())
                    .ncm(item.ncm())
                    .quantity(item.quantity())
                    .unitCost(item.unitCost())
                    .totalCost(item.totalCost())
                    .variant(matchedVariant)
                    .build());
        }

        StockDocument saved = stockDocumentRepository.save(doc);
        return StockDocumentDto.fromEntity(saved, true);
    }

    @Transactional
    public StockDocumentDto linkItemToVariant(String docId, String itemId, String variantId) {
        StockDocument doc = findDraftDocument(docId);
        StockDocumentItem item = findItem(doc, itemId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variação/SKU não encontrada."));

        item.setVariant(variant);
        saveSupplierMapping(doc.getSupplierCnpj(), item.getSupplierProductCode(), variant);
        return StockDocumentDto.fromEntity(stockDocumentRepository.save(doc), true);
    }

    @Transactional
    public StockDocumentDto createProductFromInvoiceItem(
            String docId,
            String itemId,
            CreateProductFromInvoiceItemDto dto
    ) {
        StockDocument doc = findDraftDocument(docId);
        StockDocumentItem item = findItem(doc, itemId);

        String ean = (dto.getEanBarcode() != null && !dto.getEanBarcode().isBlank())
                ? dto.getEanBarcode().trim()
                : item.getEanBarcode();

        ProductUpsertDto productDto = ProductUpsertDto.builder()
                .name(dto.getProductName().trim())
                .categoryId(dto.getCategoryId())
                .brand(dto.getBrand())
                .active(true)
                .variants(List.of(VariantRequestDto.builder()
                        .sku(dto.getSku().trim())
                        .eanBarcode(ean)
                        .sizeLabel(dto.getSizeLabel().trim())
                        .colorLabel(dto.getColorLabel())
                        .costPrice(item.getUnitCost())
                        .salePrice(dto.getSalePrice())
                        .memberPrice(dto.getMemberPrice())
                        .minStock(2)
                        .active(true)
                        .build()))
                .build();

        ProductUpsertDto.ProductResponseDto created = productService.create(productDto);
        String createdVariantId = created.getVariants().get(0).getId();
        ProductVariant variant = variantRepository.findById(createdVariantId)
                .orElseThrow(() -> new ResourceNotFoundException("Erro ao localizar variação criada."));

        item.setVariant(variant);
        saveSupplierMapping(doc.getSupplierCnpj(), item.getSupplierProductCode(), variant);
        return StockDocumentDto.fromEntity(stockDocumentRepository.save(doc), true);
    }

    @Transactional
    public StockDocumentDto confirmDocument(String docId, UserPrincipal actor) {
        StockDocument doc = findDraftDocument(docId);
        if (doc.getItems().isEmpty()) {
            throw new BusinessException("Documento sem itens para confirmar.");
        }
        boolean anyUnlinked = doc.getItems().stream().anyMatch(i -> i.getVariant() == null);
        if (anyUnlinked) {
            throw new BusinessException("Vincule ou cadastre todos os itens da nota antes de confirmar a entrada no estoque.");
        }

        List<String> sortedVariantIds = doc.getItems().stream()
                .map(i -> i.getVariant().getId())
                .distinct()
                .sorted()
                .toList();

        Map<String, ProductVariant> lockedVariants = variantRepository.findAllByIdInForUpdateOrdered(sortedVariantIds)
                .stream()
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));

        String invoiceLabel = doc.getInvoiceNumber() != null
                ? "Entrada NF-e #" + doc.getInvoiceNumber()
                : "Entrada de Nota";

        for (StockDocumentItem item : doc.getItems()) {
            ProductVariant locked = lockedVariants.get(item.getVariant().getId());
            if (locked == null) {
                throw new ResourceNotFoundException("Variação vinculada não encontrada durante o fechamento.");
            }
            if (item.getUnitCost() != null && item.getUnitCost().compareTo(BigDecimal.ZERO) > 0) {
                locked.setCostPrice(item.getUnitCost());
            }
            stockLedgerService.appendMovement(
                    locked,
                    doc.getId(),
                    null,
                    StockMovement.MovementType.ENTRY_INVOICE,
                    item.getQuantity(),
                    item.getUnitCost(),
                    invoiceLabel + " — " + item.getSupplierProductName(),
                    actor != null ? actor.getId() : null,
                    actor != null ? actor.getName() : "Sistema"
            );
            variantRepository.save(locked);
        }

        doc.setStatus(StockDocument.Status.POSTED);
        doc.setPostedAt(Instant.now());
        return StockDocumentDto.fromEntity(stockDocumentRepository.save(doc), true);
    }

    @Transactional
    public StockDocumentDto manualAdjustment(ManualStockAdjustmentDto dto, UserPrincipal actor) {
        if (dto.getQuantityDelta() == null || dto.getQuantityDelta() == 0) {
            throw new BusinessException("A variação de quantidade não pode ser zero.");
        }

        ProductVariant locked = variantRepository.findByIdForUpdate(dto.getVariantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variação/SKU não encontrada."));

        StockDocument.DocType docType = dto.getQuantityDelta() > 0
                ? StockDocument.DocType.MANUAL_ENTRY
                : StockDocument.DocType.INVENTORY_ADJUSTMENT;
        StockMovement.MovementType movType = dto.getQuantityDelta() > 0
                ? StockMovement.MovementType.MANUAL_IN
                : StockMovement.MovementType.MANUAL_OUT;

        BigDecimal unitCost = locked.getCostPrice() != null ? locked.getCostPrice() : BigDecimal.ZERO;
        BigDecimal total = unitCost.multiply(BigDecimal.valueOf(Math.abs(dto.getQuantityDelta())));

        StockDocument doc = StockDocument.builder()
                .docType(docType)
                .status(StockDocument.Status.POSTED)
                .notes(dto.getReason().trim())
                .totalCost(total)
                .createdByUserId(actor != null ? actor.getId() : null)
                .postedAt(Instant.now())
                .items(new ArrayList<>())
                .build();

        doc.getItems().add(StockDocumentItem.builder()
                .document(doc)
                .lineNumber(1)
                .supplierProductCode(locked.getSku())
                .eanBarcode(locked.getEanBarcode())
                .supplierProductName(locked.getProduct().getName() + " (" + locked.getSizeLabel() + ")")
                .quantity(Math.abs(dto.getQuantityDelta()))
                .unitCost(unitCost)
                .totalCost(total)
                .variant(locked)
                .build());

        StockDocument savedDoc = stockDocumentRepository.save(doc);
        stockLedgerService.appendMovement(
                locked,
                savedDoc.getId(),
                null,
                movType,
                dto.getQuantityDelta(),
                unitCost,
                dto.getReason().trim(),
                actor != null ? actor.getId() : null,
                actor != null ? actor.getName() : "Sistema"
        );
        variantRepository.save(locked);
        return StockDocumentDto.fromEntity(savedDoc, true);
    }

    private ProductVariant resolveAutoMatchedVariant(String supplierCnpj, String supplierCode, String ean) {
        if (supplierCnpj != null && !supplierCnpj.isBlank()
                && supplierCode != null && !supplierCode.isBlank()) {
            Optional<SupplierSkuMapping> mapping =
                    mappingRepository.findBySupplierCnpjAndSupplierProductCode(supplierCnpj, supplierCode);
            if (mapping.isPresent()) {
                return mapping.get().getVariant();
            }
        }
        if (ean != null && !ean.isBlank()) {
            return variantRepository.findByEanBarcode(ean).orElse(null);
        }
        return null;
    }

    private void saveSupplierMapping(String cnpj, String code, ProductVariant variant) {
        if (cnpj == null || cnpj.isBlank() || code == null || code.isBlank()) return;
        SupplierSkuMapping mapping = mappingRepository.findBySupplierCnpjAndSupplierProductCode(cnpj, code)
                .orElseGet(() -> SupplierSkuMapping.builder()
                        .supplierCnpj(cnpj)
                        .supplierProductCode(code)
                        .build());
        mapping.setVariant(variant);
        mappingRepository.save(mapping);
    }

    private StockDocument findDraftDocument(String docId) {
        StockDocument doc = stockDocumentRepository.findWithItemsById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Nota não encontrada."));
        if (doc.getStatus() != StockDocument.Status.DRAFT) {
            throw new BusinessException("Esta nota já foi confirmada ou cancelada e não pode ser alterada.");
        }
        return doc;
    }

    private StockDocumentItem findItem(StockDocument doc, String itemId) {
        return doc.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Item da nota não encontrado."));
    }
}
