package com.example.common.realtime;

public interface RealtimeMessagePublisher {

    void broadcast(String destination, Object payload);

    void sendToUser(String userId, String destination, Object payload);
}
