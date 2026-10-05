package org.moboxlab.mbb.roleplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 资料预算分配
 *
 * 先放常驻资料，再按优先级填入可选资料，单项和总量都有上限。
 * 上限存在的意义不是省钱，而是防止某一份资料把整条提示词撑爆。
 */
public class RoleplayMaterialBudget {

    public static String assemble(List<RoleplayMaterial> materials,List<String> requested,int totalBudget) {
        if (materials == null || materials.isEmpty()) return "";
        List<RoleplayMaterial> selected = new ArrayList<>();
        for (RoleplayMaterial material : materials) {
            if (material == null) continue;
            if (material.alwaysOn() || matches(material.id(),requested)) selected.add(material);
        }
        Collections.sort(selected,new Comparator<RoleplayMaterial>() {
            @Override
            public int compare(RoleplayMaterial left,RoleplayMaterial right) {
                return right.priority() - left.priority();
            }
        });
        StringBuilder builder = new StringBuilder();
        int remaining = totalBudget <= 0 ? Integer.MAX_VALUE : totalBudget;
        for (RoleplayMaterial material : selected) {
            String text = trim(material.load());
            if (text.isEmpty()) continue;
            int limit = material.charBudget() > 0 ? material.charBudget() : text.length();
            if (limit > remaining) limit = remaining;
            if (limit <= 0) continue;
            String block = text.length() > limit ? text.substring(0,limit) : text;
            builder.append(block);
            if (!block.endsWith("\n")) builder.append("\n");
            remaining -= block.length() + 1;
            if (remaining <= 0) break;
        }
        return builder.toString();
    }

    /**
     * 是否命中某份资料，支持 id:参数 形式，例如 students.detail:优香
     */
    public static boolean matches(String id,List<String> requested) {
        if (id == null || requested == null || requested.isEmpty()) return false;
        for (String item : requested) {
            if (item == null) continue;
            String value = item.trim();
            if (value.equals(id)) return true;
            int colon = value.indexOf(':');
            if (colon > 0 && value.substring(0,colon).equals(id)) return true;
        }
        return false;
    }

    private static String trim(String text) {
        return text == null ? "" : text.trim();
    }
}
