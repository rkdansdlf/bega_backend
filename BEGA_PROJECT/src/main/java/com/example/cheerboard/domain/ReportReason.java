package com.example.cheerboard.domain;

public enum ReportReason {
    SPAM("스팸/홍보"),
    INAPPROPRIATE_CONTENT("부적절한 콘텐츠"),
    ABUSIVE_LANGUAGE("욕설/비하 발언"),
    ADVERTISEMENT("상업적 광고"),
    COPYRIGHT_INFRINGEMENT("저작권/권리 침해"),
    FAKE_INFORMATION("허위 정보/사기성 게시"),
    OTHER("기타");

    private final String description;

    ReportReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
