package com.example.percobaan2;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationBlockerService extends NotificationListenerService {

    private static final String TAG = "NotificationBlocker";
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Log.d(TAG, "NotificationBlockerService started");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            // Log notifikasi yang diterima
            Log.d(TAG, "Notification received from: " + sbn.getPackageName());

            // Batalkan semua notifikasi yang masuk
            cancelNotification(sbn.getKey());

            // Alternatif: batalkan berdasarkan package name
            // if (!sbn.getPackageName().equals(getPackageName())) {
            //     cancelNotification(sbn.getKey());
            // }

        } catch (Exception e) {
            Log.e(TAG, "Error handling notification", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: log ketika notifikasi dihapus
        Log.d(TAG, "Notification removed from: " + sbn.getPackageName());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Service akan restart jika dibunuh sistem
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NotificationBlockerService destroyed");
    }

    // Method untuk membersihkan semua notifikasi yang ada
    public void clearAllNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                StatusBarNotification[] notifications = getActiveNotifications();
                for (StatusBarNotification notification : notifications) {
                    if (!notification.getPackageName().equals(getPackageName())) {
                        cancelNotification(notification.getKey());
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error clearing all notifications", e);
        }
    }
}