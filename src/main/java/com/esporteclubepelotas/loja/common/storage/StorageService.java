package com.esporteclubepelotas.loja.common.storage;

public interface StorageService {
    String uploadFile(byte[] bytes, String fileName, String folder, String contentType);
}
