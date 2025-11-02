package com.example.nanaclu.ui.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị danh sách ảnh xem trước
 * 
 * Chức năng chính:
 * - Hiển thị danh sách ảnh dưới dạng lưới hoặc ngang
 * - Hỗ trợ xóa ảnh khỏi danh sách
 * - Tối ưu hiển thị ảnh với Glide
 * - Hỗ trợ tương tác xóa ảnh thông qua callback
 */
public class ImagePreviewAdapter extends RecyclerView.Adapter<ImagePreviewAdapter.ViewHolder> {
    
    private List<Uri> imageUris = new ArrayList<>();
    private OnImageRemoveListener listener;

    public interface OnImageRemoveListener {
        void onRemove(int position);
    }

    public ImagePreviewAdapter(OnImageRemoveListener listener) {
        this.listener = listener;
    }

    public void setImages(List<Uri> uris) {
        this.imageUris = new ArrayList<>(uris);
        notifyDataSetChanged();
    }

    public void removeImage(int position) {
        if (position >= 0 && position < imageUris.size()) {
            imageUris.remove(position);
            notifyDataSetChanged(); // Full update to refresh all numbers
        }
    }

    public List<Uri> getImages() {
        return new ArrayList<>(imageUris);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image_preview, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Uri uri = imageUris.get(position);
        
        // Load image
        Glide.with(holder.itemView.getContext())
                .load(uri)
                .centerCrop()
                .into(holder.ivPreview);
        
        // Set number (1-based index)
        holder.tvNumber.setText(String.valueOf(position + 1));
        
        // Set remove listener
        holder.ivRemove.setOnClickListener(v -> {
            if (listener != null) {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    listener.onRemove(currentPosition);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPreview;
        ImageView ivRemove;
        TextView tvNumber;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview = itemView.findViewById(R.id.ivPreview);
            ivRemove = itemView.findViewById(R.id.ivRemove);
            tvNumber = itemView.findViewById(R.id.tvNumber);
        }
    }
}

