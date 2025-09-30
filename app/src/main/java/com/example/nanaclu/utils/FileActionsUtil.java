package com.example.nanaclu.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.nanaclu.data.model.FileAttachment;
import com.example.nanaclu.data.repository.FileRepository;

import java.io.File;

/**
 * FileActionsUtil
 *  - Luồng chuẩn xử lý file: kiểm tra local → tải nếu cần → mở qua FileProvider
 *  - Nơi lưu: app external files (Downloads) để tránh xin quyền lưu trữ
 */
public class FileActionsUtil {

    private final Context context;
    private final FileRepository fileRepository;

    public FileActionsUtil(Context context, FileRepository fileRepository) {
        this.context = context;
        this.fileRepository = fileRepository;
    }

    /**
     * Lấy file local trong thư mục public Download/NanaClu
     * Path: /storage/emulated/0/Download/NanaClu/<messageId>_<fileName>
     * Giống như Zalo, Telegram - lưu vào thư mục public để user dễ truy cập
     */
    public File getLocalFile(String messageId, String fileName) {
        // Lấy thư mục Download public
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        
        // Tạo sub-folder NanaClu nếu chưa có
        File appDownloadDir = new File(downloadDir, "NanaClu");
        if (!appDownloadDir.exists()) {
            appDownloadDir.mkdirs();
        }
        
        // Trả về file path chuẩn
        return new File(appDownloadDir, messageId + "_" + fileName);
    }

    /**
     * Xử lý click file: nếu tồn tại → mở, chưa có → tải về rồi mở
     * - Có kiểm tra trường hợp file.localPath đã bị xóa thủ công
     */
    public void handleFileClick(FileAttachment file) {
        handleFileClick(file, null);
    }

    // Overload: cho phép truyền callback để cập nhật UI (icon/progress)
    public void handleFileClick(FileAttachment file, Runnable onChanged) {
        // Đồng bộ trạng thái ngay khi item hiển thị
        syncLocalState(file, onChanged);

        String messageId = file.parentMessageId != null ? file.parentMessageId : "unknown";

        // Ưu tiên mở theo localPath đã lưu nếu còn tồn tại
        if (file.localPath != null) {
            File bySavedPath = new File(file.localPath);
            if (bySavedPath.exists()) {
                openFile(bySavedPath, file.mimeType);
                return;
            } else {
                // File đã bị xóa thủ công → reset trạng thái để tải lại
                file.isDownloaded = false;
                file.localPath = null;
                if (onChanged != null) onChanged.run();
            }
        }

        // Path chuẩn theo messageId_fileName
        File target = getLocalFile(messageId, file.fileName);
        if (target.exists()) {
            file.localPath = target.getAbsolutePath();
            file.isDownloaded = true;
            if (onChanged != null) onChanged.run();
            openFile(target, file.mimeType);
            return;
        }
        downloadToLocalAndOpen(file, target, onChanged);

        // Đồng bộ lần nữa để đảm bảo icon phản ánh đúng trạng thái ngay sau khi gọi
        syncLocalState(file, onChanged);
    }

    /**
     * Tải file về đúng path chuẩn rồi mở; đồng thời cập nhật trạng thái model
     */
    private void downloadToLocalAndOpen(FileAttachment file, File targetLocalFile, Runnable onChanged) {
        Toast.makeText(context, "Đang tải xuống: " + file.fileName, Toast.LENGTH_SHORT).show();
        fileRepository.downloadFile(file, targetLocalFile, new FileRepository.FileProgressCallback() {
            @Override public void onProgress(int progress) { if (onChanged != null) onChanged.run(); }
            @Override public void onSuccess(File downloadedFile) {
                if (downloadedFile != null && downloadedFile.exists()) {
                    file.localPath = downloadedFile.getAbsolutePath();
                    file.isDownloaded = true;
                    if (onChanged != null) onChanged.run();
                    openFile(downloadedFile, file.mimeType);
                } else {
                    file.isDownloaded = false; file.localPath = null;
                    if (onChanged != null) onChanged.run();
                    Toast.makeText(context, "Không tìm thấy file sau khi tải xuống", Toast.LENGTH_LONG).show();
                }
                // Đồng bộ sau tải xong
                syncLocalState(file, onChanged);
            }
            @Override public void onFailure(Exception e) {
                file.isDownloaded = false; file.localPath = null;
                if (onChanged != null) onChanged.run();
                Toast.makeText(context, "Lỗi tải xuống: " + e.getMessage(), Toast.LENGTH_LONG).show();
                // Đồng bộ sau khi tải lỗi
                syncLocalState(file, onChanged);
            }
        });
    }

    // Kiểm tra tồn tại file local và cập nhật trạng thái model; dùng để app tự cập nhật icon khi bind/hiển thị
    public void syncLocalState(FileAttachment file, Runnable onChanged) {
        File bySavedPath = (file.localPath != null) ? new File(file.localPath) : null;
        if (bySavedPath != null && bySavedPath.exists()) {
            file.isDownloaded = true;
            if (onChanged != null) onChanged.run();
            return;
        }
        String messageId = file.parentMessageId != null ? file.parentMessageId : "unknown";
        File target = getLocalFile(messageId, file.fileName);
        if (target.exists()) {
            file.localPath = target.getAbsolutePath();
            file.isDownloaded = true;
        } else {
            file.isDownloaded = false;
            if (file.localPath != null && !new File(file.localPath).exists()) file.localPath = null;
        }
        if (onChanged != null) onChanged.run();
    }

    /**
     * Mở file bằng FileProvider (bắt buộc cho Android 10+)
     * - Bổ sung fallback MIME */
    public void openFile(File file, String mimeType) {
        try {
            String safeMime = (mimeType == null || mimeType.isEmpty()) ? "*/*" : mimeType;
            
            // Kiểm tra file có nằm trong thư mục Download public không
            String filePath = file.getAbsolutePath();
            Uri fileUri;
            
            if (filePath.contains("/Download/")) {
                // File nằm trong thư mục Download public, sử dụng FileProvider với external-path
                try {
                    fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
                } catch (Exception e) {
                    // Fallback: sử dụng file URI trực tiếp
                    fileUri = Uri.fromFile(file);
                }
            } else {
                // File nằm trong thư mục khác, dùng FileProvider bình thường
                fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            }
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, safeMime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
            } else {
                Intent chooser = Intent.createChooser(intent, "Mở file với ứng dụng nào?");
                if (chooser.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(chooser);
                } else {
                    Toast.makeText(context, "Không có ứng dụng nào có thể mở file này", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, "Lỗi mở file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Mở file explorer đến thư mục public Download/NanaClu
     * Path: /storage/emulated/0/Download/NanaClu/
     */
    public void openDownloadFolder() {
        try {
            // Lấy thư mục Download public
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File appDownloadDir = new File(downloadDir, "NanaClu");
            
            if (!appDownloadDir.exists()) {
                boolean created = appDownloadDir.mkdirs();
                if (!created) {
                    Toast.makeText(context, "Không thể tạo thư mục NanaClu", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Sử dụng FileProvider để tạo URI an toàn (tránh FileUriExposedException)
            Uri folderUri;
            try {
                folderUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", appDownloadDir);
            } catch (Exception e) {
                Log.w("FileActionsUtil", "FileProvider failed, trying alternative method", e);
                // Fallback: thử mở bằng ACTION_GET_CONTENT
                Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fallbackIntent.setType("*/*");
                fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                
                Intent chooser = Intent.createChooser(fallbackIntent, "Chọn ứng dụng quản lý file");
                if (chooser.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(chooser);
                    Toast.makeText(context, "Chọn ứng dụng để mở thư mục...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Không tìm thấy ứng dụng quản lý file", Toast.LENGTH_LONG).show();
                }
                return;
            }

            // Thử nhiều cách mở file manager với FileProvider URI
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(folderUri, "resource/folder");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                Toast.makeText(context, "Đang mở thư mục Download/NanaClu...", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Fallback: thử với chooser
            Intent chooser = Intent.createChooser(intent, "Mở thư mục với ứng dụng nào?");
            if (chooser.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(chooser);
                Toast.makeText(context, "Chọn ứng dụng để mở thư mục...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Không tìm thấy ứng dụng quản lý file", Toast.LENGTH_LONG).show();
            }
            
        } catch (Exception e) {
            String errorMsg = "Lỗi mở thư mục Downloads: " + e.getMessage();
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            Log.e("FileActionsUtil", errorMsg, e); // in ra logcat chi tiết stacktrace
        }
        
    }
}
