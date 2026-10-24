# 无事务存储门面上用单语句 CAS、唯一索引与可重跑步骤保证一致性

状态：accepted（2026-10-22）

主程序 `StorageService` 只暴露 get / set / remove / query / queryOne / update / insert 七个方法：没有事务，没有连接或 DDL 专用入口，异常会被实现吞掉（update 返回 -1 表示语句未执行、返回 0 表示没有匹配行且 DDL 成功也是 0；insert 返回 0 兼表「没插入」与「插入成功但取不到生成键」；query 异常返回已经读到的部分行，可能为空），底层 SqlExecutor 对每条语句各自 autocommit 并持全局静态锁。插件调用可能来自不同线程（主程序 SQL 执行器另有全局静态锁），且进程退出不保证调用 onDisable。因此「一局」的状态不能靠跨语句事务维护，只能把每次落库设计成单条语句即一次事务，再让多步流程天然可重跑。具体做法：DDL 一律经 `update()` 执行（`query()` 执行 DDL 会抛异常但不建表，且被吞成空结果）；状态迁移一律用 `UPDATE ... WHERE` 前置条件判断的 CAS 语句：-1 表示语句未执行，中止本步、下轮重试；0 表示前置不匹配，回读重判；「每群一局」用部分唯一索引 `UNIQUE(groupID) WHERE state IN ('RECRUITING','RUNNING')` 兜底，插入返回 0 时回读既有活跃局；建表只发 `CREATE ... IF NOT EXISTS`，缺列先读 `PRAGMA table_info` 再 `ALTER TABLE ADD COLUMN`，只增不删不改；清理只选已结算（growthSettled=1）的 ENDED 局，按 log → member → session 的子到父顺序按年龄删除；任一条子表语句返回 -1 或删除 0 行（预期应有行）就中止本局、不删父行、下轮重试；归档只置 state / endedAt / endReason / summary / growthSettled，不做跨表搬迁。

## 考虑过的选项

- **插件内建写锁串行化所有存储操作**：只能约束本进程，重启窗口与将来多实例都不覆盖，还会把全局 SQL 锁再叠一层；无法替代语句级原子性。
- **绕过 StorageService 直接拿 JDBC 连接开事务**：违反「插件只依赖 org.moboxlab.moboxbot.API」的边界，且 0.3 不提供连接或 DDL 入口，主程序升级即失效。
- **给 session/member 加独立 version 列做乐观锁**：可行但列冗余；state、lastActivityAt 与 successSkills 等既有字段已能表达前置条件，额外的 version 只增加迁移与遗忘风险。
- **接受不一致并只在读取时丢弃坏行**：丢存档比坏存档更伤玩家，且无法区分「没写成功」与「还没写」。

## 后果

- 跨表操作（成长结算、清理）不是原子的，中途中断后必须能续跑：所有 RUNNING→ENDED 先 CAS 置 ENDED，再逐成员一条 UPDATE 同时写 cardJSON 与清空 successSkills，重启只补 growthSettled=0 的局、只处理 successSkills 非空的成员；摘要最后写并在同一 UPDATE 置 growthSettled=1；RECRUITING→ENDED 不结算成长。
- 日志 seq 由 `COALESCE(MAX(seq),0)+1` 计算，并发下可能撞 UNIQUE(sessionID,seq)；插入返回 0 时不重试（行可能已插入但取键失败，重算会重复写），只记插件日志并跳过该事件，下一个事件重新计算 seq；日志缺失可接受、重复不可接受。
- 恢复只信任已落库的 session / member 行；没有对应未归档 session 的成员行不会进入内存局面。清理不会产生孤儿：子表失败即中止、不删父行，父行删除也带 state='ENDED' 条件；孤儿只可能来自旧版本或人工改库，读取侧过滤即可，不再承诺「按年龄带走」。
- 契约测试必须连真实 SQLite，并用实现 StorageService 的 Fake 注入 -1 / 0 / 部分结果（含异常）来模拟失败路径，断言重试与降级行为而不是依赖异常；恢复加载不包含 ENDED 局，ENDED 摘要与成长靠补做扫描。
