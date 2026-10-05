package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.OneBot.OneBotClient;
import org.moboxlab.moboxbot.API.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表达类动作执行
 *
 * 动作由执行层和正文一起决定，发出前在这里统一校验。
 * 校验失败只丢弃该动作，不影响正文发送。
 */
public class RoleplayActionService {
    private final Plugin plugin;
    private final Map<String,Long> pokeBackMap = new ConcurrentHashMap<>();

    public RoleplayActionService(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 戳回去，同一群同一用户有冷却，避免双方来回戳个不停
     */
    public boolean pokeBack(RoleplayConfig config,long groupID,long userID) {
        if (!config.pokeBackEnable) return false;
        if (groupID <= 0 || userID <= 0) return false;
        long now = System.currentTimeMillis();
        String key = groupID+"-"+userID;
        Long last = pokeBackMap.get(key);
        if (last != null && now - last < config.pokeBackCooldownSecond * 1000L) {
            plugin.getLogger().sendInfo("[角色] 戳回去被冷却拦截 群"+groupID+" 用户"+userID);
            return false;
        }
        OneBotClient client = plugin.getServer().getOneBotClient();
        if (client == null) return false;
        JSONObject params = new JSONObject(true);
        params.put("user_id",String.valueOf(userID));
        params.put("group_id",String.valueOf(groupID));
        JSONObject response = client.callAction("group_poke",params);
        boolean success = response != null && response.getIntValue("retcode") == 0;
        if (success) {
            pokeBackMap.put(key,now);
        } else {
            plugin.getLogger().sendWarn("[角色] 戳回去失败 群"+groupID+" 用户"+userID);
        }
        return success;
    }
}
