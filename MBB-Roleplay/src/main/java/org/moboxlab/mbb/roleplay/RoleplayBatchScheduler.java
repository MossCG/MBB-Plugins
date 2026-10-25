package org.moboxlab.mbb.roleplay;

import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 群消息批处理调度
 *
 * 负责合批窗口、批次刷新、同群串行和过期消息过滤。
 */
public class RoleplayBatchScheduler {
    private final RoleplayService service;

    public RoleplayBatchScheduler(RoleplayService service) {
        this.service = service;
    }

    public void handle(GroupMessageEvent event) {
        if (event == null || !service.config().enable) return;
        if (event.getMessageID() > 0 && service.mergedMessageIDs.contains(event.getMessageID())) return;
        long groupID = event.getGroupID();
        if (groupID <= 0) {
            service.processGroupMessage(event);
            return;
        }
        if (service.toolLoopService().captureLiveMessage(event)) return;
        boolean scheduled;
        boolean meaningfulText = service.hasMeaningfulText(event.getMessage());
        boolean pureMedia = !meaningfulText
                && (service.hasImageContent(event.getMessage())
                || service.hasStickerContent(event.getMessage()));
        synchronized (service.groupQueueLock) {
            List<GroupMessageEvent> buffer = service.batchBufferMap.get(groupID);
            if (buffer == null) {
                buffer = new ArrayList<>();
                service.batchBufferMap.put(groupID,buffer);
                service.batchBufferStartMap.put(groupID,System.currentTimeMillis());
                boolean busy = Boolean.TRUE.equals(service.groupBusyMap.get(groupID));
                service.batchCollectingMap.put(groupID,!busy);
                if (!busy) service.groupBusyMap.put(groupID,true);
                scheduled = !busy;
            } else {
                //后续纯表情包或裸图只并入已攒批次，不再把等待窗口继续往后推
                scheduled = Boolean.TRUE.equals(service.batchCollectingMap.get(groupID))
                        && (!pureMedia || buffer.isEmpty());
            }
            if (buffer.size() >= service.config().messageBatchMaxMessages) {
                dropOldestBatchMessage(buffer);
            }
            buffer.add(event);
        }
        if (!service.hasImageContent(event.getMessage()) && !service.hasStickerContent(event.getMessage())) {
            service.wakePendingTurnForUser(groupID,event);
        }
        if (scheduled) scheduleBatchFlush(groupID);
    }

    private List<GroupMessageEvent> filterStaleBatchMessages(long groupID,List<GroupMessageEvent> batch) {
        if (service.config().messageBatchMaxAgeSecond <= 0) return batch;
        long now = System.currentTimeMillis();
        long maxAge = service.config().messageBatchMaxAgeSecond * 1000L;
        List<GroupMessageEvent> fresh = new ArrayList<>();
        int dropped = 0;
        for (GroupMessageEvent event : batch) {
            long time = eventTimeMillis(event);
            if (time <= 0 || now - time <= maxAge || isBatchMessageImportant(event)) {
                fresh.add(event);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批丢弃 "+dropped
                    +" 条等待超过 "+service.config().messageBatchMaxAgeSecond+" 秒的过期消息");
        }
        return fresh;
    }

    private long eventTimeMillis(GroupMessageEvent event) {
        if (event == null || event.getRaw() == null) return 0L;
        long seconds = event.getRaw().getLongValue("time");
        return seconds <= 0 ? 0L : seconds * 1000L;
    }

    private void dropOldestBatchMessage(List<GroupMessageEvent> buffer) {
        for (int i = 0; i < buffer.size(); i++) {
            GroupMessageEvent item = buffer.get(i);
            if (!isBatchMessageImportant(item)) {
                buffer.remove(i);
                return;
            }
        }
        buffer.remove(0);
    }

    private boolean isBatchMessageImportant(GroupMessageEvent event) {
        if (event == null || event.getRaw() == null) return false;
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID <= 0) return false;
        return service.isMentioningSelf(event,selfID) || service.isQuotingSelf(event,selfID);
    }

    private void scheduleBatchFlush(long groupID) {
        int generation;
        long delaySeconds;
        synchronized (service.groupQueueLock) {
            List<GroupMessageEvent> buffer = service.batchBufferMap.get(groupID);
            if (buffer == null || buffer.isEmpty()) return;
            service.batchCollectingMap.put(groupID,true);
            Integer current = service.batchGenerationMap.get(groupID);
            generation = current == null ? 1 : current + 1;
            service.batchGenerationMap.put(groupID,generation);
            Long start = service.batchBufferStartMap.get(groupID);
            if (!service.config().messageBatchEnable) {
                delaySeconds = 0;
            } else {
                long now = System.currentTimeMillis();
                GroupMessageEvent latest = buffer.get(buffer.size() - 1);
                int size = buffer.size();
                boolean sameUser = size > 1
                        && buffer.get(size - 2).getUserID() == latest.getUserID();
                boolean direct = isBatchMessageImportant(latest);
                boolean initialMediaOnly = size == 1
                        && (service.hasImageContent(latest.getMessage())
                        || service.hasStickerContent(latest.getMessage()))
                        && !service.hasMeaningfulText(latest.getMessage());
                long delayMs = RoleplayMessageDebounce.delayMillis(
                        service.config().messageBatchDebounceMode,size,
                        service.isGroupActive(groupID),sameUser,direct,initialMediaOnly,
                        service.config().messageBatchWindowSecond);
                if (service.config().messageBatchMaxWaitSecond > 0 && start != null) {
                    long maxRemain = service.config().messageBatchMaxWaitSecond * 1000L
                            - (now - start);
                    if (maxRemain <= 0) delayMs = 0;
                    else delayMs = Math.min(delayMs,maxRemain);
                }
                delaySeconds = delayMs <= 0 ? 0 : (delayMs + 999) / 1000;
            }
        }
        final int expected = generation;
        service.plugin().getServer().getPluginManager().runTaskLater(service.plugin(),
                () -> flushBatch(groupID,expected),delaySeconds);
    }

    private void flushBatch(long groupID,int generation) {
        List<GroupMessageEvent> batch;
        synchronized (service.groupQueueLock) {
            Integer current = service.batchGenerationMap.get(groupID);
            if (current == null || current != generation) return;
            batch = service.batchBufferMap.remove(groupID);
            service.batchCollectingMap.remove(groupID);
        }
        final List<GroupMessageEvent> taken = batch;
        service.submitTurn(() -> runBatchTurn(groupID,taken));
    }

    private void runBatchTurn(long groupID,List<GroupMessageEvent> batch) {
        boolean deferred = false;
        try {
            if (batch != null && !batch.isEmpty()) {
                List<GroupMessageEvent> fresh = filterStaleBatchMessages(groupID,batch);
                if (fresh.isEmpty()) {
                    service.plugin().getLogger().sendInfo("[角色] 群"+groupID+" 合批消息全部过期，本轮跳过");
                } else if (fresh.size() == 1) {
                    deferred = service.processGroupMessage(fresh.get(0));
                } else {
                    deferred = service.processMessageBatch(groupID,fresh);
                }
            }
        } catch (Exception e) {
            service.plugin().getLogger().sendException(e);
        }
        if (!deferred) finishTurn(groupID);
    }

    void finishTurn(long groupID) {
        boolean more;
        synchronized (service.groupQueueLock) {
            if (!Boolean.TRUE.equals(service.groupBusyMap.get(groupID))) return;
            List<GroupMessageEvent> next = service.batchBufferMap.get(groupID);
            more = next != null && !next.isEmpty();
            if (!more) {
                service.batchBufferMap.remove(groupID);
                service.batchGenerationMap.remove(groupID);
                service.batchBufferStartMap.remove(groupID);
                service.batchCollectingMap.remove(groupID);
                service.groupBusyMap.remove(groupID);
            }
        }
        if (more) scheduleBatchFlush(groupID);
    }
}
