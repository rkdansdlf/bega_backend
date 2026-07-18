package com.example.common.realtime;

/**
 * Strict transport used by durable relays. A successful return means that the
 * envelope was accepted by the configured fan-out transport.
 */
public interface RealtimeMessageTransport {

    void publish(RealtimeMessageEnvelope envelope);
}
