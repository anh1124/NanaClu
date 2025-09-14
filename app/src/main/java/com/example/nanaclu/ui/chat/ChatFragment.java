package com.example.nanaclu.ui.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.nanaclu.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class ChatFragment extends Fragment {

    private RecyclerView rv;
    private ChatThreadAdapter adapter;
    private final List<ChatThread> allThreads = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_chat, container, false);

        MaterialToolbar toolbar = root.findViewById(R.id.toolbar);
        // Bold purple title
        android.text.SpannableString s = new android.text.SpannableString("Chats");
        s.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, s.length(), 0);
        toolbar.setTitle(s);
        toolbar.setTitleTextColor(0xFF6200EE);

        toolbar.inflateMenu(R.menu.menu_chat);
        // Replace menu item with custom action view to control icon size
        android.view.MenuItem item = toolbar.getMenu().findItem(R.id.action_new_chat);
        android.view.LayoutInflater.from(getContext());
        android.view.View action = android.view.LayoutInflater.from(getContext()).inflate(R.layout.menu_action_new_chat, toolbar, false);
        item.setActionView(action);
        android.view.View btn = action.findViewById(R.id.btnNewChat);
        btn.setOnClickListener(v -> android.widget.Toast.makeText(getContext(), "New chat", android.widget.Toast.LENGTH_SHORT).show());

        EditText edtSearch = root.findViewById(R.id.edtSearch);
        rv = root.findViewById(R.id.rvChatThreads);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new ChatThreadAdapter(new ArrayList<>(), this::showThreadActions);
        rv.setAdapter(adapter);

        // Demo data
        seedDemo();
        adapter.setItems(new ArrayList<>(allThreads));

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filter(s.toString());
            }
        });
        return root;
    }

    private void seedDemo() {
        allThreads.clear();
        allThreads.add(new ChatThread("Alice", "Hôm nay đi học không?", System.currentTimeMillis() - 60_000));
        allThreads.add(new ChatThread("Group Family", "Mẹ: Ăn cơm nhé!", System.currentTimeMillis() - 3_600_000));
        allThreads.add(new ChatThread("Bob", "OK", System.currentTimeMillis() - 10_000));
        allThreads.add(new ChatThread("Design Team", "New mockup attached.", System.currentTimeMillis() - 86_400_000));
    }

    private void filter(String q) {
        List<ChatThread> filtered = new ArrayList<>();
        for (ChatThread t : allThreads) {
            if (t.name.toLowerCase().contains(q.toLowerCase())) filtered.add(t);
        }
        adapter.setItems(filtered);
    }

    private void showThreadActions(ChatThread thread) {
        if (getContext() == null) return;
        BottomSheetDialog dialog = new BottomSheetDialog(getContext());
        View v = View.inflate(getContext(), R.layout.bottomsheet_chat_actions, null);
        v.findViewById(R.id.btnDelete).setOnClickListener(x -> { dialog.dismiss(); });
        v.findViewById(R.id.btnMute).setOnClickListener(x -> { dialog.dismiss(); });
        dialog.setContentView(v);
        dialog.show();
    }

    // ===== Models & Adapter =====
    static class ChatThread {
        String name;
        String lastMessage;
        long time;
        String avatarUrl; // optional
        ChatThread() {}
        ChatThread(String n, String lm, long t) { name=n; lastMessage=lm; time=t; }
    }

    static class ChatThreadAdapter extends RecyclerView.Adapter<ChatThreadAdapter.VH> {
        interface ThreadListener { void onLongPress(ChatThread t); }
        private final List<ChatThread> items;
        private final ThreadListener listener;
        ChatThreadAdapter(List<ChatThread> items, ThreadListener l) { this.items = items; this.listener = l; }
        void setItems(List<ChatThread> list) { items.clear(); items.addAll(list); notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = View.inflate(parent.getContext(), R.layout.item_chat_thread, null);
            return new VH(v);
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) { h.bind(items.get(pos), listener); }
        @Override public int getItemCount() { return items.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView avatar; TextView name; TextView sub; TextView time;
            VH(@NonNull View itemView) { super(itemView);
                avatar = itemView.findViewById(R.id.imgAvatar);
                name = itemView.findViewById(R.id.tvName);
                sub = itemView.findViewById(R.id.tvSub);
                time = itemView.findViewById(R.id.tvTime);
            }
            void bind(ChatThread t, ThreadListener l) {
                name.setText(t.name);
                sub.setText(t.lastMessage);
                time.setText(android.text.format.DateUtils.getRelativeTimeSpanString(t.time));
                itemView.setOnLongClickListener(v -> { if (l != null) l.onLongPress(t); return true; });
            }
        }
    }
}

