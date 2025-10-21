package com.example.nanaclu.utils;

/**
 * Utility class để theo dõi màn hình và chat đang active
 * Dùng để quyết định có hiển thị banner thông báo hay không
 */
public class ActiveScreenTracker {
    private static String activeScreen;
    private static String activeChatId;

    /**
     * Set màn hình đang active
     */
    public static void setActiveScreen(String screenName) {
        activeScreen = screenName;
    }

    /**
     * Get màn hình đang active
     */
    public static String getActiveScreen() {
        return activeScreen;
    }

    /**
     * Set chat ID đang active (null nếu không ở chat room)
     */
    public static void setActiveChatId(String chatId) {
        activeChatId = chatId;
    }

    /**
     * Get chat ID đang active
     */
    public static String getActiveChatId() {
        return activeChatId;
    }

    /**
     * Kiểm tra có đang ở chat room cụ thể không
     */
    public static boolean isInChatRoom(String chatId) {
        return chatId != null && chatId.equals(activeChatId);
    }

    /**
     * Clear tất cả state
     */
    public static void clear() {
        activeScreen = null;
        activeChatId = null;
    }
}
