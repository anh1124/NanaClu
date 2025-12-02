package com.example.nanaclu.ui.post;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.nanaclu.R;
import com.google.firebase.firestore.FirebaseFirestore;

public class PollResultsActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "groupId";
    public static final String EXTRA_POST_ID = "postId";

    private LinearLayout container;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poll_results);
        container = findViewById(R.id.container);
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String postId = getIntent().getStringExtra(EXTRA_POST_ID);
        loadResults(groupId, postId);
    }

    private void loadResults(String groupId, String postId) {
        FirebaseFirestore.getInstance()
                .collection("groups").document(groupId)
                .collection("posts").document(postId)
                .collection("options")
                .orderBy("createdAt")
                .get()
                .addOnSuccessListener(snap -> {
                    container.removeAllViews();
                    long total = 0L;
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        Long c = d.getLong("voteCount"); total += c != null ? c : 0L;
                    }
                    for (com.google.firebase.firestore.DocumentSnapshot d : snap.getDocuments()) {
                        String text = d.getString("text");
                        Long c = d.getLong("voteCount"); long count = c != null ? c : 0L;
                        int percent = total > 0 ? (int)(count * 100 / total) : 0;
                        TextView tv = new TextView(this);
                        tv.setText((text != null ? text : "") + " — " + count + " phiếu (" + percent + "%)");
                        tv.setTextSize(16f);
                        int pad = (int)(8*getResources().getDisplayMetrics().density);
                        tv.setPadding(0, pad, 0, pad);
                        container.addView(tv);
                    }
                });
    }
}
