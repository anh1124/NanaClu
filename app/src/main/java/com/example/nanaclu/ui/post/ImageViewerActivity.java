package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;

import java.util.ArrayList;
import java.util.List;

public class ImageViewerActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGES = "images";
    public static final String EXTRA_INDEX = "index";

    private ViewPager2 viewPager;
    private android.widget.TextView tvCounter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        viewPager = findViewById(R.id.pager);
        tvCounter = findViewById(R.id.tvCounter);
        ImageButton btnBack = findViewById(R.id.btnBack);

        ArrayList<String> images = getIntent().getStringArrayListExtra(EXTRA_IMAGES);
        int index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        if (images == null) images = new ArrayList<>();
        final int total = images.size();

        ImagePagerAdapter adapter = new ImagePagerAdapter(images);
        viewPager.setAdapter(adapter);
        if (index >= 0 && index < total) {
            viewPager.setCurrentItem(index, false);
        }
        updateCounter(index, total);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateCounter(position, total);
            }
        });

        btnBack.setOnClickListener(v -> onBackPressed());
    }

    private void updateCounter(int position, int total) {
        if (tvCounter != null) tvCounter.setText((position+1) + "/" + total);
    }

    static class ImagePagerAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<ImageViewHolder> {
        private final List<String> images;
        ImagePagerAdapter(List<String> images) { this.images = images; }

        @Override public int getItemCount() { return images.size(); }

        @Override
        public ImageViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_image_viewer_page, parent, false);
            return new ImageViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ImageViewHolder holder, int position) {
            String url = images.get(position);
            // Simple full-screen ImageView; if you want pinch-to-zoom, we can add PhotoView dependency later
            Glide.with(holder.image.getContext())
                    .load(url)
                    .placeholder(R.drawable.image_background)
                    .error(R.drawable.image_background)
                    .into(holder.image);
        }
    }

    static class ImageViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        android.widget.ImageView image;
        ImageViewHolder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.fullImage);
        }
    }
}

