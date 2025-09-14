package com.mandar.photosync.controller;

import com.mandar.photosync.models.Photo;
import com.mandar.photosync.repository.PhotoRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@RestController
public class PhotoSyncController {

    private static final String UPLOAD_DIR = "uploads";
    private final PhotoRepository photoRepository;

    // Inject the repository via constructor
    public PhotoSyncController(PhotoRepository photoRepository) {
        this.photoRepository = photoRepository;
    }

    @PostMapping("/api/upload")
    public ResponseEntity<ApiResponse> handleFileUpload(@RequestParam("file") MultipartFile file) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("File is empty", null));
            }

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate a unique filename to avoid conflicts
            String originalFileName = file.getOriginalFilename();
            String fileExtension = originalFileName != null ?
                    originalFileName.substring(originalFileName.lastIndexOf(".")) : "";
            String storedFileName = UUID.randomUUID() + fileExtension;

            // Save the file to filesystem
            Path filePath = uploadPath.resolve(storedFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Save file metadata to database
            Photo photo = new Photo(
                    originalFileName,
                    storedFileName,
                    filePath.toString(),
                    file.getSize(),
                    file.getContentType()
            );

            Photo savedPhoto = photoRepository.save(photo);

            System.out.println("Uploaded and saved to DB: " + originalFileName +
                    " (ID: " + savedPhoto.getId() + ")");

            return ResponseEntity.ok(
                    new ApiResponse("File uploaded successfully", savedPhoto.getId().toString())
            );

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ApiResponse("Failed to upload file: " + e.getMessage(), null));
        }
    }

    // Endpoint to get all photos
    @GetMapping("/api/photos")
    public ResponseEntity<List<Photo>> getAllPhotos() {
        return ResponseEntity.ok(photoRepository.findAll());
    }

    // Test endpoint
    @GetMapping(value = "/api/test", produces = "text/plain")
    public String sampleEndPoint() {
        return "PhotoSync server up and running...";
    }
}

// Response class
class ApiResponse {
    private String message;
    private String fileId;

    public ApiResponse(String message, String fileId) {
        this.message = message;
        this.fileId = fileId;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
}