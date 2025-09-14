package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/*lỗi ,cái chỗ nhập mã nhóm ,icon paste to quá
thêm chức ,
ở active thành viên hóm ,vẫn chưa hiển thị avatar,bỏ mấy cáu chủ sở hữu với thành viên ở ietm đi vì có tag member với owner rồi
,làm cho cái phần tìm kiếm thành hoạt động được giống như group fracment,
cải tiến chức năng tìm kiếm ,mặc định là chỉ load ra 10 doc để tiếp kiệm ở mấy cái list dạng item như thế ,khi kéo xuống như post thì mới hiện thêm, còn nếu dùng cái ô tìm kiếm để tìm thì sau khi nhập 2s sẽ gửi cái thứ mà user vừa nhập lên db để lấy list ,thêm fetch lại list thành viên sau khi ấn kich và fetch thủ công
thêm sau khi kich thì xóa toàn bộ bài post mà người vừa kich đã đăng
lỗi ở thành viên nhóm active ,màu toolbar chưa giống với màu đẫ thiết lập trong profile,
xóa toast  hiện group id và oncreate khi ấn vào item trong group list để vào group detail,lỗi ,nút bình luận (ấn vào để em demo ở post) khi ấn vào thì chỉ có ở feed mới hhiệndemo ,còn post ở group lại không hiện, bạn kiểm ta xem, đồng thời loại bỏ chữ demo ở demo comment đi

log lỗi ở postdetailactive.java:
Cannot resolve symbol 'bumptech'
`@layout/activity_post_detail` does not contain a declaration with id `btnBack`
Cannot resolve symbol 'Glide'
*/

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.Post;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostDetailActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_POST_ID = "postId";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private RecyclerView rvComments;
    private CommentsAdapter adapter;
    private TextView tvAuthor, tvTime, tvContent;
    private androidx.constraintlayout.widget.ConstraintLayout imageArea;

    // Debounce storage per comment
    private final Map<String, Integer> pendingLikeIncrements = new HashMap<>();
    private final Map<String, Runnable> pendingRunnables = new HashMap<>();

    private String groupId;
    private String postId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        postId = getIntent().getStringExtra(EXTRA_POST_ID);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> onBackPressed());

        tvAuthor = findViewById(R.id.tvAuthor);
        tvTime = findViewById(R.id.tvTime);
        tvContent = findViewById(R.id.tvContent);
        imageArea = findViewById(R.id.imageArea);

        rvComments = findViewById(R.id.rvComments);
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CommentsAdapter(new ArrayList<>(), new CommentActionListener() {
            @Override public void onLikeClicked(Comment c, int position) { likeWithDebounce(c, position); }
        });
        rvComments.setAdapter(adapter);

        EditText edtComment = findViewById(R.id.edtComment);
        View btnSend = findViewById(R.id.btnSendComment);
        btnSend.setOnClickListener(v -> {
            String text = edtComment.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            addComment(text);
            edtComment.setText("");
        });

        loadPost();
        loadComments();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup pending debounce tasks
        for (Runnable r : pendingRunnables.values()) handler.removeCallbacks(r);
        pendingRunnables.clear();
        pendingLikeIncrements.clear();
    }

    private void loadPost() {
        if (groupId == null || postId == null) return;
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .get()
                .addOnSuccessListener(doc -> {
                    Post post = doc.toObject(Post.class);
                    if (post == null) return;
                    tvAuthor.setText("Member");
                    tvTime.setText(android.text.format.DateUtils.getRelativeTimeSpanString(post.createdAt));
                    tvContent.setVisibility(TextUtils.isEmpty(post.content) ? View.GONE : View.VISIBLE);
                    tvContent.setText(post.content);
                    setupImages(post.imageUrls);
                });
    }

    private void setupImages(List<String> urls) {
        imageArea.removeAllViews();
        if (urls == null || urls.isEmpty()) return;
        // Simple vertical list of images for detail screen; tap to open viewer
        for (int i = 0; i < urls.size(); i++) {
            android.widget.ImageView iv = new android.widget.ImageView(this);
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            iv.setAdjustViewBounds(true);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            Glide.with(this).load(urls.get(i))
                    .placeholder(R.drawable.image_background)
                    .error(R.drawable.image_background)
                    .into(iv);
            final int index = i;
            iv.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(this, ImageViewerActivity.class);
                intent.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGES, new ArrayList<>(urls));
                intent.putExtra(ImageViewerActivity.EXTRA_INDEX, index);
                startActivity(intent);
            });
            imageArea.addView(iv);
        }
    }

    private void loadComments() {
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .orderBy("createdAt")
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;
                    List<Comment> list = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        Comment c = d.toObject(Comment.class);
                        if (c == null) continue;
                        c.commentId = d.getId();
                        list.add(c);
                    }
                    adapter.setItems(list);
                });
    }

    private void addComment(String text) {
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        data.put("likeCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("comments")
                .add(data);
    }

    private void likeWithDebounce(@NonNull Comment c, int position) {
        // Immediate local update
        c.likeCount += 1;
        adapter.notifyItemChanged(position, "like_only");

        // Track pending increments
        int pending = pendingLikeIncrements.containsKey(c.commentId) ? pendingLikeIncrements.get(c.commentId) : 0;
        pendingLikeIncrements.put(c.commentId, pending + 1);

        // Reset timer
        if (pendingRunnables.containsKey(c.commentId)) {
            handler.removeCallbacks(pendingRunnables.get(c.commentId));
        }
        Runnable r = () -> {
            Integer inc = pendingLikeIncrements.remove(c.commentId);
            pendingRunnables.remove(c.commentId);
            if (inc == null || inc <= 0) return;
            db.collection("groups").document(groupId)
                    .collection("posts").document(postId)
                    .collection("comments").document(c.commentId)
                    .update("likeCount", FieldValue.increment(inc));
        };
        pendingRunnables.put(c.commentId, r);
        handler.postDelayed(r, 3000);
    }

    // ----- Simple data holder for comments -----
    public static class Comment {
        public String commentId;
        public String text;
        public long likeCount;
        public Timestamp createdAt;
        public Comment() {}
    }

    interface CommentActionListener {
        void onLikeClicked(Comment c, int position);
    }

    static class CommentsAdapter extends RecyclerView.Adapter<CommentsAdapter.VH> {
        private final List<Comment> items;
        private final CommentActionListener listener;
        CommentsAdapter(List<Comment> items, CommentActionListener l) { this.items = items; this.listener = l; }
        void setItems(List<Comment> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_comment, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos), pos, listener); }
        @Override public int getItemCount() { return items.size(); }
        @Override public void onBindViewHolder(@NonNull VH h, int pos, @NonNull List<Object> payloads) {
            if (!payloads.isEmpty() && payloads.contains("like_only")) {
                h.tvLikes.setText(String.valueOf(items.get(pos).likeCount));
            } else {
                super.onBindViewHolder(h, pos, payloads);
            }
        }
        static class VH extends RecyclerView.ViewHolder {
            TextView tvText, tvLikes; View btnLike;
            VH(@NonNull View itemView) { super(itemView);
                tvText = itemView.findViewById(R.id.tvCommentText);
                tvLikes = itemView.findViewById(R.id.tvLikeCount);
                btnLike = itemView.findViewById(R.id.btnLikeComment);
            }
            void bind(Comment c, int position, CommentActionListener l) {
                tvText.setText(c.text);
                tvLikes.setText(String.valueOf(c.likeCount));
                btnLike.setOnClickListener(v -> { if (l != null) l.onLikeClicked(c, position); });
            }
        }
    }
}

