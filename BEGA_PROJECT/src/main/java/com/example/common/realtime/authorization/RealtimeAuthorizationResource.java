package com.example.common.realtime.authorization;

enum RealtimeAuthorizationResource {
    PARTY("party"),
    DM("dm");

    private final String tag;

    RealtimeAuthorizationResource(String tag) {
        this.tag = tag;
    }

    String tag() {
        return tag;
    }
}
