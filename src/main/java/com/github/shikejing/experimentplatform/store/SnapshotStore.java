package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.PublishedSnapshot;

/**
 * 已发布快照的原子持有者。读取侧无锁获取不可变快照，
 * 发布/回滚整体替换，失败时旧快照继续服务。
 */
public interface SnapshotStore {
    PublishedSnapshot get();

    void casPublish(PublishedSnapshot newSnapshot);
}
