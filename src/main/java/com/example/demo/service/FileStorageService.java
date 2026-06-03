package com.example.demo.service;

import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.User;
import com.example.demo.repository.FileMetadataRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class FileStorageService {

    private final FileMetadataRepository fileMetadataRepository;

    @Value("${file.upload.dir:uploads}")
    private String uploadDir;

    @Value("${file.encryption.secret}")
    private String secretKeyString;

    private SecretKeySpec secretKey;

    public FileStorageService(FileMetadataRepository fileMetadataRepository) {
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(secretKeyString.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to initialize AES key derivation", e);
        }
    }

    @Transactional
    public void storeFile(MultipartFile file, User owner) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store an empty file");
        }
        if (owner == null || owner.getId() == null) {
            throw new IllegalArgumentException("File must have a valid registered owner");
        }

        // 1. Generate unique UUID
        String fileUuid = UUID.randomUUID().toString();

        // 2. Create user-isolated storage path
        Path userUploadDir = Paths.get(uploadDir).resolve("user_" + owner.getId()).normalize();
        try {
            Files.createDirectories(userUploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory for user: " + owner.getId(), e);
        }

        Path targetPath = userUploadDir.resolve(fileUuid).normalize();

        // 3. Encrypt file input stream using AES and save to disk
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);

            try (OutputStream fileOut = Files.newOutputStream(targetPath);
                 CipherOutputStream cipherOut = new CipherOutputStream(fileOut, cipher);
                 InputStream fileIn = file.getInputStream()) {

                // Write IV first so we can read it back during decryption
                fileOut.write(iv);

                // Copy stream content
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    cipherOut.write(buffer, 0, bytesRead);
                }
            }
        } catch (Exception e) {
            // Clean up file if failed during writing
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ioEx) {
                // Suppress clean up failure and throw original
            }
            throw new RuntimeException("Failed to securely store file on disk", e);
        }

        // 4. Save metadata record to PostgreSQL database
        FileMetadata metadata = new FileMetadata(
                file.getOriginalFilename(),
                targetPath.toAbsolutePath().toString(),
                file.getSize(),
                LocalDateTime.now(),
                owner
        );

        try {
            fileMetadataRepository.save(metadata);
        } catch (Exception e) {
            // Roll back file on disk if metadata save fails to prevent orphan files
            try {
                Files.deleteIfExists(targetPath);
            } catch (IOException ioEx) {
                // Suppress clean up failure
            }
            throw new RuntimeException("Failed to save file metadata in database", e);
        }
    }

    public InputStream loadFileAsDecryptedStream(FileMetadata metadata) {
        Path targetPath = Paths.get(metadata.getStoragePath());
        if (!Files.exists(targetPath)) {
            throw new RuntimeException("Secure file not found on disk");
        }

        try {
            InputStream fileIn = Files.newInputStream(targetPath);

            // Read the prepended 16-byte IV
            byte[] iv = new byte[16];
            int bytesRead = fileIn.read(iv);
            if (bytesRead != 16) {
                fileIn.close();
                throw new RuntimeException("Secure file header is corrupted or incomplete");
            }

            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);

            return new CipherInputStream(fileIn, cipher);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt and load file", e);
        }
    }
}
