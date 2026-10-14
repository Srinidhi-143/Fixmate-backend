package com.fixmate.service;

import com.fixmate.dto.RequestImageResponse;
import com.fixmate.entity.RequestImage;
import com.fixmate.entity.ServiceRequest;
import com.fixmate.entity.UserRole;
import com.fixmate.exception.ResourceNotFoundException;
import com.fixmate.repository.RequestImageRepository;
import com.fixmate.repository.ServiceRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageUploadService {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final RequestImageRepository imageRepository;
    private final ServiceRequestRepository requestRepository;
    private final ServiceRequestService requestService;
    private final Path storageRoot;
    private final long maxFileSizeBytes;
    private final int maxFilesPerRequest;

    public ImageUploadService(
            RequestImageRepository imageRepository,
            ServiceRequestRepository requestRepository,
            ServiceRequestService requestService,
            @Value("${app.upload.directory}") String uploadDirectory,
            @Value("${app.upload.max-file-size-bytes}") long maxFileSizeBytes,
            @Value("${app.upload.max-files-per-request}") int maxFilesPerRequest
    ) {
        this.imageRepository = imageRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.storageRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxFilesPerRequest = maxFilesPerRequest;
    }

    @Transactional
    public List<RequestImageResponse> uploadImages(
            Long requestId,
            String userEmail,
            UserRole role,
            List<MultipartFile> files
    ) {
        if (role != UserRole.USER) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the request owner can upload images");
        }
        requestService.getRequest(requestId, userEmail, role);
        ServiceRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Service request not found: " + requestId));

        List<ValidatedUpload> validatedUploads = validateFiles(files);
    long existingImageCount = imageRepository.countByServiceRequest_Id(requestId);
    if (existingImageCount + validatedUploads.size() > maxFilesPerRequest) {
        throw new IllegalArgumentException("A maximum of " + maxFilesPerRequest
            + " images can be attached to one request");
    }

        List<Path> storedPaths = new ArrayList<>();
        List<RequestImage> savedImages = new ArrayList<>();

        try {
            for (ValidatedUpload upload : validatedUploads) {
                String storageKey = "requests/" + requestId + "/" + UUID.randomUUID() + upload.extension();
                Path target = resolveStoragePath(storageKey);
                Files.createDirectories(target.getParent());
                storedPaths.add(target);
                try (InputStream input = upload.file().getInputStream();
                     OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    input.transferTo(output);
                }

                savedImages.add(imageRepository.save(new RequestImage(
                        request,
                        storageKey,
                        upload.originalFilename(),
                        upload.contentType(),
                        upload.file().getSize()
                )));
            }
        } catch (IOException | RuntimeException exception) {
            deleteStoredFiles(storedPaths);
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unable to store uploaded image", exception);
        }

        requestService.analyzeUploadedImage(request, new FileSystemResource(storedPaths.get(0)), validatedUploads.get(0).contentType());
        return savedImages.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RequestImageResponse> listImages(Long requestId, String userEmail, UserRole role) {
        requestService.getRequest(requestId, userEmail, role);
        return imageRepository.findAllByServiceRequest_IdOrderByCreatedAtAsc(requestId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImageContent getImage(
            Long requestId,
            Long imageId,
            String userEmail,
            UserRole role
    ) {
        requestService.getRequest(requestId, userEmail, role);
        RequestImage image = imageRepository.findByIdAndServiceRequest_Id(imageId, requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Request image not found"));
        Path imagePath = resolveStoragePath(image.getStoragePath());
        if (!Files.isRegularFile(imagePath)) {
            throw new ResourceNotFoundException("Request image file not found");
        }
        return new ImageContent(
                new FileSystemResource(imagePath),
                MediaType.parseMediaType(image.getContentType()),
                image.getOriginalFilename()
        );
    }

    private List<ValidatedUpload> validateFiles(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one image is required");
        }
        if (files.size() > maxFilesPerRequest) {
            throw new IllegalArgumentException("A maximum of " + maxFilesPerRequest + " images can be uploaded");
        }

        List<ValidatedUpload> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Uploaded images cannot be empty");
            }
            if (file.getSize() > maxFileSizeBytes) {
                throw new IllegalArgumentException("Each image must be no larger than "
                        + maxFileSizeBytes + " bytes");
            }

            String safeOriginalFilename = safeOriginalFilename(file.getOriginalFilename());
            String extension = extensionOf(safeOriginalFilename);
            String contentType = detectContentType(file);
            validateExtensionAndContentType(extension, contentType, file.getContentType());
            uploads.add(new ValidatedUpload(file, safeOriginalFilename, extension, contentType));
        }
        return uploads;
    }

    private String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("An image filename is required");
        }
        String basename = originalFilename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        if (basename.isBlank()) {
            throw new IllegalArgumentException("Invalid image filename");
        }
        return basename.length() > 255 ? basename.substring(basename.length() - 255) : basename;
    }

    private String extensionOf(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0) {
            throw new IllegalArgumentException("Only JPG, JPEG, and PNG images are supported");
        }
        String extension = filename.substring(extensionIndex).toLowerCase(Locale.ROOT);
        if (!List.of(".jpg", ".jpeg", ".png").contains(extension)) {
            throw new IllegalArgumentException("Only JPG, JPEG, and PNG images are supported");
        }
        return extension;
    }

    private String detectContentType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(PNG_SIGNATURE.length);
            if (Arrays.equals(header, PNG_SIGNATURE)) {
                return MediaType.IMAGE_PNG_VALUE;
            }
            if (header.length >= 3 && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
                return MediaType.IMAGE_JPEG_VALUE;
            }
            throw new IllegalArgumentException("File contents are not a valid JPG, JPEG, or PNG image");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read uploaded image", exception);
        }
    }

    private void validateExtensionAndContentType(String extension, String detectedType, String declaredType) {
        boolean jpegExtension = extension.equals(".jpg") || extension.equals(".jpeg");
        boolean jpegContent = detectedType.equals(MediaType.IMAGE_JPEG_VALUE);
        if (jpegExtension != jpegContent) {
            throw new IllegalArgumentException("Image extension does not match its file contents");
        }

        if (declaredType != null && !declaredType.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(declaredType)
                && !declaredType.equalsIgnoreCase(detectedType)
                && !(jpegContent && "image/jpg".equalsIgnoreCase(declaredType))) {
            throw new IllegalArgumentException("Image content type does not match its file contents");
        }
    }

    private Path resolveStoragePath(String storageKey) {
        Path resolved = storageRoot.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageRoot)) {
            throw new IllegalStateException("Invalid stored image path");
        }
        return resolved;
    }

    private void deleteStoredFiles(List<Path> paths) {
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }

    private RequestImageResponse toResponse(RequestImage image) {
        Long requestId = image.getServiceRequest().getId();
        return new RequestImageResponse(
                image.getId(),
                image.getOriginalFilename(),
                image.getContentType(),
                image.getFileSizeBytes(),
                "/api/requests/" + requestId + "/images/" + image.getId(),
                image.getCreatedAt()
        );
    }

    private record ValidatedUpload(
            MultipartFile file,
            String originalFilename,
            String extension,
            String contentType
    ) {
    }

    public record ImageContent(Resource resource, MediaType contentType, String originalFilename) {
    }
}
