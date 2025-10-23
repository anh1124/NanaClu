package com.example.nanaclu.ui.group;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.example.nanaclu.data.repository.PostRepository;
import com.example.nanaclu.ui.common.CommentsBottomSheet;
import com.example.nanaclu.ui.report.ReportBottomSheetDialogFragment;
import com.example.nanaclu.utils.ThemeUtils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class SearchResultsActivity extends AppCompatActivity {
    
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_SEARCH_TYPE = "searchType";
    private static final String EXTRA_SEARCH_QUERY = "searchQuery";
    
    public static final String SEARCH_TYPE_CONTENT = "content";
    public static final String SEARCH_TYPE_AUTHOR = "author";
    
    private String groupId;
    private String searchType;
    private String searchQuery;
    private String currentUserId;
    
    private PostRepository postRepository;
    private PostAdapter postAdapter;
    private RecyclerView rvSearchResults;
    private ProgressBar progressBar;
    private ProgressBar progressBarLoadMore;
    private TextView tvSearchInfo;
    private TextView tvResultCount;
    private TextView tvNoResults;
    
    private boolean isLoadingMore = false;
    private boolean reachedEnd = false;
    private DocumentSnapshot lastVisible;
    private int totalResults = 0;
    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_results);
        
        // Get intent extras
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        searchType = getIntent().getStringExtra(EXTRA_SEARCH_TYPE);
        searchQuery = getIntent().getStringExtra(EXTRA_SEARCH_QUERY);
        
        if (groupId == null || searchType == null || searchQuery == null) {
            Toast.makeText(this, "Thiếu thông tin tìm kiếm", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Get current user
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            currentUserId = currentUser.getUid();
        } else {
            finish();
            return;
        }
        
        // Initialize repository
        postRepository = new PostRepository(FirebaseFirestore.getInstance());
        
        // Setup UI
        setupToolbar();
        setupViews();
        setupRecyclerView();
        
        // Start search
        performSearch();
    }
    
    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setBackgroundColor(ThemeUtils.getThemeColor(this));
        toolbar.setTitleTextColor(getResources().getColor(android.R.color.white));
        toolbar.setNavigationOnClickListener(v -> finish());
    }
    
    private void setupViews() {
        rvSearchResults = findViewById(R.id.rvSearchResults);
        progressBar = findViewById(R.id.progressBar);
        progressBarLoadMore = findViewById(R.id.progressBarLoadMore);
        tvSearchInfo = findViewById(R.id.tvSearchInfo);
        tvResultCount = findViewById(R.id.tvResultCount);
        tvNoResults = findViewById(R.id.tvNoResults);
        
        // Update search info
        String searchTypeText = SEARCH_TYPE_CONTENT.equals(searchType) ? "nội dung" : "tác giả";
        tvSearchInfo.setText("Tìm kiếm theo " + searchTypeText + ": \"" + searchQuery + "\"");
    }
    
    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rvSearchResults.setLayoutManager(layoutManager);
        
        postAdapter = new PostAdapter(postRepository, new PostAdapter.PostActionListener() {
            @Override
            public void onLike(Post post) {
                // TODO: implement like functionality
            }
            
            @Override
            public void onComment(Post post) {
                CommentsBottomSheet.show(SearchResultsActivity.this, post);
            }
            
            @Override
            public void onDelete(Post post) {
                // Show delete confirmation dialog
                new androidx.appcompat.app.AlertDialog.Builder(SearchResultsActivity.this)
                        .setTitle("Xóa bài viết")
                        .setMessage("Bạn có chắc chắn muốn xóa bài viết này?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            postRepository.deletePost(groupId, post.postId, new PostRepository.PostCallback() {
                                @Override
                                public void onSuccess(Post deletedPost) {
                                    postAdapter.removePost(post.postId);
                                    totalResults--;
                                    updateResultCount();
                                    Toast.makeText(SearchResultsActivity.this, "Đã xóa bài viết", Toast.LENGTH_SHORT).show();
                                }
                                
                                @Override
                                public void onError(Exception e) {
                                    Toast.makeText(SearchResultsActivity.this, "Lỗi khi xóa bài viết", Toast.LENGTH_SHORT).show();
                                }
                            });
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            }
            
            @Override
            public void onReport(Post post) {
                ReportBottomSheetDialogFragment reportDialog = new ReportBottomSheetDialogFragment();
                Bundle args = new Bundle();
                args.putString("postId", post.postId);
                args.putString("groupId", groupId);
                reportDialog.setArguments(args);
                reportDialog.show(getSupportFragmentManager(), "report_dialog");
            }
        });
        
        rvSearchResults.setAdapter(postAdapter);
        
        // Add scroll listener for pagination
        rvSearchResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return;
                
                int visible = layoutManager.getChildCount();
                int total = layoutManager.getItemCount();
                int first = layoutManager.findFirstVisibleItemPosition();
                
                if (!isLoadingMore && !reachedEnd && (visible + first) >= total - 2) {
                    loadMoreResults();
                }
            }
        });
    }
    
    private void performSearch() {
        progressBar.setVisibility(View.VISIBLE);
        rvSearchResults.setVisibility(View.GONE);
        tvNoResults.setVisibility(View.GONE);
        tvResultCount.setText("Đang tìm kiếm...");
        
        lastVisible = null;
        reachedEnd = false;
        totalResults = 0;
        
        loadMoreResults();
    }
    
    private void loadMoreResults() {
        if (isLoadingMore || reachedEnd) return;
        
        isLoadingMore = true;
        progressBarLoadMore.setVisibility(View.VISIBLE);
        
        PostRepository.PagedPostsCallback callback = new PostRepository.PagedPostsCallback() {
            @Override
            public void onSuccess(List<Post> posts, DocumentSnapshot last) {
                progressBar.setVisibility(View.GONE);
                progressBarLoadMore.setVisibility(View.GONE);
                isLoadingMore = false;
                
                if (posts == null || posts.isEmpty()) {
                    if (totalResults == 0) {
                        // No results at all
                        tvNoResults.setVisibility(View.VISIBLE);
                        rvSearchResults.setVisibility(View.GONE);
                        tvResultCount.setText("Không tìm thấy kết quả nào");
                    } else {
                        // No more results to load
                        reachedEnd = true;
                        Toast.makeText(SearchResultsActivity.this, "Đã tải hết kết quả", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Add results
                    if (totalResults == 0) {
                        // First page
                        postAdapter.setItems(posts);
                        rvSearchResults.setVisibility(View.VISIBLE);
                        tvNoResults.setVisibility(View.GONE);
                    } else {
                        // Load more
                        postAdapter.addItems(posts);
                    }
                    
                    totalResults += posts.size();
                    lastVisible = last;
                    updateResultCount();
                    
                    // Check if we've reached the end
                    if (posts.size() < 5) {
                        reachedEnd = true;
                    }
                }
            }
            
            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                progressBarLoadMore.setVisibility(View.GONE);
                isLoadingMore = false;
                
                Toast.makeText(SearchResultsActivity.this, "Lỗi khi tìm kiếm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                tvResultCount.setText("Lỗi khi tìm kiếm");
            }
        };
        
        // Call appropriate search method
        if (SEARCH_TYPE_CONTENT.equals(searchType)) {
            postRepository.searchPostsByContentPaged(groupId, searchQuery, 5, lastVisible, callback);
        } else {
            postRepository.searchPostsByAuthorPaged(groupId, searchQuery, 5, lastVisible, callback);
        }
    }
    
    private void updateResultCount() {
        if (totalResults == 0) {
            tvResultCount.setText("Không tìm thấy kết quả nào");
        } else {
            tvResultCount.setText("Tìm thấy " + totalResults + " bài viết");
        }
    }
    
    public static void start(android.content.Context context, String groupId, String searchType, String searchQuery) {
        Intent intent = new Intent(context, SearchResultsActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_SEARCH_TYPE, searchType);
        intent.putExtra(EXTRA_SEARCH_QUERY, searchQuery);
        context.startActivity(intent);
    }
}
