/*
 * Copyright (C) 2019 The Turms Project
 * https://github.com/turms-im/turms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.turms.server.common.domain.blocklist.manager;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjLongConsumer;

import lombok.AllArgsConstructor;

import im.turms.server.common.infra.collection.CollectionUtil;
import im.turms.server.common.infra.property.env.common.security.AutoBlockItemProperties;
import im.turms.server.common.infra.property.env.common.security.AutoBlockItemProperties.BlockLevel;
import im.turms.server.common.infra.time.DateTimeUtil;

/**
 * @author James Chen
 */
public class AutoBlockManager<T> {

    private static final int UNSET_BLOCK_LEVEL = -1;

    private final ObjLongConsumer<T> onClientBlocked;

    private final boolean isEnabled;
    private final List<BlockLevel> levels;
    private final int maxLevel;
    private final int blockTriggerTimes;

    private final ConcurrentHashMap<T, BlockStatus> blockedClientIdToStatus;

    public AutoBlockManager(
            AutoBlockItemProperties autoBlockProperties,
            ObjLongConsumer<T> onClientBlocked) {
        this.onClientBlocked = onClientBlocked;
        levels = CollectionUtil.toListSupportRandomAccess(autoBlockProperties.getBlockLevels());
        isEnabled = autoBlockProperties.isEnabled() && !levels.isEmpty();
        if (!isEnabled) {
            blockedClientIdToStatus = null;
            maxLevel = UNSET_BLOCK_LEVEL;
            blockTriggerTimes = 0;
            return;
        }
        blockedClientIdToStatus = new ConcurrentHashMap<>(1024);
        maxLevel = levels.size() - 1;
        blockTriggerTimes = autoBlockProperties.getBlockTriggerTimes();
    }

    public void tryBlockClient(T id) {
        if (!isEnabled) {
            return;
        }
        blockedClientIdToStatus.compute(id, (key, status) -> {
            long now = System.nanoTime();
            if (status == null) {
                status = new BlockStatus(UNSET_BLOCK_LEVEL, null, 0, now);
            } else {
                status.lastBlockTriggerTimeNanos = now;
            }
            // Update status
            long previousBlockTriggerTimeNanos = status.lastBlockTriggerTimeNanos;
            int reduceOneTriggerTimeIntervalMillis =
                    status.currentLevelProperties.getReduceOneTriggerTimeIntervalMillis();
            int times = status.triggerTimes;
            if (reduceOneTriggerTimeIntervalMillis > 0) {
                times -= (int) ((status.lastBlockTriggerTimeNanos - previousBlockTriggerTimeNanos)
                        / (reduceOneTriggerTimeIntervalMillis * DateTimeUtil.NANOS_PER_MILLI));
                if (times < 0) {
                    times = 0;
                }
            }
            status.triggerTimes = times + 1;
            boolean isBlocked = status.currentLevel != UNSET_BLOCK_LEVEL;
            if (isBlocked) {
                if (status.triggerTimes >= status.currentLevelProperties
                        .getGoNextLevelTriggerTimes() && status.currentLevel < maxLevel) {
                    status.currentLevel++;
                    status.currentLevelProperties = levels.get(status.currentLevel);
                    status.triggerTimes = 0;
                }
                onClientBlocked.accept(id, status.currentLevelProperties.getBlockDurationSeconds());
            } else if (status.triggerTimes >= blockTriggerTimes) {
                status.currentLevel = 0;
                status.currentLevelProperties = levels.getFirst();
                status.triggerTimes = 0;
                onClientBlocked.accept(id, status.currentLevelProperties.getBlockDurationSeconds());
            } else {
                status.triggerTimes++;
            }
            return status;
        });
    }

    public void unblockClient(T id) {
        if (!isEnabled) {
            return;
        }
        blockedClientIdToStatus.remove(id);
    }

    public void evictExpiredBlockedClients() {
        if (!isEnabled) {
            return;
        }
        long now = System.nanoTime();
        Iterator<BlockStatus> iterator = blockedClientIdToStatus.values()
                .iterator();
        while (iterator.hasNext()) {
            BlockStatus status = iterator.next();
            int reduceOneTriggerTimeInterval =
                    status.currentLevelProperties.getReduceOneTriggerTimeIntervalMillis();
            if (reduceOneTriggerTimeInterval > 0) {
                int times = status.triggerTimes - (int) ((now - status.lastBlockTriggerTimeNanos)
                        / (reduceOneTriggerTimeInterval * DateTimeUtil.NANOS_PER_MILLI));
                if (times <= 0) {
                    iterator.remove();
                }
            }
        }
    }

    @AllArgsConstructor
    private static class BlockStatus {
        private int currentLevel;
        private BlockLevel currentLevelProperties;
        private int triggerTimes;
        private long lastBlockTriggerTimeNanos;
    }

}