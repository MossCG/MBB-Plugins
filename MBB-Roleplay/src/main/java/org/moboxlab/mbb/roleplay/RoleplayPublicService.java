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
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message","不支持的动作："+action);
        return result;
    }
}
