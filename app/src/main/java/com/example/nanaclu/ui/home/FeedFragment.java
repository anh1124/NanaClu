package com.example.nanaclu.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.example.nanaclu.ui.group.PostAdapter;
import com.example.nanaclu.data.repository.PostRepository;
import com.example.nanaclu.data.model.Post;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class FeedFragment extends Fragment {
    private RecyclerView rvFeed;
    private PostAdapter adapter;
    private PostRepository postRepository;
    private boolean isLoading;
    private DocumentSnapshot lastVisible;
    private boolean reachedEnd;
    private java.util.Set<String> joinedGroupIds = new java.util.HashSet<>();
    private static final String TAG = "FeedFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_feed, container, false);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);
        if (toolbar != null) {
            int color = ThemeUtils.getToolbarColor(requireContext());
            toolbar.setBackgroundColor(color);
            toolbar.setTitle("NANACLUB");
            toolbar.setTitleTextColor(android.graphics.Color.parseColor("#6200EE"));
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_feed);
            toolbar.setOnMenuItemClickListener(item -> {
                android.util.Log.d(TAG, "Toolbar item clicked: " + item.getItemId());
                if (item.getItemId() == R.id.action_notice) {
                    android.widget.Toast.makeText(requireContext(), "Notice clicked", android.widget.Toast.LENGTH_SHORT).show();
                    return true;
                }
                return false;
            });
            android.util.Log.d(TAG, "Toolbar initialized. bgColor=" + color + ", title=NANACLUB");
        } else {
            android.util.Log.w(TAG, "Toolbar is null in layout");
        }

        postRepository = new PostRepository(FirebaseFirestore.getInstance());

        rvFeed = root.findViewById(R.id.rvFeed);
        final android.widget.TextView tvEmpty = root.findViewById(R.id.tvEmpty);
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        rvFeed.setLayoutManager(lm);
        adapter = new PostAdapter(postRepository, new PostAdapter.PostActionListener() {
            @Override public void onLike(Post post) {}
            @Override public void onComment(Post post) {}
            @Override public void onDelete(Post post) {}
            @Override public void onReport(Post post) {}
        });
        rvFeed.setAdapter(adapter);
        android.util.Log.d(TAG, "RecyclerView + Adapter set");

        rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy <= 0) return;
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (!isLoading && !reachedEnd && (visible + first) >= total - 2) {
                    loadMore();
                }
            }
        });

        // TODO: lấy danh sách group đã tham gia từ ViewModel/Repository
        // Tạm thời: log cảnh báo nếu trống
        if (joinedGroupIds.isEmpty()) {
            android.util.Log.w(TAG, "joinedGroupIds is empty. Feed will be empty. Hãy truyền danh sách groupId user đã tham gia.");
            tvEmpty.setVisibility(View.VISIBLE);
            rvFeed.setVisibility(View.GONE);
        }
        loadInitial();
        return root;
    }

    private void loadInitial() {
        isLoading = true; reachedEnd = false; lastVisible = null;
        // Lấy 5 post mới nhất từ tất cả groups đã tham gia: dùng collection group-by-group là phức tạp
        // Ở mức demo: ta sẽ gọi từng groupId và merge client-side (đơn giản hơn cần ViewModel). Bạn hãy thay joinedGroupIds bằng dữ liệu thực.
        final java.util.List<Post> merged = new java.util.ArrayList<>();
        if (joinedGroupIds.isEmpty()) { isLoading = false; adapter.setItems(merged); return; }
        android.util.Log.d(TAG, "loadInitial for groups: " + joinedGroupIds.size());
        // Với số lượng nhóm ít, có thể lần lượt gọi getGroupPostsPaged(groupId, 5, null, ...), gom 1-2 post mới nhất mỗi nhóm, rồi sort.
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(joinedGroupIds.size());
        for (String gid : joinedGroupIds) {
            android.util.Log.d(TAG, "fetch group posts gid=" + gid);
            postRepository.getGroupPostsPaged(gid, 5, null, (posts, last) -> {
                android.util.Log.d(TAG, "group=" + gid + " fetched posts=" + (posts != null ? posts.size() : 0));
                if (posts != null) merged.addAll(posts);
                if (remaining.decrementAndGet() == 0) {
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> top = merged.size() > 5 ? new java.util.ArrayList<>(merged.subList(0, 5)) : new java.util.ArrayList<>(merged);
                    adapter.setItems(top);
                    View root = getView();
                    if (root != null) {
                        android.widget.TextView tvEmpty = root.findViewById(R.id.tvEmpty);
                        if (top.isEmpty()) {
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvFeed.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            rvFeed.setVisibility(View.VISIBLE);
                        }
                    }
                    isLoading = false;
                    // Lưu ý: để phân trang đa-group đúng, cần một cơ chế cursor hợp nhất; phần này có thể mở rộng sau
                    android.util.Log.d(TAG, "feed initial size=" + top.size());
                }
            }, e -> {
                android.util.Log.e(TAG, "fetch error gid=" + gid + ": " + e.getMessage());
                if (remaining.decrementAndGet() == 0) {
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> top = merged.size() > 5 ? new java.util.ArrayList<>(merged.subList(0, 5)) : new java.util.ArrayList<>(merged);
                    adapter.setItems(top);
                    isLoading = false;
                }
            });
        }
    }

    private void loadMore() {
        // Gợi ý: để làm phân trang chuẩn đa-group, ta cần giữ một priority queue theo createdAt và một map groupId->lastVisible
        // Do hiện chưa có danh sách groupIds thực ở đây, tôi để trống hàm này và sẽ bổ sung khi có nguồn groupIds.
    }
}


