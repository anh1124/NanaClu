package com.example.nanaclu.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.nanaclu.ui.post.PostDetailActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ShareLinkUtils {
    
    private static final String POST_LINK_PREFIX = "@post:";
    
    /**
     * Copy post link to clipboard
     * @param context Context
     * @param postId Post ID
     */
    public static void copyPostLink(Context context, String postId) {
        if (context == null || postId == null || postId.isEmpty()) {
            return;
        }
        
        String link = POST_LINK_PREFIX + postId;
        
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Post Link", link);
        clipboard.setPrimaryClip(clip);
        
        Toast.makeText(context, "Đã sao chép link bài viết", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Parse post link to extract post ID
     * @param link Link to parse
     * @return Post ID or null if invalid
     */
    public static String parsePostLink(String link) {
        if (link == null || link.isEmpty()) {
            return null;
        }
        
        link = link.trim();
        if (link.startsWith(POST_LINK_PREFIX)) {
            return link.substring(POST_LINK_PREFIX.length());
        }
        
        return null;
    }
    
    /**
     * Open post link by checking permissions and navigating to PostDetailActivity
     * @param activity Activity context
     * @param link Link to open
     */
    public static void openPostLink(Activity activity, String link) {
        if (activity == null || link == null || link.isEmpty()) {
            return;
        }
        
        String postId = parsePostLink(link);
        if (postId == null) {
            Toast.makeText(activity, "Link không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("ShareLinkUtils", "Opening post link: " + link + ", postId: " + postId);
        
        // Get current user
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null 
            ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        
        if (currentUserId == null) {
            Toast.makeText(activity, "Vui lòng đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("ShareLinkUtils", "Current user ID: " + currentUserId);
        
        // Use a flag to track if post was found
        final boolean[] postFound = {false};
        
        // Set a timeout to show error if no post found
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!postFound[0]) {
                android.util.Log.d("ShareLinkUtils", "Timeout: Post not found in any group");
                Toast.makeText(activity, "Không tìm thấy bài viết", Toast.LENGTH_SHORT).show();
            }
        }, 5000); // 5 second timeout
        
        // Query all groups to find which one contains this post
        FirebaseFirestore.getInstance()
            .collection("groups")
            .get()
            .addOnSuccessListener(groupsSnapshot -> {
                android.util.Log.d("ShareLinkUtils", "Found " + groupsSnapshot.size() + " groups");
                
                // Search through all groups to find the one containing this post
                for (com.google.firebase.firestore.DocumentSnapshot groupDoc : groupsSnapshot.getDocuments()) {
                    String currentGroupId = groupDoc.getId();
                    android.util.Log.d("ShareLinkUtils", "Checking group: " + currentGroupId);
                    
                    // Check if this group contains the post
                    FirebaseFirestore.getInstance()
                        .collection("groups")
                        .document(currentGroupId)
                        .collection("posts")
                        .document(postId)
                        .get()
                        .addOnSuccessListener(postDoc -> {
                            android.util.Log.d("ShareLinkUtils", "Post exists in group " + currentGroupId + ": " + postDoc.exists());
                            if (postDoc.exists()) {
                                postFound[0] = true; // Mark as found
                                // Found the post, now check membership
                                checkMembershipAndOpenPost(activity, currentGroupId, postId, currentUserId);
                            }
                        })
                        .addOnFailureListener(e -> {
                            android.util.Log.e("ShareLinkUtils", "Error checking post in group " + currentGroupId, e);
                        });
                }
                
                android.util.Log.d("ShareLinkUtils", "Finished checking all groups");
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ShareLinkUtils", "Error getting groups", e);
                Toast.makeText(activity, "Lỗi tìm bài viết", Toast.LENGTH_SHORT).show();
            });
    }
    
    private static void checkMembershipAndOpenPost(Activity activity, String groupId, String postId, String currentUserId) {
        android.util.Log.d("ShareLinkUtils", "Checking membership for group: " + groupId + ", user: " + currentUserId);
        
        // Check if user is member of the group
        FirebaseFirestore.getInstance()
            .collection("groups")
            .document(groupId)
            .collection("members")
            .document(currentUserId)
            .get()
            .addOnSuccessListener(memberDoc -> {
                android.util.Log.d("ShareLinkUtils", "Membership check result: " + memberDoc.exists());
                if (memberDoc.exists()) {
                    // User is member, open post
                    android.util.Log.d("ShareLinkUtils", "Opening PostDetailActivity with groupId: " + groupId + ", postId: " + postId);
                    Intent intent = new Intent(activity, PostDetailActivity.class);
                    intent.putExtra(PostDetailActivity.EXTRA_GROUP_ID, groupId);
                    intent.putExtra(PostDetailActivity.EXTRA_POST_ID, postId);
                    activity.startActivity(intent);
                } else {
                    // User is not member
                    android.util.Log.d("ShareLinkUtils", "User is not a member of group: " + groupId);
                    Toast.makeText(activity, "Chỉ thành viên của group mới được xem", Toast.LENGTH_SHORT).show();
                }
            })
            .addOnFailureListener(e -> {
                android.util.Log.e("ShareLinkUtils", "Error checking membership", e);
                Toast.makeText(activity, "Lỗi kiểm tra quyền truy cập", Toast.LENGTH_SHORT).show();
            });
    }
}
