package com.example.nanaclu.ui.group;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.nanaclu.R;
import com.example.nanaclu.data.model.User;
import com.example.nanaclu.data.repository.UserRepository;
import com.example.nanaclu.ui.profile.ProfileActivity;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserSelectAdapter extends BaseAdapter {
    private final Context context;
    private final LayoutInflater inflater;
    private final List<String> userIds = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Map<String, User> cache = new HashMap<>();
    private final UserRepository userRepository;
    private String currentUserId;

    public UserSelectAdapter(Context context, List<String> ids, String currentUserId) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
        if (ids != null) this.userIds.addAll(ids);
        this.currentUserId = currentUserId;
        this.userRepository = new UserRepository(FirebaseFirestore.getInstance());
    }

    public void setItems(List<String> ids) {
        this.userIds.clear();
        if (ids != null) this.userIds.addAll(ids);
        notifyDataSetChanged();
    }

    public void setSelectedAll(boolean checked) {
        if (checked) selected.addAll(userIds); else selected.clear();
        notifyDataSetChanged();
    }

    public List<String> getSelected() {
        return new ArrayList<>(selected);
    }

    @Override public int getCount() { return userIds.size(); }
    @Override public Object getItem(int position) { return userIds.get(position); }
    @Override public long getItemId(int position) { return position; }

    static class ViewHolder {
        ImageView ivAvatar; TextView tvName; TextView tvSub; CheckBox cb;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder h;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_user_select, parent, false);
            h = new ViewHolder();
            h.ivAvatar = convertView.findViewById(R.id.ivAvatar);
            h.tvName = convertView.findViewById(R.id.tvName);
            h.tvSub = convertView.findViewById(R.id.tvSub);
            h.cb = convertView.findViewById(R.id.cbSelect);
            convertView.setTag(h);
        } else {
            h = (ViewHolder) convertView.getTag();
        }

        String uid = userIds.get(position);
        h.cb.setOnCheckedChangeListener(null);
        h.cb.setChecked(selected.contains(uid));

        User u = cache.get(uid);
        if (u != null) {
            bindUser(h, u, uid);
        } else {
            h.tvName.setText("...");
            h.tvSub.setText("");
            userRepository.getUserById(uid, new UserRepository.UserCallback() {
                @Override public void onSuccess(User user) {
                    cache.put(uid, user);
                    bindUser(h, user, uid);
                }
                @Override public void onError(Exception e) {
                    User fallback = new User();
                    fallback.displayName = uid;
                    fallback.photoUrl = null;
                    cache.put(uid, fallback);
                    bindUser(h, fallback, uid);
                }
            });
        }

        View nameArea = convertView;
        nameArea.setOnClickListener(v -> {
            // Toggle selection when row clicked
            if (selected.contains(uid)) selected.remove(uid); else selected.add(uid);
            notifyDataSetChanged();
        });

        h.ivAvatar.setOnClickListener(v -> openProfile(uid));
        h.tvName.setOnClickListener(v -> openProfile(uid));

        h.cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) selected.add(uid); else selected.remove(uid);
        });

        return convertView;
    }

    private void bindUser(ViewHolder h, User user, String uid) {
        String name = user != null && user.displayName != null ? user.displayName : uid;
        if (uid != null && uid.equals(currentUserId)) {
            name = name + context.getString(R.string.label_tag_you);
        }
        h.tvName.setText(name);
        h.tvSub.setText(""); // could be email or role if needed
        if (user != null && user.photoUrl != null && !user.photoUrl.isEmpty()) {
            Glide.with(context).load(user.photoUrl).circleCrop()
                    .placeholder(R.drawable.ic_account_circle_24)
                    .error(R.drawable.ic_account_circle_24)
                    .into(h.ivAvatar);
        } else {
            h.ivAvatar.setImageResource(R.drawable.ic_account_circle_24);
        }
    }

    private void openProfile(String userId) {
        Intent intent = new Intent(context, ProfileActivity.class);
        intent.putExtra("userId", userId);
        context.startActivity(intent);
    }
}

