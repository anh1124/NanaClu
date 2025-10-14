package com.example.nanaclu.ui.report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.ReportModel;
import com.example.nanaclu.data.repository.ReportRepository;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * BottomSheet cho phép user báo cáo post
 * Hiển thị form để chọn lý do báo cáo và nhập chi tiết
 */
public class ReportBottomSheetDialogFragment extends BottomSheetDialogFragment {
    
    private static final String ARG_GROUP_ID = "group_id";
    private static final String ARG_POST_ID = "post_id";
    private static final String ARG_REPORTED_USER_ID = "reported_user_id";
    
    private String groupId;
    private String postId;
    private String reportedUserId;
    
    private Spinner spinnerReason;
    private EditText etReasonDetail;
    private CheckBox cbAnonymous;
    private Button btnSubmit;
    private Button btnCancel;
    
    private ReportRepository reportRepository;
    
    /**
     * Tạo instance mới của ReportBottomSheet với các tham số cần thiết
     */
    public static ReportBottomSheetDialogFragment newInstance(String groupId, String postId, String reportedUserId) {
        ReportBottomSheetDialogFragment fragment = new ReportBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putString(ARG_POST_ID, postId);
        args.putString(ARG_REPORTED_USER_ID, reportedUserId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            groupId = getArguments().getString(ARG_GROUP_ID);
            postId = getArguments().getString(ARG_POST_ID);
            reportedUserId = getArguments().getString(ARG_REPORTED_USER_ID);
        }
        
        // Khởi tạo repository
        reportRepository = new ReportRepository(FirebaseFirestore.getInstance());
    }
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_report, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        initViews(view);
        setupSpinner();
        setupClickListeners();
    }
    
    /**
     * Khởi tạo các view components
     */
    private void initViews(View view) {
        spinnerReason = view.findViewById(R.id.spinnerReason);
        etReasonDetail = view.findViewById(R.id.etReasonDetail);
        cbAnonymous = view.findViewById(R.id.cbAnonymous);
        btnSubmit = view.findViewById(R.id.btnSubmit);
        btnCancel = view.findViewById(R.id.btnCancel);
    }
    
    /**
     * Thiết lập dữ liệu cho spinner lý do báo cáo
     */
    private void setupSpinner() {
        String[] reasons = {
            "Spam hoặc quảng cáo",
            "Nội dung không phù hợp", 
            "Quấy rối hoặc bắt nạt",
            "Bạo lực hoặc đe dọa",
            "Thông tin sai lệch",
            "Vi phạm bản quyền",
            "Khác"
        };
        
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
            requireContext(), android.R.layout.simple_spinner_item, reasons);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(adapter);
    }
    
    /**
     * Thiết lập các sự kiện click
     */
    private void setupClickListeners() {
        btnCancel.setOnClickListener(v -> dismiss());
        
        btnSubmit.setOnClickListener(v -> {
            // Disable button để tránh submit nhiều lần
            btnSubmit.setEnabled(false);
            btnSubmit.setText("Đang gửi...");
            
            submitReport();
        });
    }
    
    /**
     * Thu thập dữ liệu từ UI và tạo ReportModel
     */
    private ReportModel buildReportModel() {
        ReportModel report = new ReportModel();
        report.postId = postId;
        report.reportedUserId = reportedUserId;
        report.timestamp = System.currentTimeMillis();
        report.status = "pending";
        
        // Lấy lý do từ spinner
        String selectedReason = (String) spinnerReason.getSelectedItem();
        if (selectedReason != null) {
            // Map từ tiếng Việt sang enum string
            switch (selectedReason) {
                case "Spam hoặc quảng cáo":
                    report.reason = "spam";
                    break;
                case "Nội dung không phù hợp":
                    report.reason = "inappropriate";
                    break;
                case "Quấy rối hoặc bắt nạt":
                    report.reason = "harassment";
                    break;
                case "Bạo lực hoặc đe dọa":
                    report.reason = "violence";
                    break;
                case "Thông tin sai lệch":
                    report.reason = "misinformation";
                    break;
                case "Vi phạm bản quyền":
                    report.reason = "copyright";
                    break;
                default:
                    report.reason = "other";
                    break;
            }
        }
        
        // Lấy chi tiết từ EditText
        String detail = etReasonDetail.getText().toString().trim();
        if (!detail.isEmpty()) {
            report.reasonDetail = detail;
        }
        
        // Xử lý anonymous
        if (!cbAnonymous.isChecked()) {
            String currentUserId = FirebaseAuth.getInstance().getUid();
            report.reporterUserId = currentUserId;
        }
        // Nếu anonymous thì reporterUserId = null
        
        return report;
    }
    
    /**
     * Gửi báo cáo lên server
     */
    private void submitReport() {
        ReportModel report = buildReportModel();
        
        // Validate dữ liệu
        if (report.reason == null) {
            showError("Vui lòng chọn lý do báo cáo");
            resetSubmitButton();
            return;
        }
        
        // Gửi báo cáo
        reportRepository.submitGroupReport(groupId, report, new ReportRepository.Callback<String>() {
            @Override
            public void onSuccess(String reportId) {
                // Thành công
                Toast.makeText(requireContext(), "Báo cáo đã được gửi thành công", Toast.LENGTH_SHORT).show();
                dismiss();
            }
            
            @Override
            public void onError(Exception e) {
                // Lỗi
                showError("Lỗi gửi báo cáo: " + e.getMessage());
                resetSubmitButton();
            }
        });
    }
    
    /**
     * Hiển thị lỗi và reset button
     */
    private void showError(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
    }
    
    /**
     * Reset trạng thái button submit
     */
    private void resetSubmitButton() {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("Gửi báo cáo");
    }
}
