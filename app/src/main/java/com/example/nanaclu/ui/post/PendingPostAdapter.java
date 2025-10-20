package com.example.nanaclu.ui.post;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;

import java.util.List;

public class PendingPostAdapter extends RecyclerView.Adapter<PendingPostAdapter.VH> {

    public interface PendingPostActionListener {
        void onApprove(Post post);
        void onReject(Post post);
    }

    private List<Post> items;
    private final PendingPostActionListener listener;

    public PendingPostAdapter(List<Post> items, PendingPostActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setItems(List<Post> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_post, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Post p = items.get(position);
        h.tvAuthor.setText(p.authorId != null ? p.authorId : "");
        h.tvContent.setText(p.content != null ? p.content : "");

        // Only show first image as preview (if any)
        if (p.imageUrls != null && !p.imageUrls.isEmpty()) {
            h.ivImage.setVisibility(View.VISIBLE);
            Glide.with(h.ivImage.getContext()).load(p.imageUrls.get(0)).into(h.ivImage);
        } else {
            h.ivImage.setVisibility(View.GONE);
        }

        h.btnApprove.setOnClickListener(v -> listener.onApprove(p));
        h.btnReject.setOnClickListener(v -> listener.onReject(p));
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvAuthor;
        TextView tvContent;
        ImageView ivImage;
        Button btnApprove;
        Button btnReject;

        VH(@NonNull View itemView) {
            super(itemView);
            tvAuthor = itemView.findViewById(R.id.tvAuthor);
            tvContent = itemView.findViewById(R.id.tvContent);
            ivImage = itemView.findViewById(R.id.ivImage);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnReject = itemView.findViewById(R.id.btnReject);
        }
    }
}


