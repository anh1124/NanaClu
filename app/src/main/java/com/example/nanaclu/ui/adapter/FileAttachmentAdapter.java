package com.example.nanaclu.ui.adapter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.FileAttachment;
import java.io.File;
import java.util.List;

public class FileAttachmentAdapter extends RecyclerView.Adapter<FileAttachmentAdapter.ViewHolder> {

    public interface OnFileActionListener {
        void onFileClick(FileAttachment file);
        void onDownloadClick(FileAttachment file);
        void onDeleteClick(FileAttachment file);
    }

    private List<FileAttachment> files;
    private OnFileActionListener listener;
    private Context context;
    private boolean showDeleteOption;

    public FileAttachmentAdapter(List<FileAttachment> files, OnFileActionListener listener, 
                                Context context, boolean showDeleteOption) {
        this.files = files;
        this.listener = listener;
        this.context = context;
        this.showDeleteOption = showDeleteOption;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_attachment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileAttachment file = files.get(position);
        holder.bind(file, listener);
    }

    @Override
    public int getItemCount() {
        return files != null ? files.size() : 0;
    }

    public void updateFiles(List<FileAttachment> newFiles) {
        this.files = newFiles;
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivFileIcon;
        private TextView tvFileName;
        private TextView tvFileSize;
        private TextView tvFileStatus;
        private ProgressBar pbProgress;
        private ImageButton btnFileAction;
        private TextView tvSenderName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.ivFileIcon);
            tvFileName = itemView.findViewById(R.id.tvFileName);
            tvFileSize = itemView.findViewById(R.id.tvFileSize);
            tvFileStatus = itemView.findViewById(R.id.tvFileStatus);
            pbProgress = itemView.findViewById(R.id.pbProgress);
            btnFileAction = itemView.findViewById(R.id.btnFileAction);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
        }

        public void bind(FileAttachment file, OnFileActionListener listener) {
            // Set file name
            tvFileName.setText(file.fileName);
            // Set sender name nếu có
            if (file.senderName != null && !file.senderName.isEmpty()) {
                tvSenderName.setText(file.senderName);
                tvSenderName.setVisibility(View.VISIBLE);
            } else {
                tvSenderName.setText("");
                tvSenderName.setVisibility(View.GONE);
            }
            
            // Set file size
            tvFileSize.setText(file.getFormattedFileSize());
            
            // Set file icon based on type
            setFileIcon(file);
            
            // Handle file status and progress
            updateFileStatus(file);
            
            // Set click listeners
            itemView.setOnClickListener(v -> {
                if (file.isDownloaded && file.localPath != null) {
                    openFile(file);
                } else if (listener != null) {
                    listener.onFileClick(file);
                }
            });
            
            btnFileAction.setOnClickListener(v -> {
                if (file.isDownloaded) {
                    openFile(file);
                } else if (file.isDownloading) {
                    // Cancel download (if implemented)
                    Toast.makeText(context, "Đang tải xuống...", Toast.LENGTH_SHORT).show();
                } else {
                    if (listener != null) {
                        listener.onDownloadClick(file);
                    }
                }
            });

            // Long click for additional options
            itemView.setOnLongClickListener(v -> {
                if (file.isDownloaded) {
                    showFileOptions(file);
                }
                return true;
            });
        }

        private void setFileIcon(FileAttachment file) {
            int iconRes;
            switch (file.fileType.toLowerCase()) {
                case "pdf":
                    iconRes = R.drawable.ic_file_pdf;
                    break;
                case "doc":
                case "docx":
                    iconRes = R.drawable.ic_file_doc;
                    break;
                case "txt":
                    iconRes = R.drawable.ic_file_txt;
                    break;
                case "zip":
                case "rar":
                    iconRes = R.drawable.ic_file_zip;
                    break;
                default:
                    iconRes = R.drawable.ic_file_generic;
                    break;
            }
            ivFileIcon.setImageResource(iconRes);
        }

        private void updateFileStatus(FileAttachment file) {
            if (file.isUploading) {
                pbProgress.setVisibility(View.VISIBLE);
                pbProgress.setProgress(file.uploadProgress);
                tvFileStatus.setVisibility(View.VISIBLE);
                tvFileStatus.setText("Đang tải lên... " + file.uploadProgress + "%");
                btnFileAction.setImageResource(R.drawable.ic_file_error);
                btnFileAction.setEnabled(false);
            } else if (file.isDownloading) {
                pbProgress.setVisibility(View.VISIBLE);
                pbProgress.setProgress(file.downloadProgress);
                tvFileStatus.setVisibility(View.VISIBLE);
                tvFileStatus.setText("Đang tải xuống... " + file.downloadProgress + "%");
                btnFileAction.setImageResource(R.drawable.ic_download);
                btnFileAction.setEnabled(false);
            } else if (file.isDownloaded) {
                pbProgress.setVisibility(View.GONE);
                tvFileStatus.setVisibility(View.VISIBLE);
                tvFileStatus.setText(" • Đã tải xuống");
                btnFileAction.setImageResource(R.drawable.ic_downloaded);
                btnFileAction.setEnabled(true);
            } else {
                pbProgress.setVisibility(View.GONE);
                tvFileStatus.setVisibility(View.GONE);
                btnFileAction.setImageResource(R.drawable.ic_download);
                btnFileAction.setEnabled(true);
            }
        }

        private void openFile(FileAttachment file) {
            if (file.localPath == null) {
                Toast.makeText(context, "File chưa được tải xuống", Toast.LENGTH_SHORT).show();
                return;
            }

            File localFile = new File(file.localPath);
            if (!localFile.exists()) {
                Toast.makeText(context, "File không tồn tại", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri fileUri = FileProvider.getUriForFile(context, 
                    context.getPackageName() + ".fileprovider", localFile);
                
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri, file.mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                } else {
                    // Try to open with chooser
                    Intent chooser = Intent.createChooser(intent, "Mở file với");
                    if (chooser.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(chooser);
                    } else {
                        Toast.makeText(context, "Không có ứng dụng để mở file này", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                Toast.makeText(context, "Lỗi mở file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void showFileOptions(FileAttachment file) {
            String[] options = {"Mở file", "Chia sẻ file", "Xóa file khỏi thiết bị"};

            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle(file.fileName)
                    .setItems(options, (dialog, which) -> {
                        switch (which) {
                            case 0: // Open file
                                openFile(file);
                                break;
                            case 1: // Share file
                                shareFile(file);
                                break;
                            case 2: // Delete local file
                                deleteLocalFile(file);
                                break;
                        }
                    })
                    .show();
        }

        private void shareFile(FileAttachment file) {
            if (file.localPath == null) {
                Toast.makeText(context, "File chưa được tải xuống", Toast.LENGTH_SHORT).show();
                return;
            }

            File localFile = new File(file.localPath);
            if (!localFile.exists()) {
                Toast.makeText(context, "File không tồn tại", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                Uri fileUri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider", localFile);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType(file.mimeType);
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(shareIntent, "Chia sẻ file");
                context.startActivity(chooser);
            } catch (Exception e) {
                Toast.makeText(context, "Lỗi chia sẻ file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void deleteLocalFile(FileAttachment file) {
            new androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("Xóa file")
                    .setMessage("Bạn có chắc muốn xóa file này khỏi thiết bị? File vẫn có thể tải lại từ server.")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        if (file.localPath != null) {
                            File localFile = new File(file.localPath);
                            if (localFile.exists() && localFile.delete()) {
                                file.isDownloaded = false;
                                file.localPath = null;
                                notifyDataSetChanged();
                                Toast.makeText(context, "Đã xóa file khỏi thiết bị", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(context, "Lỗi xóa file", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }
}
