package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.PluginService;

/**
 * MBB-Roleplay 对外服务
 */
public class RoleplayPublicService implements PluginService {
    private final RoleplayService service;

    public RoleplayPublicService(RoleplayService service) {
        this.service = service;
    }

    @Override
    public String getName() {
        return "MBB-Roleplay";
    }

    @Override
    public JSONObject call(String action,JSONObject params) {
        if ("notify-draw-complete".equalsIgnoreCase(action)) {
            return service.notifyDrawComplete(params);
        }
        if ("prepare-draw-complete".equalsIgnoreCase(action)) {
            return service.prepareDrawComplete(params);
        }
        if ("send-draw-notify".equalsIgnoreCase(action)) {
            return service.sendDrawNotify(params);
        }
        if ("notify-draw-failed".equalsIgnoreCase(action)) {
            return service.notifyDrawFailed(params);
        }
        if ("status".equalsIgnoreCase(action)) {
            return service.status(params == null ? 0L : params.getLongValue("groupID"));
        }
        if ("identity".equalsIgnoreCase(action)) {
            JSONObject result = new JSONObject(true);
            RoleplayPersona persona = service.persona();
            result.put("status",true);
            result.put("persona",persona == null ? "" : persona.name);
            return result;
        }
        if ("decide-friend-request".equalsIgnoreCase(action)) {
            return service.friendRequestService().decide(params);
        }
        if ("brain_remember".equalsIgnoreCase(action)) {
            return service.brainMemoryService().remember(params);
        }
        if ("brain_search".equalsIgnoreCase(action)) {
            JSONObject result = new JSONObject(true);
            if (params == null) {
                result.put("status",false);
                result.put("message","缺少查询参数");
                return result;
            }
            String channel = params.getString("channel");
            if (channel == null || channel.trim().isEmpty()) channel = "group";
            result.put("status",true);
            result.put("memories",service.brainMemoryService().search(
                    params.getLongValue("groupID"),params.getLongValue("userID"),channel,
                    params.getString("query"),params.getIntValue("limit")));
            return result;
        }
        if ("brain_status".equalsIgnoreCase(action)) {
            return service.brainMemoryService().status();
        }
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message","不支持的动作："+action);
        return result;
    }
}
