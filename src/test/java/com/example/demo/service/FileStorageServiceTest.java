package com.example.demo.service;

import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.User;
import com.example.demo.repository.FileMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FileStorageServiceTest {

    private FileMetadataRepository repository;
    private FileStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repository = mock(FileMetadataRepository.class);
        service = new FileStorageService(repository);
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "secretKeyString", "myTestSecretKeyForAESEncryption123!");
        service.init();
    }

    @Test
    void testStoreAndLoadFile() throws Exception {
        // Arrange
        User owner = new User("johndoe", "john@example.com");
        owner.setId(42L);

        String originalContent = "Hello World! This is a secure file.";
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "testfile.txt",
                "text/plain",
                originalContent.getBytes(StandardCharsets.UTF_8)
        );

        when(repository.save(any(FileMetadata.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.storeFile(multipartFile, owner);

        // Assert
        // 1. Verify repository.save was called and metadata fields are correct
        ArgumentCaptor<FileMetadata> metadataCaptor = ArgumentCaptor.forClass(FileMetadata.class);
        verify(repository, times(1)).save(metadataCaptor.capture());

        FileMetadata savedMetadata = metadataCaptor.getValue();
        assertNotNull(savedMetadata);
        assertEquals("testfile.txt", savedMetadata.getOriginalFileName());
        assertEquals(multipartFile.getSize(), savedMetadata.getFileSize());
        assertEquals(owner, savedMetadata.getUser());

        // 2. Verify file is encrypted on disk
        Path storedFilePath = Paths.get(savedMetadata.getStoragePath());
        assertTrue(Files.exists(storedFilePath));

        byte[] rawDiskBytes = Files.readAllBytes(storedFilePath);
        String diskContent = new String(rawDiskBytes, StandardCharsets.UTF_8);
        assertNotEquals(originalContent, diskContent);

        // 3. Verify we can decrypt it back using loadFileAsDecryptedStream
        try (InputStream decryptedStream = service.loadFileAsDecryptedStream(savedMetadata)) {
            byte[] decryptedBytes = decryptedStream.readAllBytes();
            String decryptedContent = new String(decryptedBytes, StandardCharsets.UTF_8);
            assertEquals(originalContent, decryptedContent);
        }
    }
}
