# MBB-Trpg v1 验收记录（S11）

- 记录日期：2026-10-23
- 验收对象：`master` 之上的 S11 交付分支，插件版本 `V0.1.10.0.2155`；v1.1 与审查修复后为 `V0.1.10.1.2338`，复核建议修正后为 `V0.1.10.2.0001`（560 tests / 49 类）
- 规格依据：[MBB-Trpg.md](MBB-Trpg.md) 第 12 节（86 条口径，表内 87 行，J5 拆 J5a/J5b）；[呈现规范分册](MBB-Trpg-presentation.md)
- 判定口径：**通过**＝有自动化断言直接覆盖且本轮全绿；**部分**＝主要预期已覆盖、个别边界无断言；**间接**＝仅下层证据；**手测**＝本轮人工执行并留记录；**N/A**＝不属 v1 范围（J12）。

## 1. 结论

| 项 | 结果 |
| --- | --- |
| 测试 | `MBB-Trpg\build.ps1 -Test` → **OK (497 tests)，45 个测试类** |
| 全仓构建 | `build-all.ps1 -Bot <MoBoxBot.jar>` → **exit 0，24 个 JAR 汇总到根 `out/`** |
| 插件产物 | `out/MBB-Trpg.jar`（389.5 KB）根目录含 `plugin.json`；主类继承 `API.Plugin` |
| 自动化覆盖 | 87 行结果：58 通过（含 E1 的文档裁决）、18 部分、3 间接、6 手测（A1/A3/A4/J11/K3/K5）、1 缺口（B4）、1 N/A（J12） |
| 规格冲突裁决 | E1 改为「招募期不发恢复提示，`recovered=true`」，同步 §5.2 T8、§5.7 与 §12；J13 词表计数改为 29，同步 §10 与 §12；本记录为裁决依据 |
| 已知缺口 | B4 命中顺序、C1 状态机矩阵、B2 权限矩阵等留作后续切片；I2/I5 的插件装配层断言与 J10 的过期清理直证也记入 §4 |

## 2. 逐条执行记录

### A. 构建与测试载体（4）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| A1 | 手测通过 | 本轮执行根 `build-all.ps1`，exit 0；24 个 JAR 汇总到根 `out/`，含 `MBB-Trpg.jar` |
| A2 | 通过 | `TrpgBuildSmokeTest#pluginJsonShouldBePackagedAndValid`、`#pluginClassShouldBeLoadable`；本轮人工确认 JAR 根 `plugin.json` 与主类继承 |
| A3 | 手测通过 | 本轮执行 `MBB-Trpg\build.ps1 -Test`：junit 4.13.2 / hamcrest 1.3 sha1 校验通过，编译 144 源文件 + 60 测试文件，跑 497 个用例 |
| A4 | 手测通过 | 全仓构建使用默认开关，未生成 `test-libs/`、未下载测试依赖、未执行 JUnit |

### B. 指令与权限矩阵（7）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| B1 | 通过 | `TrpgSubcommandTest#resolvesEveryPrimaryName`、`#resolvesChineseAliases`、`#unknownTokensAreRejected`、`TrpgCommandServiceTest#helpListsAllImplementedCommands` |
| B2 | 部分 | 常见格子有断言（`TrpgCommandServiceTest`、`TrpgKpCommandTest`、`ScriptAdminCommandTest`、`SessionPlayServiceTest#sceneRejectsNonKp` 等），未做五角色 × 全指令表驱动矩阵 |
| B3 | 通过 | `TrpgCommandServiceTest#privateChatRejectsGroupOnlyCommands`、`#privateChatAllowsHelp`、`DiceCommandTest#privateChatIsAllowed`、`TrpgSubcommandTest#privateChatOnlyAllowsHelpAndScriptLibrary` |
| B4 | 缺口 | 固定命中序（黑名单→群内→权限→状态→参数→冷却）无顺序断言，仅有分散单项用例；记入 §4 |
| B5 | 部分 | `SessionServiceTest#rejectionDedupeSuppressesRepeatedSameError` 覆盖第 1、2 次；第 3 次与窗口过期未断言 |
| B6 | 通过 | `DiceCommandTest#rejectsUnsupportedExpressionWithoutRolling`、`#sumsDiceAndConstants`、`DiceParserTest#rejectsMultiplicationAndSkillNames` |
| B7 | 通过 | `TrpgCommandServiceTest#aliasBehavesLikePrimary`、`TrpgSubcommandTest#labelsCombinePrimaryAndAlias` |

### C. 状态机与结束编排（9）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| C1 | 部分 | T1–T7 合法路径均有断言（`SessionServiceTest`、`SessionRecoveryTest`），非法分支未做全矩阵表驱动 |
| C2 | 通过 | `SessionServiceTest#endNeedsSecondConfirmationWithinWindow` |
| C3 | 通过 | `SessionServiceTest#cancellingRecruitingDoesNotSettleGrowth`、`#confirmationExpiresAfterWindow` |
| C4 | 部分 | `SessionServiceTest#secondEndConfirmationFinishesAndSettlesSummary`：只断言最终状态、成长标记与摘要非空；「CAS→成长→摘要」顺序未单独断言 |
| C5 | 通过 | `SessionRecoveryTest#settlePendingSkipsAlreadySettledSessions`、`SessionServiceTest#growthWriteFailureDefersSettlementUntilNextStartup` |
| C6 | 通过 | `SessionServiceTest#adminEndIsForcedAndSettlesGrowth`、`TrpgCommandServiceTest#groupAdminForcesEndReason` |
| C7 | 通过 | `SessionPlayServiceTest#skipRecordsReason`、`TrpgKpCommandTest#inviteTakeAndSkipReply` |
| C8 | 通过 | `SessionServiceTest#rejoinAfterLeaveClearsLeftAtAndKeepsCard`、`SessionPlayServiceTest#inviteAddsNewMemberDuringRunning` |
| C9 | 通过 | `SessionServiceTest#endConfirmationWindowIsPerInitiator` |

### D. 并发与 CAS（6）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| D1 | 通过 | `GroupLockRegistryTest#secondThreadCannotEnterSameGroup`、`SessionServiceTest#secondStartInSameGroupIsRejected`、`SqlSessionStoreTest#activeUniqueIndexRejectsSecondActiveSessionInSameGroup` |
| D2 | 间接 | `GroupLockRegistryTest#differentGroupsDoNotBlockEachOther` 只证锁层不互斥，未在 `start` 层断言两局都建成 |
| D3 | 通过 | `SessionServiceTest#busyWhenAnotherThreadHoldsTheGroupLock` |
| D4 | 通过 | `SessionServiceTest#growthWriteFailureDefersSettlementUntilNextStartup`、`#endCasFailureKeepsSessionRunning`、`JdbcStorageFakeTest#updateDistinguishesNotExecutedFromNoMatch` |
| D5 | 间接 | `SqlSessionStoreTest#casTransitionSeparatesMinusOneFromZero` 只证 store 层区分 -1/0；服务层「0 命中」日志未断言 |
| D6 | 通过 | `SessionServiceTest#eventsArePublishedAfterTheGroupLockIsReleased` |

### E. 重启恢复与过期（9）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| E1 | 通过（文档已裁决） | `SessionRecoveryTest#recoverDoesNotPublishRecoveryPromptForRecruitingSession`：招募期不发提示、`recovered=true`；§12 E1 原「提示 + false」已改为与本条一致 |
| E2 | 通过 | `SessionRecoveryTest#resumeRequiresKpAndPendingRecovery`、`TrpgPresenterTest#recoveredEventMentionsResume` |
| E3 | 部分 | 加载与字段持久化已断言（`SessionRecoveryTest`、`SqlSessionStoreTest`），SAN/技能/离场/代管/疯狂未逐项断言还原 |
| E4 | 部分 | 加载后 `recovered=false` 已断言；「操作后仍 false」与 `status` 提示行未断言 |
| E5 | 部分 | `SessionRecoveryTest#recoverArchivesSessionWithoutMembers` 断言归档；告警未用 `RecordingLogger` 断言 |
| E6 | 通过 | `SessionServiceTest#lazyExpiryCancelsRecruitingAfter24Hours` |
| E7 | 通过 | `SessionServiceTest#lazyExpiryFinishesRunningAfter7DaysAndSettlesGrowth` |
| E8 | 通过 | `SessionServiceTest#touchActivityIsThrottledAndOnlyCountsMembers`、`TrpgListenerTest#messageRefreshesActivityAfterThrottle` |
| E9 | 通过 | `SessionRecoveryTest#recoverFallsBackToBuiltinRuleSetForUnknownIds`、`RuleRegistryTest#unknownRuleSetFallsBackToBuiltinAndWarns` |

### F. 存档与清理（9）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| F1 | 通过 | `TrpgSchemaServiceTest#f1SchemaIsIdempotent` |
| F2 | 通过 | `TrpgSchemaServiceTest#f2RestoresDroppedColumnWithoutLosingData` |
| F3 | 通过 | `TrpgSchemaServiceTest#f3CreateFailureDisablesWithoutDropping` |
| F4 | 通过 | `TrpgSchemaServiceTest#f4PragmaFailureSkipsAlterInsteadOfBlindChange`、`#f4PartialPragmaRowsDoNotTriggerAlterOrDisable` |
| F5 | 通过 | `TrpgRetentionServiceTest#purgesExpiredSessionChildrenBeforeParent` |
| F6 | 通过 | `TrpgRetentionServiceTest#keepsYoungSessionsAndNeverPurgesUnsettledOnes` |
| F7 | 通过 | `TrpgRetentionServiceTest#abortsWholeSessionWhenChildDeleteFailsAndRetriesNextRun` |
| F8 | 部分 | 结果已测（`SessionRecoveryTest#settlePendingFinishesEndedSessionThatMissedSettlement`）；「同语句」「摘要空串补写」未直接断言 |
| F9 | 通过 | `TrpgRetentionServiceTest#traceKeepsByTimeFirstThenByRowCount`、`#traceUnderKeepRowsIsNotDeleted` |

### G. 规则引擎（7）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| G1 | 通过 | `D100RuleSetTest#mapsSixDegreesByThreshold`、`#difficultyRaisesTheMinimumSuccessLevel` |
| G2 | 通过 | `D100RuleSetTest#lowSkillFumblesOn96To100`、`#highSkillFumblesOnlyOn100`、`#faceOneIsCriticalEvenAtExtremeDifficultyAndZeroSkill` |
| G3 | 通过 | `D100RuleSetTest#bonusDicePickLowestTens`、`#penaltyDicePickHighestTens`、`#bonusAndPenaltyCancelOneToOne` |
| G4 | 通过 | `D100RuleSetTest#opposedDecidedByLevel`、`#opposedRerollsOnceOnSameRoll`、`#opposedNoneWhenRerollStillTies`、`#opposedBothFailIsNone` |
| G5 | 通过 | `D100RuleSetTest#sanSuccessUsesSuccessLoss`、`#sanFumbleUsesMaxFailureLossAndTriggers`、`#sanTriggerThresholdIsFive` |
| G6 | 通过 | `D100RuleSetTest#growthGainsOnlyWhenStrictlyGreater`、`#growthCapsAtNinetyNine` |
| G7 | 通过 | `RuleRegistryTest#unknownRuleSetFallsBackToBuiltinAndWarns`、`#unknownRuleSetFallsBackToNullWhenBuiltinMissing` |

### H. 剧本（9）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| H1 | 通过 | `ScriptValidatorTest#acceptsBuiltinExample`、`BuiltinScriptTest#everyTriggerKindIsCovered`、`#endingsAreOrderedFromRichestToFallback` |
| H2 | 通过 | `ScriptImportSessionsTest#importsInMultipleSegmentsAndSaves` |
| H3 | 通过 | `ScriptImportSessionsTest#cancelClearsPending`、`#endAndCancelWithoutPendingFail` |
| H3b | 通过 | `ScriptImportSessionsTest#idleTimeoutExpiresPending`、`#maxMinutesExpiresEvenWithActivity` |
| H3c | 通过 | `ScriptImportSessionsTest#segmentLimitRejectsFurtherFragments`（回 `E_SIZE`、清暂存） |
| H4 | 部分 | 校验层 `ScriptValidatorTest#rejectsReservedScriptId`、`#rejectsGeneratedIdPrefix`、`#rejectsInvalidScriptId`；导入路径「同内置 id → E_BUILTIN」无测试；`BuiltinScriptCatalogTest#refusesToDeleteBuiltin` |
| H5 | 部分 | `ScriptImportSessionsTest#semanticErrorClearsPendingAndSavesRejected`；rejected 最多 20 份轮转未断言 |
| H6 | 部分 | `ScriptAdminCommandTest#deleteInUseScriptIsRejected`、`#deleteImportedScriptSucceeds`、`#deleteBuiltinScriptIsRejected`；「归档局引用可删」未测 |
| H7 | 通过 | `ScriptGenerationServiceTest#mapsDisabledErrorAndArchivesRaw`、`#invalidJsonIsOutputError`、`#semanticValidationFailureReportsErrors`、`#timeoutAndGenericFailureMapToCodes`、`#concurrentCallWhileRunningIsBusy`、`#emptyOutputIsRecordedAsPlaceholder` |

### I. AI 契约桩与关闭一致（7）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| I1 | 通过 | `TrpgAiHostServiceTest#toggleOnIsDisabledWhenGlobalSwitchIsOff`、`TrpgKpCommandTest#aiSwitchRepliesV1Branches` |
| I2 | 部分 | `FullSessionAcceptanceTest#fullSessionRunsWithoutAi`（本轮新增）：`SessionService` 层的 B/C/G/H 代表动作跑完整局（start/join/begin/check/san/scene/adjust/end）、`ai_trace` 0 行、收尾结算与幂等；命令层入口（`TrpgCommandService` 的权限/冷却/呈现）与「AI 提示数 0」未覆盖，记入 §4 |
| I3 | 间接 | `TrpgAiHostServiceTest#toggleOnIsPlannedWhenGlobalSwitchIsOn`；v1 无局级授权概念，零调用/零审计无直接断言 |
| I4 | 通过 | `TrpgKpCommandTest#aiOnWithGlobalSwitchReportsPlannedAndStatusStaysOff` |
| I5 | 部分 | `SessionServiceTest#aiOnWithGlobalSwitchReportsPlannedWithoutWritingState`；「不注册 AI 定时器」未做装配层显式断言（整局零审计/零事件只能作间接证据），记入 §4 |
| I6 | 通过 | `TrpgAiHostServiceTest#parseWordAcceptsChineseAndEnglishWords`、`#toggleWithoutWordIsUsage`、`#toggleOffIsAlwaysAcknowledged` |
| I7 | 通过 | `TrpgKpCommandTest#aiOnWithGlobalSwitchReportsPlannedAndStatusStaysOff`、`SessionServiceTest#aiOnWithGlobalSwitchReportsPlannedWithoutWritingState` |

### J. 图片、呈现与异常路径（15）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| J1 | 通过 | `TrpgPresenterTest#sceneImageIsSentWhenEnabled`、`#sceneFallsBackToTextWhenImageSendFails`、`TrpgImageTemplatesTest#sceneCardCapsTotalLines` |
| J2 | 通过 | `TrpgPresenterTest#endedEventSendsReportImageBeforeText`、`#endedEventFallsBackToReportTextWhenImageUnavailable`、`TrpgImageTemplatesTest#reportHasNineFrozenSections` |
| J3 | 通过 | `TrpgPresenterTest#scriptPageUsesPrivateImage`、`#scriptPageFallsBackToPrivateTextWhenImageFails`、`TrpgImageTemplatesTest#scriptPageListsScenes` |
| J4 | 部分 | 战报路径的 null/throw 已测（`TrpgPresenterTest#endedEventFallsBackWhenRendererThrows`）；场景/脚本页与 WARN 内容未断言 |
| J5a | 通过 | `TrpgPresenterTest#privateCardFailureNoticesGroupAndCountsStreak`、`#privateCardFailureUsesFallbackName` |
| J5b | 部分 | `TrpgPresenterTest#privateSecretFailureNeverRevealsSecret`；「连续 2 次仍不折叠」边界未直接断言 |
| J6 | 通过 | `TrpgMessageSplitterTest#splitsByParagraphAndAddsContinuedTitle`、`#foldsWhenPartsExhausted`、`TrpgPresenterTest#longGroupMessageSplitsIntoAtMostThreeParts` |
| J7 | 通过 | `TrpgListenerTest#blacklistedMessageIsCancelledSilently`、`TrpgKpCommandTest#blacklistedMemberGetsNoReply` |
| J8 | 通过 | `TrpgCommandServiceTest#statusRepeatsAreSuppressedByCooldown`、`DiceCommandTest#cooldownIsTwoSecondsPerUserAndChannel` |
| J9 | 部分 | `JdbcStorageFakeTest#queryReturnsPartialRowsInsteadOfThrowing`、`SessionServiceTest#endCasFailureKeepsSessionRunning`；「内存局面不半更新」仅 CAS 一例 |
| J10 | 部分 | `ThousandCommandsLoadTest#thousandCommandsKeepOneGroupLockAndFlatTables`、`#groupLockCacheStaysBoundedAndEvictsIdleGroups`、`#twoThousandCommandsKeepHeapUsageStable`（本轮新增，3 用例全绿）：缓存条数有界并随空闲淘汰（以 `size()==1` 为证，`isLocked` 只反映当前持锁）、四表行数零增长、活跃局唯一、两轮堆增量 ≤32MB（`System.gc` 采样，作 smoke）；`GroupLockRegistry` 容量上限 256 的分支未触达，且「日志与审计按保留策略清理过期行」无直接证据（只断言活跃局不是清理候选），由 `TrpgRetentionServiceTest` 另覆盖 |
| J11 | 手测通过 | 对照呈现分册 §4 抽 7 条冻结样例（开团/检定/理智/AI 叙述/结束/战报/恢复），`TrpgMessageTemplatesTest` 内联冻结串逐字相等 |
| J12 | 通过（v1.1 补测） | v1.1 交付 20 秒或 5 条先到的合并窗口：`AiHostEngineTest` 覆盖立即触发、窗口追加、窗口到达 5 条或 20 秒后合并、每群单槽位只留最新窗口；详见 §5 |
| J13 | 通过 | `TrpgTitlesTest#allTitlesAreUniqueAndCount29`、`#allTitlesAppearInSpecOrder`、`TrpgMessageFormatterTest#cleansMarkdownAndEmoji`；词表实为 29 个标题（§2.3 分册），§10/§12 的「24 词表」已改 |
| J14 | 部分 | 结束/恢复路径已测（`TrpgPresenterTest#endedEventSendFailureAddsCriticalNotice`、`#recoveredEventFallsBackWhenSendFails`）；开团、开局发送失败未测 |

### K. 安全与隐私（5）

| 编号 | 结果 | 证据 / 说明 |
| --- | --- | --- |
| K1 | 通过 | `SqlSessionStoreTest#allSqlStayInsidePluginScope`、`TrpgSchemaServiceTest#k1AllSqlStayInPluginScope` |
| K2 | 通过 | `SourceBoundaryTest#mainSourcesOnlyImportApiPackage`、`#mainSourcesAvoidForbiddenInternalHooks`、`#packageLayoutStaysInDeclaredPath` |
| K3 | 手测通过 | 扫描新增日志与审计调用：只记录动作、群号/局号、剧本 id/名称、错误码与操作人账号（排障所需，不含昵称、邮箱、手机号），不含完整提示词/完整输出/Token；`ai_trace` 在 v1 不写入 |
| K4 | 通过 | `TrpgKpCommandTest#sceneSendsImageClueAndKpSecret`、`TrpgPresenterTest#privateSecretFailureNeverRevealsSecret` |
| K5 | 手测通过 | 提交前扫描：`git ls-files` 不含 `out/`、日志、数据库，`git diff` 不含 `sk-`、Token、密码、邮箱；`.scratch/` 为本地临时交接目录，不入库 |

## 3. 本轮新增/修改

- 新增 `MBB-Trpg/src/test/java/org/moboxlab/mbb/trpg/session/FullSessionAcceptanceTest.java`（I2 整局回归）。
- 新增 `MBB-Trpg/src/test/java/org/moboxlab/mbb/trpg/session/ThousandCommandsLoadTest.java`（J10 千次长跑）。
- 规格裁决：§12 E1 改为招募期不发提示、`recovered=true`，并同步 §5.2 T8、§5.7 恢复段；§10/§12 J13 词表计数改为 29，并补「86 条 / 87 行 / J12 N/A」计数口径。
- 本记录纳入版本控制，作为 S11 交付证据；测试总数 493 → 497 → 560（v1.1 补测与审查修复）。

## 4. 后续切片建议（不阻断 v1）

按验收矩阵优先级：

1. P0：B4 错误命中顺序、C1 状态机非法路径矩阵、B2 五角色 × 指令权限矩阵（表驱动）。
2. P1：E3/E4/E5 恢复逐项还原与告警、H4/H5/H6 导入与删除边界、J4/J5b/J9/J14 异常路径。
3. P2：A2 产物依赖检查、B5 三次去重与窗口过期、I2 命令层入口与 AI 提示数断言、I5 零定时器注册显式断言、J10 过期日志/审计清理直证、D2/D5/I3 补直接证据。

## 5. v1.1 补测（AI 主持真实调用）

v1.1 接 issue AI 主持，v1 的 §12 清单不变；以下条目在 v1.1 由「N/A / 桩」转为可验收，证据全部为自动化测试。

| 条目 | 结果 | 证据 |
| --- | --- | --- |
| J12 合并窗口 | 通过 | `AiHostEngineTest`：立即触发绕过窗口、`PLAYER_ACTION` / 玩家自助 `CHECK` 进窗口、5 条先到立即合并、20 秒到期由 `tick` 结算、同局跨场景仍在同一窗口且结算取最新场景版本、玩家行动原文进入提示词 |
| 网关与降级 | 通过 | `AiHostEngineTest`：全局关/关 AI/挂起/非进行中静默且零调用；降级表逐类断言「计失败、写审计、发降级提示」，含 `network`、`http_401`、`http_500` 与 `finishReason=length`（按成功截断）；`disabled` / `unavailable` / `interrupted` / `queue_full` 不计失败，`network` / `http_<code>` 与未列出错误码原样透传 |
| 挂起与恢复 | 通过 | `AiHostEngineTest`：连续 3 次可计失败写 `aiSuspendedUntil` 并清队列；挂起期零调用、每周期只发一次提示；`/trpg ai on` 清挂起并清零计数 |
| 审计 | 通过 | `AiHostEngineTest`：每请求至多 1 行；全局关、未授权、窗口无内容不写行，挂起丢弃写 `skipped`、换局换场景写 `stale`；`traceEnabled=false` 不写、写失败不阻断叙述 |
| 提示词装配 | 通过 | `AiPromptAssemblerTest`：稳定前缀与可缓存中段稳定、动态段按预算截断并保留最近事件行 |
| 输出清洗 | 通过 | `AiNarrationParserTest`：300 字截断、建议最多 3 条、数值变化/检定结论/推进句式拒绝、空输出拒绝 |
| 开关落库 | 通过 | `SessionServiceTest`：`ai on` 写 `aiEnabled=1` 并清挂起、`ai off` 写 0、无效词不写库、全局关按「当前状态不可用」拒绝 |
| 触发点 | 通过 | `TrpgKpCommandTest#playerCheckUsesMergedTriggerWithEventText`、`TrpgListenerAiActionTest`：玩家自助检定进合并窗口并携带判定摘要；玩家自由文本行动取消事件并按事件序号进窗口；KP 自由文本不被识别 |
| 投递门禁 | 通过 | `AiHostEngineTest#suspendedTriggerNotifiesOncePerSuspensionPeriod`、`#failureAfterSuspensionIsDiscardedAsSkipped`：局结束、挂起、换局或换场景一律不发消息，只记 `skipped` / `stale` |
| 呈现文案 | 通过 | `TrpgMessageTemplatesTest` / 呈现规范 §3.6：叙述落款、降级原因、挂起提示；降级先发剧本模板叙述再发说明 |

测试总数 497 → 554 → 560（49 个测试类）。
