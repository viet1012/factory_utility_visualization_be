package com.example.factory_utility_visualization_be.request;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilitySeriesRequest {

    private LocalDateTime from;
    private LocalDateTime to;

    // optional filters
    private String facId;
    private String scadaId;
    private String cate;

    // nếu muốn lấy nhiều param cùng lúc
    private List<SeriesParamKey> params;

    // gộp theo thời gian (optional) - "RAW" | "HOUR" | "DAY"
    private String bucket;
    private Boolean isImportant;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeriesParamKey {
        private String boxDeviceId;
        private String plcAddress;
    }
}
