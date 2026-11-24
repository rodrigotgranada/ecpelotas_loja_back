package com.esporteclubepelotas.loja.common.storage;

import com.esporteclubepelotas.loja.common.exception.BusinessException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.StorageClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class FirebaseStorageService implements StorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "application/pdf"
    );

    @Value("${firebase.credentials-base64:}")
    private String credentialsBase64;

    @Value("${firebase.storage-bucket:}")
    private String storageBucket;

    private boolean initialized = false;

    @PostConstruct
    public void init() {
        if (credentialsBase64 == null || credentialsBase64.isBlank()) {
            log.warn("⚠️ Firebase credentials não fornecidas. Upload funcionará em modo fallback/mock.");
            return;
        }
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String normalized = credentialsBase64.replaceAll("\\s+", "");
                byte[] decoded = Base64.getDecoder().decode(normalized);
                InputStream serviceAccount = new ByteArrayInputStream(decoded);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setStorageBucket(storageBucket)
                        .build();

                FirebaseApp.initializeApp(options);
            }
            this.initialized = true;
            log.info("✅ Firebase Storage inicializado para bucket: {}", storageBucket);
        } catch (Exception e) {
            log.error("❌ Falha ao inicializar Firebase Storage: {}", e.getMessage(), e);
        }
    }

    @Override
    public String uploadFile(byte[] bytes, String fileName, String folder, String contentType) {
        String normalizedMime = contentType != null ? contentType.trim().toLowerCase(Locale.ROOT) : "";
        if ("image/jpg".equals(normalizedMime)) {
            normalizedMime = "image/jpeg";
        }
        if (!ALLOWED_CONTENT_TYPES.contains(normalizedMime)) {
            throw new BusinessException("Tipo de arquivo não permitido para armazenamento: " + normalizedMime);
        }

        String safeName = (fileName != null ? fileName : "media").replaceAll("[^a-zA-Z0-9._-]", "_");
        String cleanFolder = folder != null && !folder.isBlank()
                ? folder.replace("..", "").replaceAll("^/|/$", "") + "/"
                : "";
        String fullPath = cleanFolder + UUID.randomUUID() + "-" + safeName;

        if (!initialized) {
            return "data:" + normalizedMime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }

        try {
            Bucket bucket = StorageClient.getInstance().bucket();
            String token = UUID.randomUUID().toString();
            BlobInfo blobInfo = BlobInfo.newBuilder(bucket.getName(), fullPath)
                    .setContentType(normalizedMime)
                    .setMetadata(Collections.singletonMap("firebaseStorageDownloadTokens", token))
                    .build();
            bucket.getStorage().create(blobInfo, bytes);
            String encodedPath = URLEncoder.encode(fullPath, StandardCharsets.UTF_8).replace("+", "%20");
            return "https://firebasestorage.googleapis.com/v0/b/" + bucket.getName() + "/o/" + encodedPath
                    + "?alt=media&token=" + token;
        } catch (Exception e) {
            log.error("Erro no upload Firebase: {}", e.getMessage(), e);
            throw new BusinessException("Falha ao enviar arquivo para o armazenamento.");
        }
    }
}
