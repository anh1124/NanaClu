package com.example.nanaclu.ui.group;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;

import java.util.ArrayList;
import java.util.List;

public class GroupNotificationsActivity extends AppCompatActivity {

    private String groupId;
    private RecyclerView rv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_notifications);

        groupId = getIntent().getStringExtra("groupId");
        setupToolbar();
        setupList();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Thông báo nhóm");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void setupList() {
        rv = findViewById(R.id.rvNotifications);
        rv.setLayoutManager(new LinearLayoutManager(this));

        // Demo data giống Facebook
        List<String> items = new ArrayList<>();
        items.add("Nhóm có 3 bài đăng mới hôm nay");
        items.add("Có tin nhắn mới từ nhóm");
        items.add("Thành viên A vừa tham gia nhóm");
        items.add("Bài đăng của bạn nhận được 5 bình luận");

        rv.setAdapter(new SimpleStringAdapter(items, s ->
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
        ));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Simple inline adapter
    static class SimpleStringAdapter extends RecyclerView.Adapter<VH> {
        private final List<String> data;
        private final OnClick onClick;
        interface OnClick { void onItem(String s); }
        SimpleStringAdapter(List<String> data, OnClick onClick) { this.data = data; this.onClick = onClick; }
        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            String s = data.get(position);
            ((android.widget.TextView) holder.itemView.findViewById(android.R.id.text1)).setText(s);
            holder.itemView.setOnClickListener(v -> onClick.onItem(s));
        }
        @Override public int getItemCount() { return data.size(); }
    }

    static class VH extends RecyclerView.ViewHolder {
        VH(@NonNull android.view.View itemView) { super(itemView); }
    }
}

