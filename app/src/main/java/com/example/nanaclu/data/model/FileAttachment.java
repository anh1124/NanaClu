package com.example.nanaclu.data.model;

public class FileAttachment {
    public String fileName;
    public String fileType; // "pdf", "doc", "txt", "zip", etc.
    public long fileSize; // in bytes
    public String storageUrl; // Firebase Storage path
    public String downloadUrl; // Firebase Storage download URL
    public long uploadedAt;
    public String uploadedBy;
    public boolean isDownloaded; // For offline access tracking
    public String localPath; // Local file path for downloaded files
    public String mimeType; // Full MIME type (e.g., "application/pdf")
    public String senderName; // Tên người gửi file (dùng cho UI gallery)

    // Upload/download progress tracking (not stored in Firestore)
    public transient int uploadProgress = 0;
    public transient int downloadProgress = 0;
    public transient boolean isUploading = false;
    public transient boolean isDownloading = false;

    public FileAttachment() {}

    public FileAttachment(String fileName, String fileType, long fileSize, String mimeType) {
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.mimeType = mimeType;
        this.uploadedAt = System.currentTimeMillis();
        this.isDownloaded = false;
    }

    // Utility methods
    public String getFormattedFileSize() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.1f KB", fileSize / 1024.0);
        } else if (fileSize < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", fileSize / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public String getFileExtension() {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        }
        return fileType != null ? fileType.toLowerCase() : "unknown";
    }

    public boolean isValidFile() {
        return fileName != null && !fileName.trim().isEmpty() && 
               fileSize > 0 && fileSize <= 50 * 1024 * 1024; // 50MB limit
    }

    public static String getFileTypeFromMimeType(String mimeType) {
        if (mimeType == null) return "unknown";
        
        if (mimeType.startsWith("image/")) return "image";
        if (mimeType.equals("application/pdf")) return "pdf";
        if (mimeType.equals("application/msword") || 
            mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
            return "doc";
        }
        if (mimeType.equals("application/vnd.ms-excel") || 
            mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
            return "xls";
        }
        if (mimeType.equals("application/vnd.ms-powerpoint") || 
            mimeType.equals("application/vnd.openxmlformats-officedocument.presentationml.presentation")) {
            return "ppt";
        }
        if (mimeType.startsWith("text/")) return "txt";
        if (mimeType.equals("application/zip") || mimeType.equals("application/x-zip-compressed")) {
            return "zip";
        }
        if (mimeType.equals("application/x-rar-compressed")) return "rar";
        if (mimeType.startsWith("audio/")) return "audio";
        if (mimeType.startsWith("video/")) return "video";
        
        return "unknown";
    }
}
