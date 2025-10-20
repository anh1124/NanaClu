package com.example.nanaclu.ui.group.statistics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.example.nanaclu.data.model.GroupStatistics;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter cho danh sách thống kê
 */
public class StatisticsAdapter extends RecyclerView.Adapter<StatisticsAdapter.StatisticsViewHolder> {

    private List<GroupStatistics> statisticsList = new ArrayList<>();
    private boolean isMonthlyView = true;

    public void setStatisticsList(List<GroupStatistics> statisticsList) {
        this.statisticsList = statisticsList != null ? statisticsList : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setMonthlyView(boolean isMonthlyView) {
        this.isMonthlyView = isMonthlyView;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public StatisticsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_statistics, parent, false);
        return new StatisticsViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StatisticsViewHolder holder, int position) {
        GroupStatistics stats = statisticsList.get(position);
        holder.bind(stats);
    }

    @Override
    public int getItemCount() {
        return statisticsList.size();
    }

    class StatisticsViewHolder extends RecyclerView.ViewHolder {
        private TextView tvPeriodLabel;
        private TextView tvPeriodRange;
        private TextView tvPostCount;
        private TextView tvEventCount;
        private TextView tvMemberCount;

        public StatisticsViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPeriodLabel = itemView.findViewById(R.id.tvPeriodLabel);
            tvPeriodRange = itemView.findViewById(R.id.tvPeriodRange);
            tvPostCount = itemView.findViewById(R.id.tvPostCount);
            tvEventCount = itemView.findViewById(R.id.tvEventCount);
            tvMemberCount = itemView.findViewById(R.id.tvMemberCount);
        }

        public void bind(GroupStatistics stats) {
            if (isMonthlyView) {
                // Format cho tháng: "2025-01" -> "Tháng 1/2025"
                String[] parts = stats.periodKey.split("-");
                if (parts.length == 2) {
                    int year = Integer.parseInt(parts[0]);
                    int month = Integer.parseInt(parts[1]);
                    
                    tvPeriodLabel.setText(String.format(Locale.getDefault(), "Tháng %d/%d", month, year));
                    
                    // Tạo range ngày
                    Calendar cal = Calendar.getInstance();
                    cal.set(year, month - 1, 1);
                    Date startDate = cal.getTime();
                    
                    cal.set(year, month - 1, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                    Date endDate = cal.getTime();
                    
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    String range = dateFormat.format(startDate) + " - " + dateFormat.format(endDate);
                    tvPeriodRange.setText(range);
                } else {
                    tvPeriodLabel.setText(stats.periodKey);
                    tvPeriodRange.setText("");
                }
            } else {
                // Format cho năm: "2025" -> "Năm 2025"
                tvPeriodLabel.setText("Năm " + stats.periodKey);
                tvPeriodRange.setText("01/01/" + stats.periodKey + " - 31/12/" + stats.periodKey);
            }

            tvPostCount.setText(String.valueOf(stats.postCount));
            tvEventCount.setText(String.valueOf(stats.eventCount));
            tvMemberCount.setText(String.valueOf(stats.newMemberCount));
        }
    }
}
