package com.example.BegaDiary.Entity;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatViewCandidateCreateRequest {
    private List<String> storagePaths;
    private List<String> sourceTypes;
}
