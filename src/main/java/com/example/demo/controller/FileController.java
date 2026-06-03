package com.example.demo.controller;

import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.User;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileStorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final FileMetadataRepository fileMetadataRepository;

    public FileController(FileStorageService fileStorageService,
                          UserRepository userRepository,
                          FileMetadataRepository fileMetadataRepository) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated");
        }

        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User record not found"));

        try {
            fileStorageService.storeFile(file, currentUser);
            return ResponseEntity.ok("File uploaded successfully");
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file", e);
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> downloadFile(@PathVariable("id") Long id, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated");
        }

        // Fetch file metadata
        FileMetadata metadata = fileMetadataRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found"));

        // Fetch currently authenticated user
        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User record not found"));

        // Enforce ownership: File owner ID must match current user ID
        if (!metadata.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You do not own this file");
        }

        try {
            // Decrypt on the fly and stream back
            InputStream decryptedStream = fileStorageService.loadFileAsDecryptedStream(metadata);
            InputStreamResource resource = new InputStreamResource(decryptedStream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getOriginalFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(metadata.getFileSize())
                    .body(resource);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to decrypt and stream file", e);
        }
    }

    @GetMapping
    public ResponseEntity<List<FileResponse>> listFiles(Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated");
        }

        User currentUser = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User record not found"));

        List<FileResponse> responses = currentUser.getFiles().stream()
                .map(file -> new FileResponse(
                        file.getId(),
                        file.getOriginalFileName(),
                        file.getFileSize(),
                        file.getUploadTime().toString()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @GetMapping("/me")
    public ResponseEntity<String> getMe(Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok("anonymous");
        }
        return ResponseEntity.ok(principal.getName());
    }

    public record FileResponse(Long id, String originalFileName, long fileSize, String uploadTime) {}
}
