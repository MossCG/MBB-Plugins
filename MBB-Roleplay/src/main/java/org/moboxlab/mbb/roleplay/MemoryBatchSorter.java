package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 记忆分批合并前的轻量相似排序
 *
 * 不做重型聚类，只按类型、对象、内容前缀把可能相似的记忆排到一起，
 * 这样同一批里出现重复和高度相似内容的概率更高。
 */
public class MemoryBatchSorter {
    public static List<JSONObject> sortForMerge(List<JSONObject> memories) {
        List<JSONObject> result = new ArrayList<>();
        if (memories != null) result.addAll(memories);
        Collections.sort(result,new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left,JSONObject right) {
                int compare = safe(left.getString("type")).compareTo(safe(right.getString("type")));
                if (compare != 0) return compare;
                compare = Long.compare(left.getLongValue("subjectID"),right.getLongValue("subjectID"));
                if (compare != 0) return compare;
                compare = safe(left.getString("content")).compareTo(safe(right.getString("content")));
                if (compare != 0) return compare;
                return Long.compare(right.getLongValue("updateTime"),left.getLongValue("updateTime"));
            }
        });
        return result;
    }

    /**
     * 永久记忆先按内容相似度聚类，避免同一语义因为 type 不同被拆到不同批次。
     */
    public static List<JSONObject> sortForGlobalMerge(List<JSONObject> memories) {
        List<JSONObject> result = new ArrayList<>();
        if (memories != null) result.addAll(memories);
        Collections.sort(result,new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject left,JSONObject right) {
                int compare = normalize(left.getString("content"))
                        .compareTo(normalize(right.getString("content")));
                if (compare != 0) return compare;
                compare = normalize(left.getString("type")).compareTo(normalize(right.getString("type")));
                if (compare != 0) return compare;
                compare = Integer.compare(right.getIntValue("importance"),left.getIntValue("importance"));
                if (compare != 0) return compare;
                return Long.compare(right.getLongValue("updateTime"),left.getLongValue("updateTime"));
            }
        });
        return result;
    }

    public static List<List<JSONObject>> batches(List<JSONObject> memories,int batchSize) {
        List<List<JSONObject>> result = new ArrayList<>();
        if (memories == null || memories.isEmpty()) return result;
        int size = batchSize < 1 ? 60 : batchSize;
        for (int i = 0; i < memories.size(); i += size) {
            int end = Math.min(memories.size(),i + size);
            result.add(new ArrayList<>(memories.subList(i,end)));
        }
        return result;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String value) {
        return SpeechCorpusEntry.normalize(safe(value));
    }
}
