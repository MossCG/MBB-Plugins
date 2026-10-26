package org.moboxlab.mbb.trpg.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 校验通过的剧本（不可变）：节点按数组顺序保存，另提供按 id 查找的只读索引。 */
public final class Script {

    public static final String RULE_SET_D100 = "d100";
    public static final int SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String id;
    private final String name;
    private final String author;
    private final String description;
    private final String ruleSet;
    private final int minPlayers;
    private final int maxPlayers;
    private final int estimatedMinutes;
    private final List<String> tags;
    private final List<String> startingItems;
    private final List<Scene> scenes;
    private final List<Clue> clues;
    private final List<Npc> npcs;
    private final List<ScriptCheck> checks;
    private final List<ScriptTrigger> triggers;
    private final List<ScriptEnding> endings;

    private final Map<String, Scene> scenesById;
    private final Map<String, Scene> scenesByTitle;
    private final Map<String, Clue> cluesById;
    private final Map<String, Npc> npcsById;
    private final Map<String, ScriptCheck> checksById;

    public Script(int schemaVersion, String id, String name, String author, String description,
                  String ruleSet, int minPlayers, int maxPlayers, int estimatedMinutes,
                  List<String> tags, List<String> startingItems, List<Scene> scenes,
                  List<Clue> clues, List<Npc> npcs, List<ScriptCheck> checks,
                  List<ScriptTrigger> triggers, List<ScriptEnding> endings) {
        this.schemaVersion = schemaVersion;
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.ruleSet = ruleSet == null ? "" : ruleSet;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
        this.estimatedMinutes = estimatedMinutes;
        this.tags = Collections.unmodifiableList(new ArrayList<String>(tags));
        this.startingItems = Collections.unmodifiableList(new ArrayList<String>(startingItems));
        this.scenes = Collections.unmodifiableList(new ArrayList<Scene>(scenes));
        this.clues = Collections.unmodifiableList(new ArrayList<Clue>(clues));
        this.npcs = Collections.unmodifiableList(new ArrayList<Npc>(npcs));
        this.checks = Collections.unmodifiableList(new ArrayList<ScriptCheck>(checks));
        this.triggers = Collections.unmodifiableList(new ArrayList<ScriptTrigger>(triggers));
        this.endings = Collections.unmodifiableList(new ArrayList<ScriptEnding>(endings));

        Map<String, Scene> byId = new LinkedHashMap<String, Scene>();
        Map<String, Scene> byTitle = new LinkedHashMap<String, Scene>();
        for (Scene scene : this.scenes) {
            byId.put(scene.id(), scene);
            byTitle.put(scene.title().trim(), scene);
        }
        this.scenesById = Collections.unmodifiableMap(byId);
        this.scenesByTitle = Collections.unmodifiableMap(byTitle);

        this.cluesById = indexById(this.clues, new Function<Clue, String>() {
            @Override
            public String apply(Clue clue) {
                return clue.id();
            }
        });
        this.npcsById = indexById(this.npcs, new Function<Npc, String>() {
            @Override
            public String apply(Npc npc) {
                return npc.id();
            }
        });
        this.checksById = indexById(this.checks, new Function<ScriptCheck, String>() {
            @Override
            public String apply(ScriptCheck check) {
                return check.id();
            }
        });
    }

    /** 按 id 建立只读索引，保留数组顺序；id 重复时后者覆盖前者（校验器已保证唯一）。 */
    private static <T> Map<String, T> indexById(List<T> nodes, Function<T, String> idOf) {
        Map<String, T> index = new LinkedHashMap<String, T>();
        for (T node : nodes) {
            index.put(idOf.apply(node), node);
        }
        return Collections.unmodifiableMap(index);
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** 作者；缺省为空串，展示层显示「未知」。 */
    public String author() {
        return author;
    }

    /** 简介；缺省为空串。 */
    public String description() {
        return description;
    }

    public String ruleSet() {
        return ruleSet;
    }

    public int minPlayers() {
        return minPlayers;
    }

    public int maxPlayers() {
        return maxPlayers;
    }

    public int estimatedMinutes() {
        return estimatedMinutes;
    }

    public List<String> tags() {
        return tags;
    }

    /** 开局时复制到每名玩家 cardJSON.items 的初始物品。 */
    public List<String> startingItems() {
        return startingItems;
    }

    public List<Scene> scenes() {
        return scenes;
    }

    /** 按顺序取场景；下标越界返回 null。 */
    public Scene sceneAt(int index) {
        if (index < 0 || index >= scenes.size()) {
            return null;
        }
        return scenes.get(index);
    }

    /** 按标题（去首尾空白后）找场景；找不到返回 null。 */
    public Scene sceneByTitle(String title) {
        return title == null ? null : scenesByTitle.get(title.trim());
    }

    public Scene sceneById(String sceneId) {
        return sceneId == null ? null : scenesById.get(sceneId);
    }

    public List<Clue> clues() {
        return clues;
    }

    public Clue clueById(String clueId) {
        return clueId == null ? null : cluesById.get(clueId);
    }

    public List<Npc> npcs() {
        return npcs;
    }

    public Npc npcById(String npcId) {
        return npcId == null ? null : npcsById.get(npcId);
    }

    public List<ScriptCheck> checks() {
        return checks;
    }

    public ScriptCheck checkById(String checkId) {
        return checkId == null ? null : checksById.get(checkId);
    }

    public List<ScriptTrigger> triggers() {
        return triggers;
    }

    public List<ScriptEnding> endings() {
        return endings;
    }
}
