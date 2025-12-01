package com.example.nanaclu.utils;

import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.example.nanaclu.utils.NoNetworkException;

/**
 * Tiện ích chung để log lỗi mạng trong các repository.
 *
 * Mục tiêu:
 * - Khi thực hiện các thao tác network (Firestore / Storage / API),
 *   nếu lỗi do mất mạng thì log ra: "có lỗi: không có mạng".
 * - Không dùng Context, không hiển thị Toast trong repository.
 * - Chỉ log, phần UI (Activity/Fragment/ViewModel) sẽ tự xử lý hiển thị nếu cần.
 *
 * Cách dùng (trong repository):
 *
 * 1) Trong addOnFailureListener
 *    ---------------------------
 *    ví dụ Firestore:
 *
 *    db.collection("posts")
 *        .add(data)
 *        .addOnSuccessListener(docRef -> { // ... })
 *        .addOnFailureListener(e -> {
 *            // Log lỗi mạng nếu có
 *            NetworkErrorLogger.logIfNoNetwork("PostRepository", e);
 *
 *            // Phần còn lại giữ nguyên logic cũ
 *            callback.onError(e);
 *        });
 *
 * 2) Trong continueWithTask / continueWith
 *    --------------------------------------
 *    ví dụ:
 *
 *    return someTask.continueWithTask(task -> {
 *        if (!task.isSuccessful()) {
 *            Exception e = task.getException();
 *            // Log lỗi mạng nếu có
 *            NetworkErrorLogger.logIfNoNetwork("PostRepository", e);
 *
 *            return Tasks.forException(e);
 *        }
 *        // Tiếp tục luồng bình thường
 *        return Tasks.forResult(task.getResult());
 *    });
 *
 * 3) Nếu bạn đã có NoNetworkException riêng
 *    --------------------------------------
 *    Nếu ở repository bạn ném ra NoNetworkException (ví dụ từ AuthRepository),
 *    có thể log theo cùng 1 chỗ ở ViewModel/Activity:
 *
 *    try {
 *        // gọi repository
 *    } catch (Exception e) {
 *        NetworkErrorLogger.logIfNoNetwork("AuthViewModel", e);
 *        // xử lý UI tiếp theo
 *    }
 */
public final class NetworkErrorLogger {

    private NetworkErrorLogger() {
        // Utility class, không cho tạo instance
    }

    /**
     * Kiểm tra xem exception có phải là lỗi mạng không và trả về thông báo thân thiện.
     * Nếu là lỗi mạng -> "Có lỗi: không có mạng"
     * Nếu không -> null (để caller dùng message gốc)
     *
     * @param e Exception nhận được
     * @return String thông báo thân thiện hoặc null nếu không phải lỗi mạng
     */
    public static String getNetworkErrorMessage(Exception e) {
        if (e == null) return null;

        // Trường hợp Firebase network lỗi trực tiếp
        if (e instanceof FirebaseNetworkException) {
            return "Có lỗi: không có mạng";
        }

        // Nếu project có NoNetworkException riêng
        if (e instanceof NoNetworkException) {
            return "Có lỗi: không có mạng";
        }

        // Kiểm tra message có chứa các từ khóa về lỗi mạng không
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("client is offline") ||
                lowerMessage.contains("failed to get document because client is offline") ||
                lowerMessage.contains("network error") ||
                lowerMessage.contains("connection") && lowerMessage.contains("failed") ||
                lowerMessage.contains("unreachable") ||
                lowerMessage.contains("timeout") && lowerMessage.contains("network")) {
                return "Có lỗi: không có mạng";
            }
        }

        return null; // Không phải lỗi mạng
    }

    /**
     * Log "có lỗi: không có mạng" nếu exception là lỗi mạng.
     *
     * @param tag Tag dùng cho android.util.Log (nên truyền tên repository hoặc ViewModel)
     * @param e   Exception nhận được trong onFailure / continueWithTask
     */
    public static void logIfNoNetwork(String tag, Exception e) {
        if (e == null) return;

        // Trường hợp Firebase network lỗi trực tiếp
        if (e instanceof FirebaseNetworkException) {
            Log.e(tag, "có lỗi: không có mạng", e);
            return;
        }

        // Nếu project có NoNetworkException riêng, cũng log tương tự
        if (e instanceof NoNetworkException) {
            Log.e(tag, "có lỗi: không có mạng", e);
        }

        // Kiểm tra message có chứa từ khóa lỗi mạng
        String message = e.getMessage();
        if (message != null) {
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("client is offline") ||
                lowerMessage.contains("failed to get document because client is offline") ||
                lowerMessage.contains("network error") ||
                lowerMessage.contains("connection") && lowerMessage.contains("failed") ||
                lowerMessage.contains("unreachable") ||
                lowerMessage.contains("timeout") && lowerMessage.contains("network")) {
                Log.e(tag, "có lỗi: không có mạng", e);
            }
        }
    }
}
