package com.consultorprocessos.notification.channel;

public interface NotificationChannel {

    void send(NotificationPayload payload);

    String getChannelCode();
}