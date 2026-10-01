package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExposureEvent;
import com.github.shikejing.experimentplatform.model.ExposureResult;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 曝光事件幂等存储。以客户端生成的 eventId 为幂等键，
 * 重复上报（乱序到达、超时重试）直接返回首次处理结果，不重复计数。
 */
@Repository
public class InMemoryEventDedupStore implements EventDedupStore {

    private final Map<String, ExposureResult> seen = new ConcurrentHashMap<>();

    @Override
    public Optional<ExposureResult> remember(ExposureEvent event, ExposureResult result) {
        ExposureResult previous = seen.putIfAbsent(event.getEventId(), result);
        return Optional.ofNullable(previous);
    }
}
