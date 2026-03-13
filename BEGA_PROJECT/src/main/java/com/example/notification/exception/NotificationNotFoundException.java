package com.example.notification.exception;

import com.example.common.exception.NotFoundBusinessException;

public class NotificationNotFoundException extends NotFoundBusinessException {
    
    public NotificationNotFoundException(Long notificationId) {
        super("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다. ID: " + notificationId);
    }
    
    public NotificationNotFoundException(String message) {
        super("NOTIFICATION_NOT_FOUND", message);
    }
}
