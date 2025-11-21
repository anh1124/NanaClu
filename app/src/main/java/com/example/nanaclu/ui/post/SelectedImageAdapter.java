package com.example.nanaclu.ui.post;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;

import java.util.ArrayList;
import java.util.List;

public class SelectedImageAdapter extends RecyclerView.Adapter<SelectedImageAdapter.ImageViewHolder> {
    private List<String> imagePaths = new ArrayList<>();
    private OnImageRemoveListener onImageRemoveListener;

    public interface OnImageRemoveListener {
        void onImageRemove(int position);
    }

    public void setOnImageRemoveListener(OnImageRemoveListener listener) {
        this.onImageRemoveListener = listener;
    }

    public void setImages(List<String> imagePaths) {
        this.imagePaths = imagePaths != null ? imagePaths : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addImage(String imagePath) {
        this.imagePaths.add(imagePath);
        notifyItemInserted(imagePaths.size() - 1);
    }

    public void removeImage(int position) {
        if (position >= 0 && position < imagePaths.size()) {
            this.imagePaths.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, imagePaths.size() - position);
        }
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selected_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        String imagePath = imagePaths.get(position);
        holder.bind(imagePath, position);
    }

    @Override
    public int getItemCount() {
        return imagePaths.size();
    }

    class ImageViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivImage;
        private ImageView btnRemove;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivImage);
            btnRemove = itemView.findViewById(R.id.btnRemove);

            btnRemove.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && onImageRemoveListener != null) {
                    onImageRemoveListener.onImageRemove(position);
                }
            });
        }

        public void bind(String imagePath, int position) {
            // Load image from file path
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                if (bitmap != null) {
                    ivImage.setImageBitmap(bitmap);
                } else {
                    ivImage.setImageResource(R.drawable.ic_add_photo);
                }
            } catch (Exception e) {
                ivImage.setImageResource(R.drawable.ic_add_photo);
            }
        }
    }
}
