package com.example.homepage;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBootstrapLoadStateDto {

    private Boolean isFallback;
    private Boolean timedOut;
    private List<String> timedOutSections;
    private List<String> failedSections;
}
