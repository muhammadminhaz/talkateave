package com.muhammadminhaz.talkateeve.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Everything the dashboard Overview needs, in one round trip. The frontend used to
 * hardcode all of these numbers.
 */
@Data
@AllArgsConstructor
public class DashboardStatsResponse {

    private int activeBots;
    private long totalInteractions;
    private long totalDocuments;

    /** ISO dates, oldest first, one entry per day in the window. */
    private List<String> days;

    private List<BotSeries> series;

    @Data
    @AllArgsConstructor
    public static class BotSeries {
        private String botId;
        private String name;
        /** Query counts aligned index-for-index with {@code days}. */
        private List<Long> data;
    }
}
