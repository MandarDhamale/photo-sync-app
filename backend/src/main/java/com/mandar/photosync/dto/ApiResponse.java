package com.mandar.photosync.dto;

public class ApiResponse {
    private String message;
    private String fileId;

    public ApiResponse() {}

    public ApiResponse(String message, String fileId) {
        this.message = message;
        this.fileId = fileId;
    }

    // Getters and setters
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
}
