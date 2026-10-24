package org.moboxlab.mbb.trpg.present;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** 生产昵称解析：查群成员资料，按「群号:用户号」缓存；查询失败回退兜底名且不缓存。 */
public final class OneBotNameResolver implements TrpgNameResolver {

    /** 缓存条数上限，超过后整体清空（昵称不是强一致数据，重建缓存代价很低）。 */
    private static final int CACHE_LIMIT = 1024;

    private final Server server;
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<String, String>();

    public OneBotNameResolver(Server server) {
        if (server == null) {
            throw new IllegalArgumentException("服务器不能为空");
        }
        this.server = server;
    }

    @Override
    public String name(long groupId, long userId) {
        String key = groupId + ":" + userId;
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String resolved = query(groupId, userId);
        if (resolved == null) {
            // 查询失败或资料为空时不写缓存：一次网络抖动不能把兜底名永久钉住。
            return TrpgNameResolver.fallback(userId);
        }
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, resolved);
        return resolved;
    }

    /** 清空缓存（群成员改名或退群后由指令层按需调用）。 */
    public void clear() {
        cache.clear();
    }

    /** 查询群名片或昵称；拿不到时返回 null，由调用方回退兜底名。 */
    private String query(long groupId, long userId) {
        OneBotClient client = server.getOneBotClient();
        if (client == null) {
            return null;
        }
        try {
            JSONObject response = client.getGroupMemberInfo(groupId, userId);
            if (response == null) {
                return null;
            }
            String card = response.getString("card");
            if (card != null && !card.trim().isEmpty()) {
                return card.trim();
            }
            String nickname = response.getString("nickname");
            if (nickname != null && !nickname.trim().isEmpty()) {
                return nickname.trim();
            }
        } catch (RuntimeException error) {
            return null;
        }
        return null;
    }
}
