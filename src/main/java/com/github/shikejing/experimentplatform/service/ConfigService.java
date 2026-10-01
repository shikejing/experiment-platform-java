package com.github.shikejing.experimentplatform.service;

import com.github.shikejing.experimentplatform.engine.SnapshotFactory;
import com.github.shikejing.experimentplatform.exceptions.InvalidConfigException;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.store.SnapshotStore;
import org.springframework.stereotype.Service;

/**
 * 全局配置服务（全局兜底等）。热更新整体替换不可变快照，
 * 校验失败时旧配置继续服务。
 */
@Service
public class ConfigService {

    private final SnapshotStore snapshotStore;
    private final SnapshotFactory snapshotFactory;

    public ConfigService(SnapshotStore snapshotStore, SnapshotFactory snapshotFactory) {
        this.snapshotStore = snapshotStore;
        this.snapshotFactory = snapshotFactory;
    }

    public void updateGlobal(GlobalConfig config) {
        if (config == null || config.getFallbackRateBps() < 0
                || config.getFallbackRateBps() > 10000) {
            throw new InvalidConfigException(
                    "fallbackRateBps must be in [0,10000]", null, null);
        }
        PublishedSnapshot current = snapshotStore.get();
        PublishedSnapshot next = snapshotFactory.create(current.getSnapshotId() + 1,
                current.getExperiments(), config);
        snapshotStore.casPublish(next);
    }

    public GlobalConfig current() {
        return snapshotStore.get().getGlobalConfig();
    }
}
