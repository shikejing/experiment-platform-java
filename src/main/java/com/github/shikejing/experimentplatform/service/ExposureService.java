package com.github.shikejing.experimentplatform.service;

import com.github.shikejing.experimentplatform.engine.ShuntEngine;
import com.github.shikejing.experimentplatform.exceptions.AssignmentMismatchException;
import com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException;
import com.github.shikejing.experimentplatform.exceptions.VariantNotFoundException;
import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.ExposureEvent;
import com.github.shikejing.experimentplatform.model.ExposureResult;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.store.AssignmentStore;
import com.github.shikejing.experimentplatform.store.EventDedupStore;
import com.github.shikejing.experimentplatform.store.MetricsStore;
import com.github.shikejing.experimentplatform.store.SnapshotStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 曝光/指标写入服务。
 * <p>
 * 四道防线保证"不重复计数、不错计"：
 * <ol>
 *   <li>字段校验：缺失必要字段直接拒绝；</li>
 *   <li>实验/变体必须存在于当前已发布版本，否则拒绝；</li>
 *   <li>命中校验：以分流记录为准，缺失时按当前快照确定性重算；
 *       未命中或变体不一致一律不计入任何变体；</li>
 *   <li>幂等去重：eventId 重复（乱序到达、超时重试）返回首次结果，
 *       只计数一次。</li>
 * </ol>
 */
@Service
public class ExposureService {

    private static final String DEFAULT_METRIC = "exposure";

    private final SnapshotStore snapshotStore;
    private final ShuntEngine shuntEngine;
    private final AssignmentStore assignmentStore;
    private final EventDedupStore dedupStore;
    private final MetricsStore metricsStore;

    public ExposureService(SnapshotStore snapshotStore, ShuntEngine shuntEngine,
                           AssignmentStore assignmentStore, EventDedupStore dedupStore,
                           MetricsStore metricsStore) {
        this.snapshotStore = snapshotStore;
        this.shuntEngine = shuntEngine;
        this.assignmentStore = assignmentStore;
        this.dedupStore = dedupStore;
        this.metricsStore = metricsStore;
    }

    public ExposureResult record(ExposureEvent event) {
        validate(event);

        PublishedSnapshot snapshot = snapshotStore.get();
        ExperimentVersion version = snapshot.getExperiments().get(event.getExperimentId());
        if (version == null) {
            throw new ExperimentNotFoundException(event.getExperimentId());
        }
        boolean variantExists = version.getVariants().stream()
                .anyMatch(v -> v.getName().equals(event.getVariantName()));
        if (!variantExists) {
            throw new VariantNotFoundException(event.getExperimentId(), event.getVariantName());
        }

        // 命中校验：分流记录优先；缺失则按当前快照确定性重算（支持乱序到达）
        Assignment assignment = assignmentStore
                .get(event.getUnitId(), event.getExperimentId())
                .orElse(null);
        if (assignment == null) {
            List<Assignment> computed = shuntEngine.shunt(event.getUnitId(), snapshot);
            assignment = computed.stream()
                    .filter(a -> a.getExperimentId().equals(event.getExperimentId()))
                    .findFirst()
                    .orElse(null);
            if (assignment != null) {
                assignmentStore.put(assignment);
            }
        }
        if (assignment == null
                || !assignment.getVariantName().equals(event.getVariantName())) {
            String expected = assignment == null ? null : assignment.getVariantName();
            // 未真正命中实验：不计入任何变体，异常链可定位到实验/变体/单位
            throw new AssignmentMismatchException(event.getExperimentId(),
                    event.getVariantName(), event.getUnitId(), expected);
        }

        // 幂等：eventId 去重，重复上报返回首次结果，不重复计数
        ExposureResult accepted = ExposureResult.accepted(event.getExperimentId(),
                event.getVariantName());
        Optional<ExposureResult> previous = dedupStore.remember(event, accepted);
        if (previous.isPresent()) {
            return ExposureResult.duplicateOf(previous.get());
        }

        // 只有真正命中且首次出现的事件才计入聚合
        String metric = event.getMetric() == null ? DEFAULT_METRIC : event.getMetric();
        metricsStore.record(event.getExperimentId(), event.getVariantName(), metric,
                event.getValue());
        return accepted;
    }

    private void validate(ExposureEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        if (isBlank(event.getEventId())) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (isBlank(event.getUnitId())) {
            throw new IllegalArgumentException("unitId is required");
        }
        if (isBlank(event.getExperimentId())) {
            throw new IllegalArgumentException("experimentId is required");
        }
        if (isBlank(event.getVariantName())) {
            throw new IllegalArgumentException("variantName is required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
