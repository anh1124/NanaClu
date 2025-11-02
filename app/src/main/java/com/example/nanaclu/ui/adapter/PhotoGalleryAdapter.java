package com.example.nanaclu.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Message;
import java.util.List;

/**
 * Adapter cho RecyclerView hiển thị bộ sưu tập ảnh
 * 
 * Chức năng chính:
 * - Hiển thị danh sách ảnh dưới dạng lưới hoặc ngang
 * - Hỗ trợ xem ảnh toàn màn hình khi click
 * - Tối ưu hiển thị ảnh với Glide
 * - Hỗ trợ cập nhật danh sách ảnh động
 */
public class PhotoGalleryAdapter extends RecyclerView.Adapter<PhotoGalleryAdapter.PhotoViewHolder> {
    private List<Message> photoMessages;
    private OnPhotoClickListener listener;

    public interface OnPhotoClickListener {
        void onPhotoClick(Message photoMessage);
    }

    public PhotoGalleryAdapter(List<Message> photoMessages, OnPhotoClickListener listener) {
        this.photoMessages = photoMessages;
        this.listener = listener;
    }

    public void updatePhotos(List<Message> newPhotos) {
        this.photoMessages = newPhotos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_gallery, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        Message photoMessage = photoMessages.get(position);
        holder.bind(photoMessage, listener);
    }

    @Override
    public int getItemCount() {
        return photoMessages.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivPhoto;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPhoto = itemView.findViewById(R.id.ivPhoto);
        }

        void bind(Message photoMessage, OnPhotoClickListener listener) {
            // Load photo with Glide (using content field for image URL)
            Glide.with(itemView.getContext())
                    .load(photoMessage.content)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_error)
                    .centerCrop()
                    .into(ivPhoto);

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPhotoClick(photoMessage);
                }
            });
        }
    }
}
