package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.engine.Bucketer;
import com.github.shikejing.experimentplatform.engine.ShuntEngine;
import com.github.shikejing.experimentplatform.engine.SnapshotFactory;
import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import com.github.shikejing.experimentplatform.model.Variant;
import com.github.shikejing.experimentplatform.service.ConfigService;
import com.github.shikejing.experimentplatform.service.ExposureService;
import com.github.shikejing.experimentplatform.service.ExperimentService;
import com.github.shikejing.experimentplatform.service.ShuntService;
import com.github.shikejing.experimentplatform.service.StatsService;
import com.github.shikejing.experimentplatform.store.AtomicSnapshotStore;
import com.github.shikejing.experimentplatform.store.InMemoryAssignmentStore;
import com.github.shikejing.experimentplatform.store.InMemoryDraftStore;
import com.github.shikejing.experimentplatform.store.InMemoryEventDedupStore;
import com.github.shikejing.experimentplatform.store.InMemoryMetricsStore;
import com.github.shikejing.experimentplatform.store.InMemoryVersionStore;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试支撑：直接装配内存实现，不依赖 Spring 容器。
 */
public class TestSupport {

    public final InMemoryVersionStore versionStore = new InMemoryVersionStore();
    public final InMemoryDraftStore draftStore = new InMemoryDraftStore();
    public final InMemoryAssignmentStore assignmentStore = new InMemoryAssignmentStore();
    public final InMemoryEventDedupStore dedupStore = new InMemoryEventDedupStore();
    public final InMemoryMetricsStore metricsStore = new InMemoryMetricsStore();
    public final AtomicSnapshotStore snapshotStore = new AtomicSnapshotStore();
    public final SnapshotFactory snapshotFactory = new SnapshotFactory();
    public final Bucketer bucketer = new Bucketer();
    public final ShuntEngine shuntEngine = new ShuntEngine(bucketer);
    public final ExperimentService experimentService = new ExperimentService(
            draftStore, versionStore, snapshotStore, snapshotFactory);
    public final ShuntService shuntService = new ShuntService(
            shuntEngine, snapshotStore, assignmentStore);
    public final ExposureService exposureService = new ExposureService(
            snapshotStore, shuntEngine, assignmentStore, dedupStore, metricsStore);
    public final StatsService statsService = new StatsService(snapshotStore, metricsStore);
    public final ConfigService configService = new ConfigService(snapshotStore, snapshotFactory);

    public static Variant variant(String name, int weightBps) {
        return new Variant(name, weightBps);
    }

    public static ExperimentConfig config(String experimentId, String name, String layerId,
                                          int layerWeightBps, String salt,
                                          Variant... variants) {
        ExperimentConfig c = new ExperimentConfig();
        c.setExperimentId(experimentId);
        c.setName(name);
        c.setLayerId(layerId);
        c.setLayerWeightBps(layerWeightBps);
        c.setSalt(salt);
        c.setVariants(new ArrayList<>(List.of(variants)));
        return c;
    }

    /**
     * 快速发布一个实验。
     */
    public int publish(ExperimentConfig config) {
        experimentService.saveDraft(config);
        return experimentService.publish(config.getExperimentId()).getVersion();
    }
}
