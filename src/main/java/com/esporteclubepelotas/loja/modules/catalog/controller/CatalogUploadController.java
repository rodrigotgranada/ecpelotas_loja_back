package com.esporteclubepelotas.loja.modules.catalog.controller;

import com.esporteclubepelotas.loja.common.storage.MediaUploadSanitizer;
import com.esporteclubepelotas.loja.common.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'STORE_MANAGER', 'STOCK_CLERK')")
@Tag(name = "Store Uploads", description = "Upload seguro Media-First de fotos dos produtos")
public class CatalogUploadController {

    private final StorageService storageService;

    @PostMapping(value = "/product-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Envia imagem de produto (parâmetro canônico 'file')")
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "image", required = false) MultipartFile legacyImage
    ) {
        MultipartFile target = file != null ? file : legacyImage;
        String url = MediaUploadSanitizer.uploadImageAndVerify(target, "store/products", "product", storageService);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
