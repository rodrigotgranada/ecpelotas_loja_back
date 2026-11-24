package com.esporteclubepelotas.loja.common.storage;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class MediaUploadSanitizer {

    public record ValidatedMedia(byte[] bytes, String extension, String contentType) {}

    private MediaUploadSanitizer() {
    }

    public static String uploadImageAndVerify(MultipartFile file, String folder, String prefix, StorageService storageService) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Nenhum arquivo de imagem válido foi enviado.");
        }
        try {
            ValidatedMedia validated = detectImageMagicBytes(file.getBytes());
            if (validated == null) {
                throw new BusinessException("Formato inválido. Utilize apenas PNG, JPG, WEBP ou GIF.");
            }
            String fileName = prefix + "-" + UUID.randomUUID() + "." + validated.extension();
            String url = storageService.uploadFile(validated.bytes(), fileName, folder, validated.contentType());
            if (url == null || url.isBlank() || isBlobUrl(url)) {
                throw new BusinessException("Falha ao confirmar URL da imagem enviada.");
            }
            return url;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("Falha ao processar imagem enviada.");
        }
    }

    public static String sanitizeNullableUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty() || isBlobUrl(trimmed)) return null;
        return trimmed;
    }

    public static boolean isBlobUrl(String url) {
        return url != null && url.trim().toLowerCase().startsWith("blob:");
    }

    public static List<String> sanitizeUrlList(List<String> urls) {
        List<String> clean = new ArrayList<>();
        if (urls == null) return clean;
        for (String url : urls) {
            String sanitized = sanitizeNullableUrl(url);
            if (sanitized != null) clean.add(sanitized);
        }
        return clean;
    }

    private static ValidatedMedia detectImageMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return null;
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return new ValidatedMedia(bytes, "png", "image/png");
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return new ValidatedMedia(bytes, "jpg", "image/jpeg");
        }
        if (bytes.length >= 6) {
            String h6 = new String(Arrays.copyOfRange(bytes, 0, 6), StandardCharsets.US_ASCII);
            if ("GIF87a".equals(h6) || "GIF89a".equals(h6)) {
                return new ValidatedMedia(bytes, "gif", "image/gif");
            }
        }
        if (bytes.length >= 12) {
            String riff = new String(Arrays.copyOfRange(bytes, 0, 4), StandardCharsets.US_ASCII);
            String webp = new String(Arrays.copyOfRange(bytes, 8, 12), StandardCharsets.US_ASCII);
            if ("RIFF".equals(riff) && "WEBP".equals(webp)) {
                return new ValidatedMedia(bytes, "webp", "image/webp");
            }
        }
        return null;
    }
}
