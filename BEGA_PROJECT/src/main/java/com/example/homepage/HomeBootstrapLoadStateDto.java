package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequest;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBootstrapLoadStateDto {

    private Boolean isFallback;
    private Boolean timedOut;
    private List<String> timedOutSections;
    private List<String> failedSections;
    private String failureReason;
    private ManualBaseballDataRequest manualDataRequest;
}
