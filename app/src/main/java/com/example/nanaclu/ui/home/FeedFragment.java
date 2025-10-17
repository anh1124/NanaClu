package com.example.nanaclu.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.nanaclu.ui.BaseFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.nanaclu.R;
import com.example.nanaclu.utils.ThemeUtils;
import com.example.nanaclu.ui.group.PostAdapter;
import com.example.nanaclu.data.repository.PostRepository;
import com.example.nanaclu.data.repository.GroupRepository;
import com.example.nanaclu.data.model.Post;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.*;

public class FeedFragment extends BaseFragment {
    private RecyclerView rvFeed;
    private PostAdapter adapter;
    private PostRepository postRepository;
    private GroupRepository groupRepository;
    private SwipeRefreshLayout swipeRefresh;

    private boolean isLoading;
    private boolean reachedEnd;

    // Phân trang đa-group
    private final Set<String> joinedGroupIds = new HashSet<>();
    private final Map<String, DocumentSnapshot> lastByGroup = new HashMap<>();
    private final Set<String> loadedPostIds = new HashSet<>();

    private static final String TAG = "FeedFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_feed, container, false);
        androidx.appcompat.widget.Toolbar toolbar = root.findViewById(R.id.toolbar);
        if (toolbar != null) {
            int color = ThemeUtils.getThemeColor(requireContext());
            toolbar.setBackgroundColor(color);
            toolbar.setTitle("NANACLU");
            toolbar.setTitleTextColor(android.graphics.Color.WHITE);
            toolbar.setSubtitle(null);
            toolbar.setLogo(null);
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_feed);
            // Ensure overflow icon is visible on colored toolbar
            toolbar.post(() -> {
                android.graphics.drawable.Drawable ov = toolbar.getOverflowIcon();
                if (ov != null) ov.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_ATOP);
            });
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
        groupRepository = new GroupRepository(FirebaseFirestore.getInstance());

        swipeRefresh = root.findViewById(R.id.swipeRefreshFeed);
        rvFeed = root.findViewById(R.id.rvFeed);
        final android.widget.TextView tvEmpty = root.findViewById(R.id.tvEmpty);

        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        rvFeed.setLayoutManager(lm);
        adapter = new PostAdapter(postRepository, new PostAdapter.PostActionListener() {
            @Override public void onLike(Post post) {}
            @Override public void onComment(Post post) { showComments(post); }
            @Override public void onDelete(Post post) {}
            @Override public void onReport(Post post) {
                if (post.groupId != null) {
                    com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment reportSheet = 
                        com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment.newInstance(
                            post.groupId, post.postId, post.authorId);
                    reportSheet.show(getParentFragmentManager(), "report_bottom_sheet");
                } else {
                    android.widget.Toast.makeText(requireContext(), "Không thể báo cáo bài này", android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        }, true); // Show group name in feed
        rvFeed.setAdapter(adapter);
        // Manual refresh: swipe up at bottom to load more; horizontal swipe to navigate fragments
        rvFeed.setOnTouchListener(new android.view.View.OnTouchListener() {
            float downY, downX;
            final float threshold = getResources().getDisplayMetrics().density * 80; // ~80dp
            @Override public boolean onTouch(android.view.View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        downY = e.getY();
                        downX = e.getX();
                        break;
                    case android.view.MotionEvent.ACTION_UP:
                        float upY = e.getY();
                        float upX = e.getX();
                        boolean atBottom = !v.canScrollVertically(1);
                        if (atBottom && (downY - upY) > threshold && !isLoading && !reachedEnd) {
                            loadMore();
                            return true;
                        }
                        // Removed horizontal swipe navigation
                        break;
                }
                return false;
            }
        });
        android.util.Log.d(TAG, "RecyclerView + Adapter set");

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::refreshFeed);
        }

        rvFeed.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                // Hide/show toolbar on scroll direction
                androidx.appcompat.widget.Toolbar tb = root.findViewById(R.id.toolbar);
                if (tb != null) {
                    if (dy > 6) {
                        tb.animate().translationY(-tb.getHeight()).setDuration(150).start();
                    } else if (dy < -6) {
                        tb.animate().translationY(0).setDuration(150).start();
                    }
                }
                // Infinite scroll
                int visible = lm.getChildCount();
                int total = lm.getItemCount();
                int first = lm.findFirstVisibleItemPosition();
                if (dy > 0 && !isLoading && !reachedEnd && (visible + first) >= total - 2) {
                    loadMore();
                }
            }
        });

        // Nạp danh sách group user đã tham gia rồi load bài
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        groupRepository.loadJoinedGroupIds()
                .addOnSuccessListener(ids -> {
                    joinedGroupIds.clear();
                    joinedGroupIds.addAll(ids);
                    if (joinedGroupIds.isEmpty()) {
                        android.util.Log.w(TAG, "User chưa tham gia group nào");
                        tvEmpty.setVisibility(View.VISIBLE);
                        rvFeed.setVisibility(View.GONE);
                        isLoading = false; reachedEnd = true;
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        return;
                    }
                    tvEmpty.setVisibility(View.GONE);
                    rvFeed.setVisibility(View.VISIBLE);
                    loadInitial();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e(TAG, "Lỗi lấy group đã tham gia: " + e.getMessage());
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                });

        return root;
    }

    @Override
    protected void onThemeChanged() {
        // Reapply theme color to toolbar
        if (getView() != null) {
            androidx.appcompat.widget.Toolbar toolbar = getView().findViewById(R.id.toolbar);
            if (toolbar != null) {
                int color = ThemeUtils.getThemeColor(requireContext());
                toolbar.setBackgroundColor(color);
                toolbar.setTitleTextColor(android.graphics.Color.WHITE);
            }
        }
    }

    private void refreshFeed() {
        // Clear và tải lại từ đầu
        reachedEnd = false;
        isLoading = false;
        loadedPostIds.clear();
        lastByGroup.clear();
        adapter.setItems(new java.util.ArrayList<>());
        loadInitial();
    }

    private void loadInitial() {
        isLoading = true; reachedEnd = false;
        loadedPostIds.clear();
        lastByGroup.clear();

        final java.util.List<Post> merged = new java.util.ArrayList<>();
        final java.util.Map<String, DocumentSnapshot> tmpLast = new java.util.HashMap<>();
        if (joinedGroupIds.isEmpty()) {
            isLoading = false;
            adapter.setItems(merged);
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            return;
        }
        android.util.Log.d(TAG, "loadInitial for groups: " + joinedGroupIds.size());
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(joinedGroupIds.size());
        for (String gid : joinedGroupIds) {
            postRepository.getGroupPostsPaged(gid, 5, null, (posts, last) -> {
                if (posts != null && !posts.isEmpty()) {
                    merged.addAll(posts);
                    tmpLast.put(gid, last);
                }
                if (remaining.decrementAndGet() == 0) {
                    // Hợp nhất, sort và khử trùng lặp theo postId, lấy 5 bài mới nhất
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> top = new java.util.ArrayList<>();
                    for (Post p : merged) {
                        if (p == null || p.postId == null) continue;
                        if (loadedPostIds.add(p.postId)) { // add returns false nếu đã tồn tại
                            top.add(p);
                        }
                        if (top.size() == 5) break;
                    }
                    adapter.setItems(top);
                    lastByGroup.putAll(tmpLast);

                    View root = getView();
                    if (root != null) {
                        android.widget.TextView tvEmpty = root.findViewById(R.id.tvEmpty);
                        if (top.isEmpty()) {
                            tvEmpty.setText(joinedGroupIds.isEmpty()
                                    ? "Hãy tham gia group để xem bài viết của thành viên khác!"
                                    : "Ở đây hơi trống thì phải, group của bạn chưa có ai đăng bài");
                            tvEmpty.setVisibility(View.VISIBLE);
                            rvFeed.setVisibility(View.GONE);
                        } else {
                            tvEmpty.setVisibility(View.GONE);
                            rvFeed.setVisibility(View.VISIBLE);
                        }
                    }
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);

                    // Nếu không lấy đủ 5 và không còn gì ở các group -> đánh dấu hết
                    if (top.isEmpty()) reachedEnd = true;
                }
            }, e -> {
                if (remaining.decrementAndGet() == 0) {
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> top = new java.util.ArrayList<>();
                    for (Post p : merged) {
                        if (p == null || p.postId == null) continue;
                        if (loadedPostIds.add(p.postId)) top.add(p);
                        if (top.size() == 5) break;
                    }
                    adapter.setItems(top);
                    lastByGroup.putAll(tmpLast);
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                }
            });
        }
    }

    private void loadMore() {
        if (isLoading || reachedEnd) return;
        isLoading = true;
        final java.util.List<Post> merged = new java.util.ArrayList<>();
        final java.util.Map<String, DocumentSnapshot> tmpLast = new java.util.HashMap<>();
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(joinedGroupIds.size());

        for (String gid : joinedGroupIds) {
            DocumentSnapshot cursor = lastByGroup.get(gid);
            postRepository.getGroupPostsPaged(gid, 3, cursor, (posts, last) -> {
                if (posts != null && !posts.isEmpty()) {
                    merged.addAll(posts);
                    tmpLast.put(gid, last);
                }
                if (remaining.decrementAndGet() == 0) {
                    // Lọc bỏ các post đã có, sort và lấy tối đa 5
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> next = new java.util.ArrayList<>();
                    for (Post p : merged) {
                        if (p == null || p.postId == null) continue;
                        if (!loadedPostIds.contains(p.postId)) {
                            next.add(p);
                        }
                        if (next.size() == 5) break;
                    }
                    if (next.isEmpty()) {
                        reachedEnd = true;
                        android.widget.Toast.makeText(requireContext(), "Bạn đã xem hết bài viết", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        adapter.addItems(next);
                        for (Post p : next) loadedPostIds.add(p.postId);
                        lastByGroup.putAll(tmpLast);
                    }
                    isLoading = false;
                }
            }, e -> {
                if (remaining.decrementAndGet() == 0) {
                    merged.sort((a,b) -> Long.compare(b.createdAt, a.createdAt));
                    java.util.List<Post> next = new java.util.ArrayList<>();
                    for (Post p : merged) {
                        if (p == null || p.postId == null) continue;
                        if (!loadedPostIds.contains(p.postId)) next.add(p);
                        if (next.size() == 5) break;
                    }
                    if (next.isEmpty()) {
                        reachedEnd = true;
                        android.widget.Toast.makeText(requireContext(), "Bạn đã xem hết bài viết", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        adapter.addItems(next);
                        for (Post p : next) loadedPostIds.add(p.postId);
                        lastByGroup.putAll(tmpLast);
                    }
                    isLoading = false;
                }
            });
        }
    }
    private void showComments(Post post) {
        com.example.nanaclu.ui.common.CommentsBottomSheet.show(this, post);
    }
}
