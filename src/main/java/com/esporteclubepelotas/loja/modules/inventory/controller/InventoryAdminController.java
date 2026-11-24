package com.esporteclubepelotas.loja.modules.inventory.controller;

import com.esporteclubepelotas.loja.common.dto.PageResponseDto;
import com.esporteclubepelotas.loja.common.security.CurrentUser;
import com.esporteclubepelotas.loja.common.security.UserPrincipal;
import com.esporteclubepelotas.loja.modules.inventory.dto.*;
import com.esporteclubepelotas.loja.modules.inventory.service.StockDocumentService;
import com.esporteclubepelotas.loja.modules.inventory.service.StockLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
@Tag(name = "PDV Inventory & Invoices", description = "Importação de NF-e XML, vínculo/cadastro pela nota e Kardex de estoque")
public class InventoryAdminController {

    private final StockDocumentService stockDocumentService;
    private final StockLedgerService stockLedgerService;

    @GetMapping("/invoices")
    @Operation(summary = "Lista notas de entrada e lançamentos de estoque")
    public ResponseEntity<PageResponseDto<StockDocumentDto>> listDocuments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(stockDocumentService.listDocuments(page, limit));
    }

    @GetMapping("/invoices/{id}")
    @Operation(summary = "Detalha uma nota de entrada e seus itens")
    public ResponseEntity<StockDocumentDto> getDocument(@PathVariable String id) {
        return ResponseEntity.ok(stockDocumentService.getDocument(id));
    }

    @PostMapping(value = "/invoices/import-xml", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importa XML de NF-e de entrada (parâmetro canônico 'file')")
    public ResponseEntity<StockDocumentDto> importXml(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "xml", required = false) MultipartFile legacyXml,
            @CurrentUser UserPrincipal actor
    ) {
        MultipartFile target = file != null ? file : legacyXml;
        return ResponseEntity.status(HttpStatus.CREATED).body(stockDocumentService.importNfeXml(target, actor));
    }

    @PatchMapping("/invoices/{docId}/items/{itemId}/link")
    @Operation(summary = "Vincula item da NF-e a uma variação/SKU existente")
    public ResponseEntity<StockDocumentDto> linkItem(
            @PathVariable String docId,
            @PathVariable String itemId,
            @Valid @RequestBody LinkInvoiceItemDto dto
    ) {
        return ResponseEntity.ok(stockDocumentService.linkItemToVariant(docId, itemId, dto.getVariantId()));
    }

    @PostMapping("/invoices/{docId}/items/{itemId}/create-product")
    @Operation(summary = "Cadastra novo produto + variação diretamente a partir de um item da NF-e")
    public ResponseEntity<StockDocumentDto> createProductFromItem(
            @PathVariable String docId,
            @PathVariable String itemId,
            @Valid @RequestBody CreateProductFromInvoiceItemDto dto
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockDocumentService.createProductFromInvoiceItem(docId, itemId, dto));
    }

    @PostMapping("/invoices/{docId}/confirm")
    @Operation(summary = "Efetiva entrada no estoque e registra movimentações no Kardex")
    public ResponseEntity<StockDocumentDto> confirmInvoice(
            @PathVariable String docId,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.ok(stockDocumentService.confirmDocument(docId, actor));
    }

    @PostMapping("/stock/adjust")
    @Operation(summary = "Realiza entrada manual ou ajuste avulso de estoque com motivo")
    public ResponseEntity<StockDocumentDto> manualAdjust(
            @Valid @RequestBody ManualStockAdjustmentDto dto,
            @CurrentUser UserPrincipal actor
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(stockDocumentService.manualAdjustment(dto, actor));
    }

    @GetMapping("/stock/movements")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK', 'SELLER')")
    @Operation(summary = "Consulta histórico Kardex de movimentações de estoque")
    public ResponseEntity<PageResponseDto<StockMovementDto>> listMovements(
            @RequestParam(required = false) String variantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ResponseEntity.ok(stockLedgerService.listMovements(variantId, page, limit));
    }
}
