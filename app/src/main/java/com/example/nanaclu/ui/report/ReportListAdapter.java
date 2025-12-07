package com.example.nanaclu.ui.report;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.ReportModel;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter hiển thị danh sách báo cáo trong dashboard
 * Hiển thị thông tin cơ bản của report và cho phép click để xem chi tiết
 */
public class ReportListAdapter extends RecyclerView.Adapter<ReportViewHolder> {

    public interface ReportClickListener {
        void onReportClick(ReportModel report);
    }

    private List<ReportModel> reports = new ArrayList<>();
    private final ReportClickListener clickListener;
    private Map<String, String> userNameCache = new HashMap<>();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    public ReportListAdapter(List<ReportModel> reports, ReportClickListener clickListener) {
        this.reports = reports != null ? reports : new ArrayList<>();
        this.clickListener = clickListener;
    }

    public void setItems(List<ReportModel> newItems) {
        reports.clear();
        if (newItems != null) {
            reports.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(reports.get(position), clickListener);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

}
