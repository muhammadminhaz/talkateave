package com.muhammadminhaz.talkateeve.repository;

import com.muhammadminhaz.talkateeve.model.BotQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BotQueryRepository extends JpaRepository<BotQuery, UUID> {

    long countByBotIdIn(Collection<UUID> botIds);

    /**
     * Daily counts per bot for the dashboard chart. Returns [botId, day, count] rows;
     * days with no traffic are simply absent, and the caller zero-fills them so the
     * chart shows a continuous series rather than a broken line.
     */
    @Query("""
            select q.botId, function('date', q.askedAt), count(q)
            from BotQuery q
            where q.botId in :botIds and q.askedAt >= :since
            group by q.botId, function('date', q.askedAt)
            """)
    List<Object[]> countDailyByBot(@Param("botIds") Collection<UUID> botIds, @Param("since") Instant since);
}
