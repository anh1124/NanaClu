package com.example.nanaclu.data.repository;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.MimeTypeMap;
import com.example.nanaclu.data.model.FileAttachment;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileRepository {
    private static final String TAG = "FileRepository";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final Set<String> ALLOWED_MIME_TYPES = new HashSet<>(Arrays.asList(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain",
        "text/csv",
        "application/zip",
        "application/x-zip-compressed",
        "application/x-rar-compressed",
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp"
    ));

    private final FirebaseStorage storage;
    private final Context context;

    public interface ProgressCallback {
        void onProgress(int progress);
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface FileValidationCallback {
        void onValid(List<FileAttachment> validFiles);
        void onInvalid(List<String> errors);
    }

    public interface FileProgressCallback {
        void onProgress(int progress);
        void onSuccess(File downloadedFile);
        void onFailure(Exception e);
    }

    public FileRepository(Context context) {
        this.context = context;
        this.storage = FirebaseStorage.getInstance();
    }

    // Validate files before upload
    public void validateFiles(List<Uri> fileUris, FileValidationCallback callback) {
        List<FileAttachment> validFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Uri uri : fileUris) {
            try {
                String fileName = getFileName(uri);
                String mimeType = getMimeType(uri);
                long fileSize = getFileSize(uri);

                // Validate file size
                if (fileSize > MAX_FILE_SIZE) {
                    errors.add(fileName + ": File quá lớn (tối đa 50MB)");
                    continue;
                }

                // Validate MIME type
                if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
                    errors.add(fileName + ": Loại file không được hỗ trợ");
                    continue;
                }

                // Create FileAttachment
                String fileType = FileAttachment.getFileTypeFromMimeType(mimeType);
                FileAttachment attachment = new FileAttachment(fileName, fileType, fileSize, mimeType);
                validFiles.add(attachment);

            } catch (Exception e) {
                errors.add("Lỗi đọc file: " + e.getMessage());
            }
        }

        if (errors.isEmpty()) {
            callback.onValid(validFiles);
        } else {
            callback.onInvalid(errors);
        }
    }


    private File getUnifiedDownloadDir() {
        // Lấy thư mục Download public như Zalo/Telegram
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        
        // Tạo sub-folder NanaClu nếu chưa có
        File appDownloadDir = new File(downloadDir, "NanaClu");
        if (!appDownloadDir.exists()) {
            appDownloadDir.mkdirs();
        }
        
        return appDownloadDir;
    }
    // Upload files to Firebase Storage
    public Task<List<FileAttachment>> uploadFiles(List<Uri> fileUris, List<FileAttachment> fileAttachments, 
                                                  String chatId, String uploaderId, ProgressCallback callback) {
        List<Task<FileAttachment>> uploadTasks = new ArrayList<>();

        for (int i = 0; i < fileUris.size(); i++) {
            Uri uri = fileUris.get(i);
            FileAttachment attachment = fileAttachments.get(i);
            
            // Sử dụng unified storage structure: /chats/{chatId}/files/
            String storagePath = "chats/" + chatId + "/files/" + 
                               System.currentTimeMillis() + "_" + attachment.fileName;
            
            StorageReference ref = storage.getReference().child(storagePath);
            
            UploadTask uploadTask = ref.putFile(uri);
            
            // Track upload progress
            uploadTask.addOnProgressListener(taskSnapshot -> {
                double progress = (100.0 * taskSnapshot.getBytesTransferred()) / taskSnapshot.getTotalByteCount();
                attachment.uploadProgress = (int) progress;
                attachment.isUploading = true;
                if (callback != null) {
                    callback.onProgress((int) progress);
                }
            });

            Task<FileAttachment> fileTask = uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                return ref.getDownloadUrl();
            }).continueWith(task -> {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    attachment.storageUrl = storagePath;
                    attachment.downloadUrl = downloadUri.toString();
                    attachment.uploadedBy = uploaderId;
                    attachment.uploadedAt = System.currentTimeMillis();
                    attachment.isUploading = false;
                    attachment.uploadProgress = 100;
                    return attachment;
                } else {
                    attachment.isUploading = false;
                    throw task.getException();
                }
            });

            uploadTasks.add(fileTask);
        }

        return Tasks.whenAllSuccess(uploadTasks).continueWith(task -> {
            if (task.isSuccessful()) {
                // Cast an toàn với type checking
                List<?> rawResult = task.getResult();
                List<FileAttachment> result = new ArrayList<>();

                // Kiểm tra từng element trước khi cast
                for (Object item : rawResult) {
                    if (item instanceof FileAttachment) {
                        result.add((FileAttachment) item);
                    }
                }

                if (callback != null) {
                    callback.onSuccess();
                }
                return result;
            } else {
                if (callback != null) {
                    callback.onFailure(task.getException());
                }
                throw task.getException();
            }
        });
    }

    // Download file from Firebase Storage
    public Task<File> downloadFile(FileAttachment attachment, ProgressCallback callback) {
        if (attachment.downloadUrl == null) {
            return Tasks.forException(new IllegalArgumentException("Download URL is null"));
        }

        // Check if file already exists locally
        File localFile = getLocalFile(attachment);
        if (localFile.exists()) {
            attachment.isDownloaded = true;
            attachment.localPath = localFile.getAbsolutePath();
            if (callback != null) {
                callback.onSuccess();
            }
            return Tasks.forResult(localFile);
        }

        // Create local file
        try {
            localFile.getParentFile().mkdirs();
            localFile.createNewFile();
        } catch (Exception e) {
            return Tasks.forException(e);
        }

        StorageReference ref = storage.getReferenceFromUrl(attachment.downloadUrl);
        
        return ref.getFile(localFile).continueWith(task -> {
            if (task.isSuccessful()) {
                attachment.isDownloaded = true;
                attachment.localPath = localFile.getAbsolutePath();
                attachment.isDownloading = false;
                attachment.downloadProgress = 100;
                if (callback != null) {
                    callback.onSuccess();
                }
                return localFile;
            } else {
                attachment.isDownloading = false;
                if (callback != null) {
                    callback.onFailure(task.getException());
                }
                throw task.getException();
            }
        });
    }

    // Download file from Firebase Storage vào path do caller xác định
    public void downloadFile(FileAttachment attachment, File targetLocalFile, FileProgressCallback callback) {
        if (attachment == null || attachment.downloadUrl == null || targetLocalFile == null) {
            if (callback != null) callback.onFailure(new IllegalArgumentException("Invalid params"));
            return;
        }
        try {
            if (!targetLocalFile.getParentFile().exists()) targetLocalFile.getParentFile().mkdirs();
            if (!targetLocalFile.exists()) targetLocalFile.createNewFile();
        } catch (Exception e) {
            if (callback != null) callback.onFailure(e);
            return;
        }
        StorageReference ref = storage.getReferenceFromUrl(attachment.downloadUrl);
        ref.getFile(targetLocalFile)
           .addOnProgressListener(snapshot -> {
               long total = snapshot.getTotalByteCount();
               long done = snapshot.getBytesTransferred();
               int progress = total > 0 ? (int)(100 * done / total) : 0;
               if (callback != null) callback.onProgress(progress);
           })
           .addOnSuccessListener(taskSnapshot -> {
               attachment.isDownloaded = true;
               attachment.localPath = targetLocalFile.getAbsolutePath();
               attachment.isDownloading = false;
               attachment.downloadProgress = 100;
               if (callback != null) callback.onSuccess(targetLocalFile);
           })
           .addOnFailureListener(e -> {
               attachment.isDownloading = false;
               if (callback != null) callback.onFailure(e);
           });
    }

    // Helper methods
    private String getFileName(Uri uri) {
        String fileName = "unknown_file";
        try {
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                cursor.moveToFirst();
                fileName = cursor.getString(nameIndex);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file name", e);
        }
        return fileName;
    }

    private String getMimeType(Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        return mimeType != null ? mimeType : "application/octet-stream";
    }

    private long getFileSize(Uri uri) {
        long size = 0;
        try {
            android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE);
                cursor.moveToFirst();
                size = cursor.getLong(sizeIndex);
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting file size", e);
        }
        return size;
    }

    private File getLocalFile(FileAttachment attachment) {
        File dir = getUnifiedDownloadDir();
        return new File(dir, attachment.fileName);
    }

    /**
     * Dọn dẹp file cũ hơn 7 ngày
     */
    public void cleanupOldFiles() {
        File dir = getUnifiedDownloadDir();
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                long cutoffTime = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000); // 7 ngày
                for (File file : files) {
                    // 📝 Xóa file cũ để tránh đầy bộ nhớ
                    if (file.lastModified() < cutoffTime) {
                        boolean deleted = file.delete();
                        if (!deleted) {
                            Log.w(TAG, "Không thể xóa file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    public long getTotalDownloadedSize() {
        File filesDir = new File(context.getFilesDir(), "downloaded_files");
        if (!filesDir.exists()) return 0;

        long totalSize = 0;
        File[] files = filesDir.listFiles();
        if (files != null) {
            for (File file : files) {
                totalSize += file.length();
            }
        }
        return totalSize;
    }

    /**
     * Xóa toàn bộ file trong thư mục Downloads của app
     */
    public void clearAllDownloadedFiles() {
        File dir = getUnifiedDownloadDir();
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.w(TAG, "Không thể xóa file: " + file.getAbsolutePath());
                }
            }
        }
    }

    /**
     * Kiểm tra file đã được tải chưa theo fileName
     */
    public boolean isFileDownloaded(String fileName) {
        File dir = getUnifiedDownloadDir();
        File localFile = new File(dir, fileName);
        return localFile.exists();
    }


    /**
     * Lấy path local của file (nếu tồn tại)
     */
    public String getLocalFilePath(String fileName) {
        File dir = getUnifiedDownloadDir();
        File localFile = new File(dir, fileName);
        return localFile.exists() ? localFile.getAbsolutePath() : null;
    }

    /**
     * Migrate file từ old storage structure sang new unified structure
     * Old: chat_files/{chatId}/filename
     * New: chats/{chatId}/files/filename
     */
    public void migrateFileToUnifiedStructure(String oldStoragePath, String chatId, String fileName,
                                            com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                            com.google.android.gms.tasks.OnFailureListener onFailure) {
        try {
            // Tạo reference cho old path
            StorageReference oldRef = storage.getReference().child(oldStoragePath);
            
            // Tạo reference cho new unified path
            String newStoragePath = "chats/" + chatId + "/files/" + fileName;
            StorageReference newRef = storage.getReference().child(newStoragePath);
            
            // Copy file từ old location sang new location
            oldRef.getBytes(Long.MAX_VALUE).addOnSuccessListener(bytes -> {
                newRef.putBytes(bytes).addOnSuccessListener(taskSnapshot -> {
                    // Xóa file cũ sau khi copy thành công
                    oldRef.delete().addOnCompleteListener(deleteTask -> {
                        if (onSuccess != null) {
                            onSuccess.onSuccess(newStoragePath);
                        }
                    });
                }).addOnFailureListener(onFailure);
            }).addOnFailureListener(onFailure);
            
        } catch (Exception e) {
            if (onFailure != null) {
                onFailure.onFailure(e);
            }
        }
    }

    /**
     * Migrate image từ old storage structure sang new unified structure
     * Old: post_images/filename hoặc images/post_images/filename
     * New: chats/{chatId}/images/filename
     */
    public void migrateImageToUnifiedStructure(String oldStoragePath, String chatId, String fileName,
                                             com.google.android.gms.tasks.OnSuccessListener<String> onSuccess,
                                             com.google.android.gms.tasks.OnFailureListener onFailure) {
        try {
            // Tạo reference cho old path
            StorageReference oldRef = storage.getReference().child(oldStoragePath);
            
            // Tạo reference cho new unified path
            String newStoragePath = "chats/" + chatId + "/images/" + fileName;
            StorageReference newRef = storage.getReference().child(newStoragePath);
            
            // Copy file từ old location sang new location
            oldRef.getBytes(Long.MAX_VALUE).addOnSuccessListener(bytes -> {
                newRef.putBytes(bytes).addOnSuccessListener(taskSnapshot -> {
                    // Xóa file cũ sau khi copy thành công
                    oldRef.delete().addOnCompleteListener(deleteTask -> {
                        if (onSuccess != null) {
                            onSuccess.onSuccess(newStoragePath);
                        }
                    });
                }).addOnFailureListener(onFailure);
            }).addOnFailureListener(onFailure);
            
        } catch (Exception e) {
            if (onFailure != null) {
                onFailure.onFailure(e);
            }
        }
    }
}
