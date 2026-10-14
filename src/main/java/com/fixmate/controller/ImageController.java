package com.fixmate.controller;

import com.fixmate.dto.RequestImageResponse;
import com.fixmate.entity.UserRole;
import com.fixmate.service.ImageUploadService;
import jakarta.validation.constraints.Positive;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/requests/{requestId}/images")
public class ImageController {

    private final ImageUploadService imageUploadService;

    public ImageController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<RequestImageResponse>> uploadImages(
            @PathVariable @Positive Long requestId,
            @AuthenticationPrincipal UserDetails currentUser,
            @RequestParam("files") List<MultipartFile> files
    ) {
        List<RequestImageResponse> uploaded = imageUploadService.uploadImages(
                requestId, currentUser.getUsername(), getRole(currentUser), files);
        return ResponseEntity.status(201).body(uploaded);
    }

    @GetMapping
    public List<RequestImageResponse> listImages(
            @PathVariable @Positive Long requestId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        return imageUploadService.listImages(requestId, currentUser.getUsername(), getRole(currentUser));
    }

    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> getImage(
            @PathVariable @Positive Long requestId,
            @PathVariable @Positive Long imageId,
            @AuthenticationPrincipal UserDetails currentUser
    ) {
        ImageUploadService.ImageContent image = imageUploadService.getImage(
                requestId, imageId, currentUser.getUsername(), getRole(currentUser));
        return ResponseEntity.ok()
                .contentType(image.contentType())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(image.originalFilename(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(image.resource());
    }

    private UserRole getRole(UserDetails userDetails) {
        return userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .map(authority -> UserRole.valueOf(authority.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no recognized role"));
    }
}