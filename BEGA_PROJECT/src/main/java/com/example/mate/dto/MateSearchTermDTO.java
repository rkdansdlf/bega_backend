package com.example.mate.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class MateSearchTermDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordRequest {
        private String term;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PopularResponse {
        private String term;
        private Long count;
        private Integer rank;
    }
}
