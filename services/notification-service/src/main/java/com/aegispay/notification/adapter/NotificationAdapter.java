package com.aegispay.notification.adapter;

public interface NotificationAdapter {
    void send(String recipient, String title, String body);
    String channel();
}
