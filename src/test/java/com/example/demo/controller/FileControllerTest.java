package com.example.demo.controller;

import com.example.demo.config.SecurityConfig;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.User;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import(SecurityConfig.class)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private FileMetadataRepository fileMetadataRepository;

    private User aliceUser;
    private User bobUser;

    @BeforeEach
    void setUp() {
        aliceUser = new User("alice", "alice@example.com");
        aliceUser.setId(1L);

        bobUser = new User("bob", "bob@example.com");
        bobUser.setId(2L);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(aliceUser));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bobUser));
    }

    private String getBasicAuthHeader(String username, String password) {
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testUploadFileAsAlice_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "some-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader("alice", "password")))
                .andExpect(status().isOk())
                .andExpect(content().string("File uploaded successfully"));

        verify(fileStorageService, times(1)).storeFile(any(), eq(aliceUser));
    }

    @Test
    void testUploadFile_Unauthenticated_Unauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "some-content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testDownloadFile_Owner_Success() throws Exception {
        FileMetadata metadata = new FileMetadata("test.txt", "/path/to/test.txt", 12L, null, aliceUser);
        metadata.setId(100L);

        when(fileMetadataRepository.findById(100L)).thenReturn(Optional.of(metadata));
        when(fileStorageService.loadFileAsDecryptedStream(metadata))
                .thenReturn(new ByteArrayInputStream("some-content".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/files/download/100")
                        .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader("alice", "password")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test.txt\""))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "12"))
                .andExpect(content().string("some-content"));
    }

    @Test
    void testDownloadFile_NonOwner_Forbidden() throws Exception {
        FileMetadata metadata = new FileMetadata("test.txt", "/path/to/test.txt", 12L, null, aliceUser);
        metadata.setId(100L);

        when(fileMetadataRepository.findById(100L)).thenReturn(Optional.of(metadata));

        // Authenticate as bob, but file belongs to alice
        mockMvc.perform(get("/api/files/download/100")
                        .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader("bob", "password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void testListFiles_Success() throws Exception {
        FileMetadata f1 = new FileMetadata("a.txt", "/path/a.txt", 10L, java.time.LocalDateTime.of(2026, 6, 3, 12, 0), aliceUser);
        f1.setId(10L);
        aliceUser.addFile(f1);

        mockMvc.perform(get("/api/files")
                        .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader("alice", "password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].originalFileName").value("a.txt"))
                .andExpect(jsonPath("$[0].fileSize").value(10));
    }

    @Test
    void testGetMe_Authenticated() throws Exception {
        mockMvc.perform(get("/api/files/me")
                        .header(HttpHeaders.AUTHORIZATION, getBasicAuthHeader("alice", "password")))
                .andExpect(status().isOk())
                .andExpect(content().string("alice"));
    }

    @Test
    void testGetMe_Anonymous() throws Exception {
        mockMvc.perform(get("/api/files/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }
}
