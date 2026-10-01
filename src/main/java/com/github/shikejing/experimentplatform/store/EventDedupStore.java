package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExposureEvent;
import com.github.shikejing.experimentplatform.model.ExposureResult;

import java.util.Optional;

/**
 * 曝光事件幂等存储：以 eventId 去重，重复上报返回首次处理结果。
 */
public interface EventDedupStore {
    /**
     * @return 首次出现返回 empty；重复事件返回此前结果
     */
    Optional<ExposureResult> remember(ExposureEvent event, ExposureResult result);
}
