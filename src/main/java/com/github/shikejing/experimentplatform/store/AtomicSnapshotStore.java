package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.GlobalConfig;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 AtomicReference 的不可变快照持有者。
 * <p>
 * 读取侧永远拿到某一完整快照；发布/回滚通过 CAS 整体替换，
 * 任何时刻都不存在"读一半新配置、读一半旧配置"的状态；
 * 替换失败时旧快照继续提供服务。
 */
@Repository
public class AtomicSnapshotStore implements SnapshotStore {

    private final AtomicReference<PublishedSnapshot> ref;

    public AtomicSnapshotStore() {
        GlobalConfig fallback = new GlobalConfig();
        fallback.setFallbackRateBps(0);
        fallback.setFallbackSalt("global_fallback");
        this.ref = new AtomicReference<>(
                new PublishedSnapshot(0L, Map.of(), Map.of(), Map.of(), fallback));
    }

    @Override
    public PublishedSnapshot get() {
        return ref.get();
    }

    @Override
    public void casPublish(PublishedSnapshot newSnapshot) {
        PublishedSnapshot current;
        do {
            current = ref.get();
        } while (!ref.compareAndSet(current, newSnapshot));
    }
}
