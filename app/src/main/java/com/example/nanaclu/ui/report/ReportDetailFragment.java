package com.example.nanaclu.ui.report;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.ReportModel;
import com.example.nanaclu.data.repository.ReportRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Fragment hiển thị chi tiết một báo cáo và cho phép admin/moderator xử lý
 * Có các action: Dismiss, Remove Post, Warn User
 */
public class ReportDetailFragment extends Fragment {
    
    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_REPORT_ID = "report_id";
    
    private String groupId;
    private String reportId;
    private ReportModel report;
    
    private ReportRepository reportRepository;
    
    // UI Components
    private TextView tvReason;
    private TextView tvReasonDetail;
    private TextView tvPostId;
    private TextView tvReportedUser;
    private TextView tvReporter;
    private TextView tvTimestamp;
    private TextView tvStatus;
    private TextView tvModeratorNote;
    private TextView tvAction;
    private Map<String, String> userNameCache = new HashMap<>();
    
    private EditText etModeratorNote;
    private Button btnDismiss;
    private Button btnRemovePost;
    private Button btnKickUser;
    private Button btnBack;
    private Button btnViewPost;
    
    /**
     * Tạo instance mới của fragment với groupId và reportId
     */
    public static ReportDetailFragment newInstance(String groupId, String reportId) {
        ReportDetailFragment fragment = new ReportDetailFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putString(ARG_REPORT_ID, reportId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString(ARG_GROUP_ID);
            reportId = getArguments().getString(ARG_REPORT_ID);
        }
        
        reportRepository = new ReportRepository(FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report_detail, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupClickListeners();
        loadReport();
    }
    
    /**
     * Khởi tạo các view components
     */
    private void initViews(View view) {
        tvReason = view.findViewById(R.id.tvReason);
        tvReasonDetail = view.findViewById(R.id.tvReasonDetail);
        tvPostId = view.findViewById(R.id.tvPostId);
        tvReportedUser = view.findViewById(R.id.tvReportedUser);
        tvReporter = view.findViewById(R.id.tvReporter);
        tvTimestamp = view.findViewById(R.id.tvTimestamp);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvModeratorNote = view.findViewById(R.id.tvModeratorNote);
        tvAction = view.findViewById(R.id.tvAction);
        
        etModeratorNote = view.findViewById(R.id.etModeratorNote);
        btnDismiss = view.findViewById(R.id.btnDismiss);
        btnRemovePost = view.findViewById(R.id.btnRemovePost);
        btnKickUser = view.findViewById(R.id.btnKickUser);
        btnBack = view.findViewById(R.id.btnBack);
        btnViewPost = view.findViewById(R.id.btnViewPost);
    }
    
    /**
     * Thiết lập các sự kiện click
     */
    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            if (getActivity() != null) {
                // Sử dụng FragmentManager để quay lại fragment trước
                if (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getActivity().getSupportFragmentManager().popBackStack();
                } else {
                    getActivity().onBackPressed();
                }
            }
        });
        
        btnDismiss.setOnClickListener(v -> onDismissClicked());
        btnRemovePost.setOnClickListener(v -> onRemovePostClicked());
        btnKickUser.setOnClickListener(v -> onKickUserClicked());
        btnViewPost.setOnClickListener(v -> onViewPostClicked());
    }
    
    /**
     * Load chi tiết report từ Firestore
     */
    private void loadReport() {
        reportRepository.getReportById(groupId, reportId, new ReportRepository.Callback<ReportModel>() {
            @Override
            public void onSuccess(ReportModel reportData) {
                report = reportData;
                displayReportData();
            }
            
            @Override
            public void onError(Exception e) {
                Toast.makeText(requireContext(), "Lỗi tải báo cáo: " + e.getMessage(), Toast.LENGTH_LONG).show();
                if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            }
        });
    }
    
    /**
     * Hiển thị dữ liệu report lên UI
     */
    private void displayReportData() {
        if (report == null) return;
        
        // Lý do báo cáo
        tvReason.setText(getReasonDisplayText(report.reason));
        
        // Chi tiết lý do
        if (report.reasonDetail != null && !report.reasonDetail.trim().isEmpty()) {
            tvReasonDetail.setText(report.reasonDetail);
            tvReasonDetail.setVisibility(View.VISIBLE);
        } else {
            tvReasonDetail.setVisibility(View.GONE);
        }
        
        // Post ID - click để copy
        tvPostId.setText("Post ID: " + report.postId);
        tvPostId.setOnClickListener(v -> copyToClipboard(report.postId, "Post ID"));
        
        // User bị báo cáo - fetch name and display
        displayReportedUserName();

        // Người báo cáo - fetch name and display
        displayReporterName();
        
        // Thời gian
        tvTimestamp.setText("Thời gian: " + formatTimestamp(report.timestamp));
        
        // Trạng thái
        tvStatus.setText("Trạng thái: " + getStatusDisplayText(report.status));
        tvStatus.setTextColor(getStatusColor(report.status));
        
        // Ghi chú moderator (nếu có)
        if (report.moderatorNote != null && !report.moderatorNote.trim().isEmpty()) {
            tvModeratorNote.setText("Ghi chú: " + report.moderatorNote);
            tvModeratorNote.setVisibility(View.VISIBLE);
        } else {
            tvModeratorNote.setVisibility(View.GONE);
        }
        
        // Action đã thực hiện (nếu có)
        if (report.action != null && !report.action.trim().isEmpty()) {
            tvAction.setText("Hành động: " + getActionDisplayText(report.action));
            tvAction.setVisibility(View.VISIBLE);
        } else {
            tvAction.setVisibility(View.GONE);
        }
        
        // Ẩn các button action nếu đã xử lý
        if (!"pending".equals(report.status)) {
            btnDismiss.setVisibility(View.GONE);
            btnRemovePost.setVisibility(View.GONE);
            btnKickUser.setVisibility(View.GONE);
            etModeratorNote.setVisibility(View.GONE);
        }
    }
    
    /**
     * Xử lý khi click Dismiss - bỏ qua báo cáo
     */
    private void onDismissClicked() {
        String note = etModeratorNote.getText().toString().trim();
        
        Map<String, Object> extra = new HashMap<>();
        extra.put("moderatorId", FirebaseAuth.getInstance().getUid());
        if (!note.isEmpty()) {
            extra.put("moderatorNote", note);
        }
        
        updateReportStatus("dismissed", extra, "Đã bỏ qua báo cáo");
    }
    
    /**
     * Xử lý khi click Remove Post - xóa bài đăng
     */
    private void onRemovePostClicked() {
        String note = etModeratorNote.getText().toString().trim();
        
        if (report == null || report.postId == null) {
            Toast.makeText(requireContext(), "Không tìm thấy bài đăng", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Xóa post từ Firestore (dùng Long timestamp thay vì Timestamp)
        FirebaseFirestore.getInstance()
            .collection("groups").document(groupId)
            .collection("posts").document(report.postId)
            .update("deletedAt", System.currentTimeMillis())
            .addOnSuccessListener(aVoid -> {
                // Sau khi xóa post thành công, update report status
                Map<String, Object> extra = new HashMap<>();
                extra.put("moderatorId", FirebaseAuth.getInstance().getUid());
                extra.put("action", "removed");
                if (!note.isEmpty()) {
                    extra.put("moderatorNote", note);
                }
                
                updateReportStatus("action_taken", extra, "Đã xóa bài đăng");
            })
            .addOnFailureListener(e -> {
                Toast.makeText(requireContext(), "Lỗi xóa bài đăng: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Xử lý khi click Kick User - kick user khỏi group
     */
    private void onKickUserClicked() {
        String note = etModeratorNote.getText().toString().trim();
        
        if (report == null || report.reportedUserId == null) {
            Toast.makeText(requireContext(), "Không tìm thấy user", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Kick user khỏi group (xóa member)
        FirebaseFirestore.getInstance()
            .collection("groups").document(groupId)
            .collection("members").document(report.reportedUserId)
            .delete()
            .addOnSuccessListener(aVoid -> {
                // Giảm memberCount của group
                FirebaseFirestore.getInstance()
                    .collection("groups").document(groupId)
                    .update("memberCount", com.google.firebase.firestore.FieldValue.increment(-1));

                // Log the kick action
                com.example.nanaclu.data.repository.LogRepository logRepository =
                    new com.example.nanaclu.data.repository.LogRepository(FirebaseFirestore.getInstance());

                Map<String, Object> logMetadata = new HashMap<>();
                logMetadata.put("reason", "reported_content");
                logMetadata.put("reportId", reportId);
                if (!note.isEmpty()) {
                    logMetadata.put("moderatorNote", note);
                }

                logRepository.logGroupAction(groupId, com.example.nanaclu.data.model.GroupLog.TYPE_MEMBER_REMOVED,
                    "user", report.reportedUserId, null, logMetadata);

                // Sau khi kick thành công, update report status
                Map<String, Object> extra = new HashMap<>();
                extra.put("moderatorId", FirebaseAuth.getInstance().getUid());
                extra.put("action", "kicked");
                if (!note.isEmpty()) {
                    extra.put("moderatorNote", note);
                }

                updateReportStatus("action_taken", extra, "Đã kick user khỏi nhóm");
            })
            .addOnFailureListener(e -> {
                Toast.makeText(requireContext(), "Lỗi kick user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
    }
    
    /**
     * Xử lý khi click "Xem bài đăng"
     */
    private void onViewPostClicked() {
        if (report != null && report.postId != null) {
            // Mở PostDetailActivity với cả groupId và postId
            Intent intent = new Intent(requireContext(), com.example.nanaclu.ui.post.PostDetailActivity.class);
            intent.putExtra(com.example.nanaclu.ui.post.PostDetailActivity.EXTRA_GROUP_ID, groupId);
            intent.putExtra(com.example.nanaclu.ui.post.PostDetailActivity.EXTRA_POST_ID, report.postId);
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), "Không thể mở bài đăng", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Cập nhật trạng thái report
     */
    private void updateReportStatus(String newStatus, Map<String, Object> extra, String successMessage) {
        reportRepository.updateReportStatus(groupId, reportId, newStatus, extra, new ReportRepository.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
                // Quay lại dashboard
                if (getActivity() != null) {
                    if (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        getActivity().onBackPressed();
                    }
                }
            }
            
            @Override
            public void onError(Exception e) {
                Toast.makeText(requireContext(), "Lỗi cập nhật: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
    
    // Helper methods để format text
    private String getReasonDisplayText(String reason) {
        if (reason == null) return "Không xác định";
        
        switch (reason) {
            case "spam": return "Spam/Quảng cáo";
            case "inappropriate": return "Nội dung không phù hợp";
            case "harassment": return "Quấy rối/Bắt nạt";
            case "violence": return "Bạo lực/Đe dọa";
            case "misinformation": return "Thông tin sai lệch";
            case "copyright": return "Vi phạm bản quyền";
            case "other": return "Khác";
            default: return reason;
        }
    }
    
    private String getStatusDisplayText(String status) {
        if (status == null) return "Không xác định";
        
        switch (status) {
            case "pending": return "Chờ xử lý";
            case "reviewed": return "Đã xem xét";
            case "dismissed": return "Đã bỏ qua";
            case "action_taken": return "Đã xử lý";
            default: return status;
        }
    }
    
    private String getActionDisplayText(String action) {
        if (action == null) return "";
        
        switch (action) {
            case "removed": return "Xóa bài đăng";
            case "kicked": return "Kick user";
            case "banned": return "Cấm user";
            default: return action;
        }
    }
    
    private int getStatusColor(String status) {
        if (status == null) return 0xFF666666;
        
        switch (status) {
            case "pending": return 0xFFFF9800; // Orange
            case "reviewed": return 0xFF2196F3; // Blue
            case "dismissed": return 0xFF9E9E9E; // Gray
            case "action_taken": return 0xFF4CAF50; // Green
            default: return 0xFF666666; // Dark gray
        }
    }
    
    private String formatTimestamp(long timestamp) {
        return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            .format(new java.util.Date(timestamp));
    }
    
    /**
     * Display reported user name instead of ID
     */
    private void displayReportedUserName() {
        if (report.reportedUserId != null) {
            // Check cache first
            if (userNameCache.containsKey(report.reportedUserId)) {
                String cachedName = userNameCache.get(report.reportedUserId);
                displayReportedUserText(cachedName != null ? cachedName : "Unknown User");
            } else {
                // Fetch from Firestore
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(report.reportedUserId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        String displayName = null;
                        if (doc.exists()) {
                            displayName = doc.getString("displayName");
                            if (displayName == null || displayName.isEmpty()) {
                                displayName = doc.getString("name");
                            }
                        }
                        String finalName = displayName != null ? displayName : "Unknown User";
                        userNameCache.put(report.reportedUserId, finalName);
                        displayReportedUserText(finalName);
                    })
                    .addOnFailureListener(e -> {
                        userNameCache.put(report.reportedUserId, "Unknown User");
                        displayReportedUserText("Unknown User");
                    });
            }
        } else {
            tvReportedUser.setText("User bị báo cáo: N/A");
            tvReportedUser.setOnClickListener(null);
        }
    }

    /**
     * Display reporter name instead of ID
     */
    private void displayReporterName() {
        if (report.reporterUserId != null) {
            // Check cache first
            if (userNameCache.containsKey(report.reporterUserId)) {
                String cachedName = userNameCache.get(report.reporterUserId);
                displayReporterText(cachedName != null ? cachedName : "Unknown User");
            } else {
                // Fetch from Firestore
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(report.reporterUserId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        String displayName = null;
                        if (doc.exists()) {
                            displayName = doc.getString("displayName");
                            if (displayName == null || displayName.isEmpty()) {
                                displayName = doc.getString("name");
                            }
                        }
                        String finalName = displayName != null ? displayName : "Unknown User";
                        userNameCache.put(report.reporterUserId, finalName);
                        displayReporterText(finalName);
                    })
                    .addOnFailureListener(e -> {
                        userNameCache.put(report.reporterUserId, "Unknown User");
                        displayReporterText("Unknown User");
                    });
            }
        } else {
            tvReporter.setText("Người báo cáo: Ẩn danh");
            tvReporter.setOnClickListener(null);
        }
    }

    private void displayReportedUserText(String userName) {
        tvReportedUser.setText("User bị báo cáo: " + userName);
        tvReportedUser.setOnClickListener(v -> copyToClipboard(report.reportedUserId, "Reported User ID"));
    }

    private void displayReporterText(String userName) {
        tvReporter.setText("Người báo cáo: " + userName);
        tvReporter.setOnClickListener(v -> copyToClipboard(report.reporterUserId, "Reporter User ID"));
    }

    /**
     * Copy text to clipboard
     */
    private void copyToClipboard(String text, String label) {
        if (text == null) return;

        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
            requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText(label, text);
        clipboard.setPrimaryClip(clip);

        Toast.makeText(requireContext(), "Đã copy " + label, Toast.LENGTH_SHORT).show();
    }
}
