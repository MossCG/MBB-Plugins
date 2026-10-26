package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Event.GroupMessageEvent;
import org.moboxlab.moboxbot.API.PluginService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 原生工具循环、并行工具执行、结果预算与崩溃恢复。
 */
public class RoleplayToolLoopService {
    private static final int MIN_RESULT_BUDGET = 400;

    private final RoleplayService service;
    private final RoleplayToolRegistry registry;
    private final Map<Long,ActiveTurn> activeTurns = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger recoveryRunning = new AtomicInteger();
    private volatile ExecutorService toolExecutor;
    private volatile int toolThreads;
    private final ExecutorService sideEffectExecutor = Executors.newFixedThreadPool(4,runnable -> {
        Thread thread = new Thread(runnable,"MoBoxBot-Roleplay-Tool-Effect");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService aiExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable,"MoBoxBot-Roleplay-Tool-AI");
        thread.setDaemon(true);
        return thread;
    });

    public RoleplayToolLoopService(RoleplayService service,RoleplayConfig config) {
        this.service = service;
        this.registry = new RoleplayToolRegistry(service,config);
        ensureExecutor(config);
    }

    public synchronized void reload(RoleplayConfig config) {
        registry.reload(config);
        ensureExecutor(config);
    }

    public RoleplayToolRegistry registry() {
        return registry;
    }

    public boolean cancelGroup(long groupID) {
        ActiveTurn active = activeTurns.get(groupID);
        if (active == null) return false;
        active.cancelled.set(true);
        for (Future<RoleplayToolResult> future : active.futures) {
            future.cancel(true);
        }
        if (active.aiFuture != null) active.aiFuture.cancel(true);
        return true;
    }

    public synchronized void shutdown() {
        for (ActiveTurn active : activeTurns.values()) {
            active.cancelled.set(true);
        }
        service.plugin().getLogger().sendInfo("[工具] 正在关闭工具执行线程");
        sideEffectExecutor.shutdownNow();
        aiExecutor.shutdownNow();
        ExecutorService executor = toolExecutor;
        if (executor != null) executor.shutdownNow();
        awaitShutdown(sideEffectExecutor);
        awaitShutdown(aiExecutor);
        awaitShutdown(executor);
    }

    public JSONObject run(long groupID,RoleplayToolContext context,JSONArray messages,JSONObject params) {
        String token = begin(groupID);
        ActiveTurn active = activeTurns.get(groupID);
        int persistedBase = messages == null ? 0 : messages.size();
        try {
            service.toolTurnService().begin(token,context,
                    pendingTurnMessages(groupID,token,messages,persistedBase),params);
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[工具] 群"+groupID
                    +" 工具回合持久化失败，继续执行："+service.safe(e.getMessage()));
        }
        JSONArray toolTrace = new JSONArray();
        JSONObject usage = emptyUsage();
        Map<String,Integer> perToolCalls = new LinkedHashMap<>();
        Set<String> exposedTools = new LinkedHashSet<>(registry.coreToolNames(service.config()));
        if (context != null && context.message != null
                && context.message.contains("[戳一戳]")
                && registry.get("poke-back") != null) {
            exposedTools.add("poke-back");
        }
        if (canSaveSticker(context) && registry.isEnabled("sticker_save",service.config())) {
            exposedTools.add("sticker_save");
        }
        if (canSaveSticker(context)
                && registry.isEnabled("workspace_import_image",service.config())) {
            exposedTools.add("workspace_import_image");
        }
        //一轮内固定工具 schema，后续 tool_search 只扩充可执行集合，不重发不同定义的 tools 前缀。
        List<String> schemaTools = new ArrayList<>(exposedTools);
        int totalCalls = 0;
        int resultCharsUsed = 0;
        boolean finished = false;
        int steps = Math.max(1,service.config().toolMaxSteps);
        long deadline = System.currentTimeMillis()
                +Math.max(10000,service.config().toolLoopMaxMillis);
        try {
            for (int step = 0; step < steps; step++) {
                appendLiveMessages(groupID,token,messages,toolTrace);
                if (active != null && active.cancelled.get()) {
                    return finishResult(token,error("工具回合已取消"),toolTrace,false);
                }
                if (System.currentTimeMillis() >= deadline) {
                    return finishResult(token,error("工具循环超过总时限，未得到最终回复"),
                            toolTrace,false);
                }
                JSONObject callParams = copy(params);
                callParams.put("messages",messages);
                int remainingSeconds = remainingSeconds(deadline);
                callParams.put("timeoutSeconds",remainingSeconds);
                callParams.put("retryCount",service.config().toolAiRetryCount);
                JSONArray schemas = registry.schemas(service.config(),
                        schemaTools);
                JSONObject stepTrace = new JSONObject(true);
                stepTrace.put("stage","tool_step");
                stepTrace.put("step",step + 1);
                stepTrace.put("exposedTools",new JSONArray());
                stepTrace.getJSONArray("exposedTools").addAll(new ArrayList<>(exposedTools));
                stepTrace.put("schemaTools",new JSONArray());
                stepTrace.getJSONArray("schemaTools").addAll(schemaTools);
                stepTrace.put("schemaCount",schemas.size());
                toolTrace.add(stepTrace);
                if (service.config().toolEnable && !schemas.isEmpty()) {
                    callParams.put("tools",schemas);
                    callParams.put("tool_choice","auto");
                    callParams.put("parallel_tool_calls",
                            service.config().toolParallelReadOnlyEnable);
                }
                long start = System.currentTimeMillis();
                PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
                if (ai == null) {
                    return finishResult(token,error("MBB-AI 未启用"),toolTrace,false);
                }
                JSONObject response = callAi(ai,callParams,deadline,active);
                RoleplayAiLog.log(service.plugin().getLogger(),"工具循环",groupID,response,
                        System.currentTimeMillis() - start);
                if (response == null || !response.getBooleanValue("status")) {
                    if (step == 0 && callParams.getJSONArray("tools") != null
                            && recoverWithoutTools(response)) {
                        callParams.remove("tools");
                        callParams.remove("tool_choice");
                        callParams.remove("parallel_tool_calls");
                        response = callAi(ai,callParams,deadline,active);
                    }
                    if (response == null || !response.getBooleanValue("status")) {
                        return finishResult(token,response == null
                                ? error("AI 服务没有返回结果") : response,toolTrace,false);
                    }
                }
                if (active != null && active.cancelled.get()) {
                    return finishResult(token,error("工具回合已取消"),toolTrace,false);
                }
                addUsage(usage,response.getJSONObject("usage"));
                JSONArray calls = response.getJSONArray("toolCalls");
                if (calls == null || calls.isEmpty()) {
                    int liveCount = finishLiveMessages(groupID,token,messages,toolTrace);
                    if (liveCount > 0) {
                        service.toolTurnService().update(token,
                                pendingTurnMessages(groupID,token,messages,persistedBase),params);
                        service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                                +" 最终回复生成期间又收到 "+liveCount+" 条消息，丢弃旧候选并重新生成");
                        continue;
                    }
                    suppressDuplicateFinal(groupID,response,active);
                    if (active != null && active.drawSubmitted) {
                        String content = service.safe(response.getString("content")).trim();
                        if (!content.isEmpty() && !"<SKIP>".equalsIgnoreCase(content)) {
                            service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                                    +" 已提交生图任务，抑制本轮额外完成回复，等待 ComfyUI 回调");
                        }
                        response.put("content","<SKIP>");
                        response.put("drawCallbackSuppressed",true);
                    }
                    response.put("usage",usage);
                    response.put("toolTrace",toolTrace);
                    service.toolTurnService().update(token,
                            pendingTurnMessages(groupID,token,messages,persistedBase),params);
                    service.toolTurnService().finish(token,"finished");
                    finished = true;
                    return response;
                }
                if (System.currentTimeMillis() >= deadline) {
                    JSONObject result = error("工具循环超过总时限，未得到最终回复");
                    result.put("usage",usage);
                    return finishResult(token,result,toolTrace,false);
                }
                JSONObject assistant = new JSONObject(true);
                assistant.put("role","assistant");
                assistant.put("content",service.safe(response.getString("content")));
                assistant.put("tool_calls",calls);
                messages.add(assistant);
                service.toolTurnService().update(token,
                        pendingTurnMessages(groupID,token,messages,persistedBase),params);
                boolean endTurn = false;
                int consumed = executeCalls(context,calls,exposedTools,perToolCalls,
                        totalCalls,resultCharsUsed,deadline,active,messages,toolTrace);
                totalCalls += countCalls(calls);
                resultCharsUsed = consumed;
                appendLiveMessages(groupID,token,messages,toolTrace);
                service.toolTurnService().update(token,
                        pendingTurnMessages(groupID,token,messages,persistedBase),params);
                for (int i = 0; i < calls.size(); i++) {
                    RoleplayToolResult last = active == null ? null : active.lastResults.get(i);
                    if (last != null && last.endTurn) {
                        endTurn = true;
                        break;
                    }
                }
                service.toolTurnService().update(token,
                        pendingTurnMessages(groupID,token,messages,persistedBase),params);
                if (endTurn) {
                    service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                            +" 模型调用 end_turn，本轮保持沉默");
                    JSONObject result = new JSONObject(true);
                    result.put("status",true);
                    result.put("content","<SKIP>");
                    result.put("finishReason","end_turn");
                    result.put("usage",usage);
                    result.put("toolTrace",toolTrace);
                    service.toolTurnService().finish(token,"finished");
                    finished = true;
                    return result;
                }
            }
            JSONObject recovered = recoverWithoutTools(groupID,token,messages,params,usage,
                    toolTrace,deadline);
            if (recovered != null) {
                service.toolTurnService().finish(token,"finished");
                finished = true;
                return recovered;
            }
            JSONObject result = error("工具循环达到最大轮数，未得到最终回复");
            result.put("toolTrace",toolTrace);
            return finishResult(token,result,toolTrace,false);
        } catch (Exception e) {
            service.plugin().getLogger().sendException(e);
            JSONObject result = error("工具循环异常："+service.safe(e.getMessage()));
            result.put("toolTrace",toolTrace);
            return finishResult(token,result,toolTrace,false);
        } finally {
            if (!finished) service.toolTurnService().finish(token,"interrupted");
            end(groupID,token);
        }
    }

    private int executeCalls(RoleplayToolContext context,JSONArray calls,Set<String> exposedTools,
                             Map<String,Integer> perToolCalls,int alreadyCalls,int charsUsed,
                             long deadline,ActiveTurn active,JSONArray messages,JSONArray toolTrace) {
        if (active != null) active.lastResults.clear();
        List<PreparedCall> prepared = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            Object object = calls.get(i);
            PreparedCall call = new PreparedCall();
            call.index = i;
            call.groupID = context.groupID;
            if (!(object instanceof JSONObject)) {
                call.invalid = "工具调用格式无效";
                prepared.add(call);
                continue;
            }
            JSONObject raw = (JSONObject)object;
            call.id = service.safe(raw.getString("id"));
            JSONObject function = raw.getJSONObject("function");
            call.name = function == null ? "" : service.safe(function.getString("name"));
            call.args = parseArgs(function == null ? "" : function.getString("arguments"));
            RoleplayTool tool = registry.get(call.name);
            call.tool = tool;
            if (tool == null) {
                call.invalid = "未知工具："+call.name;
            } else if (!exposedTools.contains(call.name)) {
                if (!registry.isEnabled(call.name,service.config())) {
                    call.invalid = "工具未启用："+call.name;
                } else {
                    //模型已经明确点名了注册表中的工具，直接按需装载并执行，
                    //避免“tool_search 后仍提示未加载”的重复往返。
                    exposedTools.add(call.name);
                    call.invalid = registry.validateArguments(call.name,call.args);
                }
            } else {
                call.invalid = registry.validateArguments(call.name,call.args);
            }
            if (call.invalid.isEmpty() && active != null
                    && "message_send".equals(call.name) && active.substantiveToolSeen) {
                call.invalid = "工具结果已经返回，不要再用 message_send 追加阶段性结论；请直接输出最终回复";
            }
            if (call.invalid.isEmpty() && active != null
                    && "message_send".equals(call.name)) {
                String text = service.safe(call.args.getString("text")).trim();
                if (similarToAny(text,active.sentMessageTexts,0.72)) {
                    call.invalid = "这条追加消息与本次工具回合刚发送的内容重复";
                }
            }
            int used = perToolCalls.containsKey(call.name) ? perToolCalls.get(call.name) : 0;
            if (call.invalid.isEmpty() && (alreadyCalls + prepared.size() >= service.config().toolMaxCalls)) {
                call.invalid = "工具调用次数超过单轮上限";
            }
            if (call.invalid.isEmpty() && tool != null
                    && used >= Math.max(1,tool.maxCallsPerTurn())) {
                call.invalid = "工具 "+call.name+" 本轮调用次数超过上限 "
                        +Math.max(1,tool.maxCallsPerTurn());
            }
            if (call.invalid.isEmpty()) perToolCalls.put(call.name,used + 1);
            prepared.add(call);
        }

        boolean parallel = service.config().toolParallelReadOnlyEnable
                && service.config().toolParallelMax > 1
                && readOnlyCount(prepared) > 1;
        for (PreparedCall call : prepared) {
            if (!call.invalid.isEmpty() || call.tool == null) continue;
            if (parallel && !call.tool.sideEffect()) {
                call.startedAt = System.currentTimeMillis();
                call.future = submit(call,context);
            }
        }
        int consumed = charsUsed;
        for (PreparedCall call : prepared) {
            RoleplayToolResult result;
            if (!call.invalid.isEmpty()) {
                result = RoleplayToolResult.failed(call.invalid);
            } else if (call.future != null) {
                result = await(call,context);
            } else if (call.tool != null && parallel) {
                result = executeWithExecutor(call,context);
            } else {
                result = executeWithExecutor(call,context);
            }
            call.result = result;
            if (active != null) active.lastResults.put(call.index,result);
            if (active != null && result.success && "draw".equals(call.name)) {
                active.drawSubmitted = true;
            }
            recordToolState(active,call,result);
            try {
                service.toolMemoryService().record(context,call.name,call.args,result);
            } catch (Exception e) {
                service.plugin().getLogger().sendWarn("[工具] 群"+context.groupID
                        +" 持久化结果失败："+service.safe(e.getMessage()));
            }
            int remaining = Math.max(0,service.config().toolResultTotalMaxChars - consumed);
            String content = buildToolContent(result,remaining);
            consumed += content.length();
            JSONObject trace = new JSONObject(true);
            trace.put("id",call.id);
            trace.put("name",call.name);
            trace.put("arguments",service.shortText(redact(call.args.toJSONString()),800));
            trace.put("status",result.success);
            trace.put("trust",result.trust);
            trace.put("summary",service.shortText(result.summary,500));
            trace.put("elapsedMs",call.startedAt <= 0 ? 0 : System.currentTimeMillis() - call.startedAt);
            trace.put("resultChars",content.length());
            trace.put("budgetTruncated",remaining <= 0);
            toolTrace.add(trace);
            service.plugin().getLogger().sendInfo("[工具] 群"+context.groupID
                    +" 调用 "+call.name+" 结果="+(result.success ? "成功" : "失败")
                    +" 信任="+result.trust
                    +" 字符="+content.length()
                    +" 摘要="+service.shortText(result.summary,160));
            JSONObject toolMessage = new JSONObject(true);
            toolMessage.put("role","tool");
            toolMessage.put("tool_call_id",call.id);
            toolMessage.put("name",call.name);
            toolMessage.put("content",content);
            messages.add(toolMessage);
            loadTools(exposedTools,result);
        }
        return consumed;
    }

    private RoleplayToolResult await(PreparedCall call,RoleplayToolContext context) {
        long timeout = Math.max(1000,service.config().toolCallMaxMillis
                -Math.max(0,System.currentTimeMillis() - call.startedAt));
        return awaitFuture(call,timeout);
    }

    private RoleplayToolResult executeWithExecutor(PreparedCall call,RoleplayToolContext context) {
        call.startedAt = System.currentTimeMillis();
        Future<RoleplayToolResult> future = submit(call,context);
        if (future == null) return RoleplayToolResult.failed(call.name+" 执行器已关闭");
        call.future = future;
        return awaitFuture(call,Math.max(1000,service.config().toolCallMaxMillis));
    }

    private Future<RoleplayToolResult> submit(PreparedCall call,RoleplayToolContext context) {
        ExecutorService executor = call.tool != null && call.tool.sideEffect()
                ? sideEffectExecutor : toolExecutor();
        try {
            Future<RoleplayToolResult> future = executor.submit(new Callable<RoleplayToolResult>() {
                @Override
                public RoleplayToolResult call() {
                    return registry.execute(context,call.name,call.args);
                }
            });
            ActiveTurn active = activeTurns.get(context.groupID);
            if (active != null) active.futures.add(future);
            return future;
        } catch (RejectedExecutionException e) {
            return null;
        }
    }

    private RoleplayToolResult awaitFuture(PreparedCall call,long timeout) {
        if (call.future == null) return RoleplayToolResult.failed(call.name+" 执行器已关闭");
        try {
            return call.future.get(timeout,TimeUnit.MILLISECONDS);
        } catch (CancellationException e) {
            return RoleplayToolResult.failed(call.name+" 执行已取消");
        } catch (TimeoutException e) {
            call.future.cancel(true);
            return RoleplayToolResult.failed(call.name+" 执行超时，已取消");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.future.cancel(true);
            return RoleplayToolResult.failed(call.name+" 执行被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return RoleplayToolResult.failed(call.name+" 执行失败："+service.safe(cause.getMessage()));
        } finally {
            ActiveTurn active = activeTurns.get(call.groupID);
            if (active != null) active.futures.remove(call.future);
        }
    }

    private void loadTools(Set<String> exposed,RoleplayToolResult result) {
        if (result == null || result.activateTools.isEmpty()) return;
        int max = Math.max(1,service.config().toolLoadedMaxTools);
        for (String name : result.activateTools) {
            if (exposed.size() >= max) break;
            RoleplayTool tool = registry.get(name);
            if (tool != null) exposed.add(name);
        }
    }

    private void recordToolState(ActiveTurn active,PreparedCall call,RoleplayToolResult result) {
        if (active == null || call == null) return;
        if ("message_send".equals(call.name) && result != null && result.success) {
            String text = service.safe(result.data.getString("text")).trim();
            if (!text.isEmpty()) active.sentMessageTexts.add(text);
            return;
        }
        if (!"end_turn".equals(call.name)) {
            active.substantiveToolSeen = true;
        }
    }

    private void suppressDuplicateFinal(long groupID,JSONObject response,ActiveTurn active) {
        if (response == null || active == null || active.sentMessageTexts.isEmpty()) return;
        String finalText = replyText(response.getString("content"));
        if (!similarToAny(finalText,active.sentMessageTexts,0.82)) return;
        response.put("content","<SKIP>");
        response.put("duplicateInterimSuppressed",true);
        service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                +" 最终回复与工具期间追加消息重复，已抑制重复发送："
                +service.shortText(finalText,80));
    }

    private String replyText(String content) {
        RoleplayReplyDraft draft = RoleplayReplyDraft.parse(service.safe(content));
        if (draft == null) return "";
        List<RoleplayReplyDraft.Segment> segments = draft.effectiveSegments();
        if (segments.isEmpty()) return service.safe(draft.text).trim();
        StringBuilder builder = new StringBuilder();
        for (RoleplayReplyDraft.Segment segment : segments) {
            if (segment == null || segment.text == null || segment.text.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(" ");
            builder.append(segment.text.trim());
        }
        return builder.toString();
    }

    private boolean similarToAny(String text,List<String> existing,double threshold) {
        String normalized = SpeechCorpusEntry.normalize(text);
        if (normalized.length() < 4) return false;
        for (String item : existing) {
            String previous = SpeechCorpusEntry.normalize(item);
            if (previous.length() < 4) continue;
            if (normalized.contains(previous) || previous.contains(normalized)) return true;
            Set<String> left = SpeechCorpusEntry.bigrams(normalized);
            Set<String> right = SpeechCorpusEntry.bigrams(previous);
            if (left.isEmpty() || right.isEmpty()) continue;
            int overlap = 0;
            for (String bigram : left) if (right.contains(bigram)) overlap++;
            double score = (double)overlap / Math.max(left.size(),right.size());
            if (score >= threshold) return true;
        }
        return false;
    }

    private String buildToolContent(RoleplayToolResult result,int remaining) {
        String summary = result == null ? "" : service.shortText(result.summary,300);
        if (remaining <= 8) return "{\"e\":1}";
        if (remaining < MIN_RESULT_BUDGET) {
            return truncate("{\"status\":"+(result != null && result.success)
                    +",\"summary\":\""+escapeJson(summary)
                    +"\",\"data\":{\"omitted\":true,\"reason\":\"工具结果总预算已用尽\"}}",remaining);
        }
        JSONObject payload = result == null ? new JSONObject(true) : result.toJson();
        String json = payload.toJSONString();
        if ("external-untrusted".equals(result.trust)) {
            String wrapped = RoleplayExternalContentGuard.wrap("tool",json);
            return truncate(wrapped,remaining);
        }
        return truncate(json,remaining);
    }

    private JSONObject callAi(PluginService ai,JSONObject params,long deadline,ActiveTurn active) {
        Future<JSONObject> future;
        try {
            future = aiExecutor.submit(new Callable<JSONObject>() {
                @Override
                public JSONObject call() {
                    return ai.call("chat",params);
                }
            });
        } catch (RejectedExecutionException e) {
            return error("AI 请求执行器已关闭");
        }
        if (active != null) active.aiFuture = future;
        try {
            return future.get(remainingSeconds(deadline),TimeUnit.SECONDS);
        } catch (CancellationException e) {
            return error("AI 请求已取消");
        } catch (TimeoutException e) {
            future.cancel(true);
            return error("AI 请求超过工具回合时限");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return error("AI 请求等待被中断");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return error("AI 请求失败："+service.safe(cause.getMessage()));
        } finally {
            if (active != null) active.aiFuture = null;
        }
    }

    private JSONObject recoverWithoutTools(long groupID,String token,JSONArray messages,
                                           JSONObject params,JSONObject usage,JSONArray toolTrace,
                                           long deadline) {
        PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
        if (ai == null) return null;
        JSONObject callParams = copy(params);
        callParams.put("retryCount",service.config().toolAiRetryCount);
        callParams.remove("tools");
        callParams.remove("tool_choice");
        callParams.remove("parallel_tool_calls");
        for (int attempt = 0; attempt < 3; attempt++) {
            appendLiveMessages(groupID,token,messages,toolTrace);
            callParams.put("messages",messages);
            callParams.put("timeoutSeconds",remainingSeconds(deadline));
            long start = System.currentTimeMillis();
            JSONObject response = callAi(ai,callParams,deadline,null);
            RoleplayAiLog.log(service.plugin().getLogger(),"工具收尾",groupID,response,
                    System.currentTimeMillis() - start);
            if (response == null || !response.getBooleanValue("status")) return null;
            JSONArray calls = response.getJSONArray("toolCalls");
            if (calls != null && !calls.isEmpty()) return null;
            addUsage(usage,response.getJSONObject("usage"));
            int liveCount = finishLiveMessages(groupID,token,messages,toolTrace);
            if (liveCount > 0) {
                service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                        +" 收尾回复生成期间又收到 "+liveCount+" 条消息，丢弃旧候选并重新生成");
                continue;
            }
            response.put("usage",usage);
            response.put("toolTrace",toolTrace);
            service.plugin().getLogger().sendInfo("[工具] 群"+groupID
                    +" 达到最大轮数后完成一次无工具收尾");
            return response;
        }
        return null;
    }

    /**
     * 工具执行期间的新消息直接进入当前轮上下文，并持久化到未完成回合。
     */
    public boolean captureLiveMessage(GroupMessageEvent event) {
        if (event == null || event.getGroupID() <= 0) return false;
        ActiveTurn active = activeTurns.get(event.getGroupID());
        if (active == null || active.cancelled.get()) return false;
        long selfID = event.getRaw().getLongValue("self_id");
        if (selfID > 0 && event.getUserID() == selfID) return false;
        if (service.blacklistService().contains(event.getGroupID(),event.getUserID())) return false;
        if (service.containsIgnoredContent(event.getMessage())) return false;
        String content = service.extractContent(event.getMessage());
        if (content == null || content.trim().isEmpty() || service.isCommand(content)) return false;
        JSONObject safety = service.getSafetyService().inspect(
                event.getGroupID(),event.getUserID(),content);
        if (safety.getBooleanValue("block")) return false;
        if ((service.hasImageContent(event.getMessage()) || service.hasStickerContent(event.getMessage()))
                && !service.hasMeaningfulText(event.getMessage())) return false;
        LiveMessage live = new LiveMessage();
        live.userID = event.getUserID();
        live.userName = service.senderName(event);
        live.content = content;
        live.messageID = event.getMessageID();
        live.time = System.currentTimeMillis();
        synchronized (active) {
            if (!active.acceptingLive) return false;
            if (active.messages.size() >= service.config().toolLiveMessageMax) {
                service.plugin().getLogger().sendWarn("[工具] 群"+event.getGroupID()
                        +" 工具执行期间新消息超过上限，本条转入下一回合");
                return false;
            }
            active.messages.add(live);
        }
        try {
            service.toolTurnService().appendLive(active.token,live.toJson());
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[工具] 群"+event.getGroupID()
                    +" 持久化 live inbox 失败："+service.safe(e.getMessage()));
        }
        if (event.getMessageID() > 0) {
            if (service.mergedMessageIDs.size() > 500) service.mergedMessageIDs.clear();
            service.mergedMessageIDs.add(event.getMessageID());
        }
        service.recordMessage(event,content,false);
        service.contextService().recordTopic(event.getGroupID(),content,false);
        service.plugin().getLogger().sendInfo("[工具] 群"+event.getGroupID()
                +" 工具执行期间并入新消息 "+live.userName+"（QQ："+live.userID+"）："
                +service.shortText(content,60));
        return true;
    }

    /**
     * 启动后恢复进程中断前已执行过工具、但还没得到最终回复的回合。
     */
    public void recoverInterruptedTurns() {
        if (recoveryRunning.getAndIncrement() != 0) return;
        try {
            List<RoleplayToolTurnService.RecoveredTurn> turns =
                    service.toolTurnService().recoverable();
            for (RoleplayToolTurnService.RecoveredTurn turn : turns) {
                service.toolTurnService().finish(turn.token,"recovering");
                service.submitBackground(() -> recoverTurn(turn));
            }
        } finally {
            recoveryRunning.decrementAndGet();
        }
    }

    private void recoverTurn(RoleplayToolTurnService.RecoveredTurn turn) {
        if (turn == null || turn.context == null || turn.context.eventRaw.isEmpty()) {
            if (turn != null) service.toolTurnService().finish(turn.token,"expired");
            return;
        }
        if (!service.config().enable || !service.isGroupEnabled(turn.context.groupID)) {
            service.toolTurnService().finish(turn.token,"expired");
            return;
        }
        try {
            JSONArray messages;
            if (service.config().conversationAppendEnable) {
                if (turn.messages != null) {
                    for (int i = 0; i < turn.messages.size(); i++) {
                        Object object = turn.messages.get(i);
                        if (!(object instanceof JSONObject)) continue;
                        JSONObject item = (JSONObject)object;
                        String role = service.safe(item.getString("role"));
                        if ("assistant".equals(role) || "tool".equals(role)) {
                            service.conversationContextService().appendMessage("group",
                                    turn.context.groupID,item,"group-tool:"+turn.token+":"+i,
                                    "group-tool:"+turn.token);
                        }
                    }
                }
                messages = service.conversationContextService().buildMessages("group",
                        turn.context.groupID,service.promptAssembler().buildStableSystemPrompt());
            } else {
                messages = turn.messages == null ? new JSONArray() : turn.messages;
            }
            if (turn.liveMessages != null && !turn.liveMessages.isEmpty()) {
                StringBuilder live = new StringBuilder("工具执行期间收到的补充消息：\n");
                for (Object object : turn.liveMessages) {
                    if (!(object instanceof JSONObject)) continue;
                    JSONObject item = (JSONObject)object;
                    live.append(item.getString("userName")).append("（QQ：")
                            .append(item.getLongValue("userID")).append("）：")
                            .append(service.shortText(item.getString("content"),500)).append("\n");
                }
                String liveText = live.toString();
                messages.add(service.message("user",liveText));
                if (service.config().conversationAppendEnable) {
                    long firstID = 0L;
                    long lastID = 0L;
                    for (Object object : turn.liveMessages) {
                        if (!(object instanceof JSONObject)) continue;
                        long messageID = ((JSONObject)object).getLongValue("messageID");
                        if (firstID <= 0 || (messageID > 0 && messageID < firstID)) firstID = messageID;
                        if (messageID > lastID) lastID = messageID;
                    }
                    service.conversationContextService().append("group",turn.context.groupID,"user",
                            liveText,"group-tool-recover:"+turn.token+":"+firstID+":"+lastID,
                            "group-tool-recover:"+turn.token);
                }
            }
            PluginService ai = service.plugin().getServer().getPluginManager().getService("MBB-AI");
            if (ai == null) {
                service.toolTurnService().finish(turn.token,"interrupted");
                return;
            }
            JSONObject params = copy(turn.params);
            params.put("messages",messages);
            params.put("timeoutSeconds",Math.max(15,Math.min(120,service.config().toolLoopMaxMillis / 1000)));
            params.put("retryCount",0);
            params.remove("tools");
            params.remove("tool_choice");
            params.remove("parallel_tool_calls");
            JSONObject response = callAi(ai,params,System.currentTimeMillis()
                    +Math.max(30000L,service.config().toolLoopMaxMillis),null);
            RoleplayAiLog.log(service.plugin().getLogger(),"工具恢复",turn.context.groupID,
                    response,0L);
            if (response == null || !response.getBooleanValue("status")) {
                service.toolTurnService().finish(turn.token,"interrupted");
                return;
            }
            if (response.getJSONArray("toolCalls") != null
                    && !response.getJSONArray("toolCalls").isEmpty()) {
                service.toolTurnService().finish(turn.token,"interrupted");
                return;
            }
            service.completeRecoveredToolTurn(turn,response);
            service.toolTurnService().finish(turn.token,"recovered");
        } catch (Exception e) {
            service.plugin().getLogger().sendException(e);
            service.toolTurnService().finish(turn.token,"interrupted");
        }
    }

    private int appendLiveMessages(long groupID,String token,JSONArray messages,JSONArray toolTrace) {
        return drainLiveMessages(groupID,token,messages,toolTrace,false);
    }

    private JSONArray pendingTurnMessages(long groupID,String token,JSONArray messages,int base) {
        if (!service.config().conversationAppendEnable || messages == null) return messages;
        JSONArray result = new JSONArray();
        int start = Math.max(0,Math.min(base,messages.size()));
        for (int i = start; i < messages.size(); i++) {
            Object value = messages.get(i);
            result.add(value);
            if (!(value instanceof JSONObject)) continue;
            JSONObject item = (JSONObject)value;
            String role = service.safe(item.getString("role"));
            if (!"assistant".equals(role) && !"tool".equals(role)) continue;
            service.conversationContextService().appendMessage("group",groupID,item,
                    "group-tool:"+token+":"+(i - start),"group-tool:"+token);
        }
        return result;
    }

    /**
     * 最终回复采纳前关闭 live inbox，再原子地 drain 一次。
     * 关闭后到达的消息不会再进入当前回合，而会正常进入下一批。
     */
    private int finishLiveMessages(long groupID,String token,JSONArray messages,JSONArray toolTrace) {
        return drainLiveMessages(groupID,token,messages,toolTrace,true);
    }

    private int drainLiveMessages(long groupID,String token,JSONArray messages,
                                  JSONArray toolTrace,boolean stopAccepting) {
        ActiveTurn active = activeTurns.get(groupID);
        if (active == null || !active.token.equals(token)) return 0;
        List<LiveMessage> drained = new ArrayList<>();
        if (stopAccepting) {
            synchronized (active) {
                active.acceptingLive = false;
                LiveMessage live;
                while ((live = active.messages.poll()) != null) drained.add(live);
            }
        } else {
            LiveMessage live;
            while ((live = active.messages.poll()) != null) drained.add(live);
        }
        if (drained.isEmpty()) return 0;
        StringBuilder builder = new StringBuilder("工具执行期间又收到了新消息。把它们和工具结果一起考虑：\n");
        for (LiveMessage item : drained) {
            builder.append(item.userName).append("（QQ：").append(item.userID).append("）：")
                    .append(service.shortText(item.content,500)).append("\n");
        }
        String liveText = builder.toString();
        messages.add(service.message("user",liveText));
        if (service.config().conversationAppendEnable) {
            long firstID = drained.get(0).messageID;
            long lastID = drained.get(drained.size() - 1).messageID;
            service.conversationContextService().append("group",groupID,"user",liveText,
                    "group-tool-live:"+token+":"+firstID+":"+lastID,"group-tool-live:"+token);
        }
        try {
            service.toolTurnService().clearLive(token);
        } catch (Exception e) {
            service.plugin().getLogger().sendWarn("[工具] 群"+groupID
                    +" 清理 live inbox 失败："+service.safe(e.getMessage()));
        }
        JSONObject trace = new JSONObject(true);
        trace.put("stage","live_messages");
        trace.put("count",drained.size());
        toolTrace.add(trace);
        return drained.size();
    }

    private String begin(long groupID) {
        String token = groupID+"-"+sequence.incrementAndGet();
        activeTurns.put(groupID,new ActiveTurn(token));
        return token;
    }

    private void end(long groupID,String token) {
        ActiveTurn active = activeTurns.get(groupID);
        if (active != null && active.token.equals(token)) activeTurns.remove(groupID,active);
    }

    private JSONObject parseArgs(String arguments) {
        try {
            JSONObject value = JSONObject.parseObject(arguments);
            return value == null ? new JSONObject(true) : value;
        } catch (Exception e) {
            return new JSONObject(true);
        }
    }

    private JSONObject copy(JSONObject value) {
        JSONObject result = new JSONObject(true);
        if (value != null) result.putAll(value);
        return result;
    }

    private boolean recoverWithoutTools(JSONObject response) {
        String type = response == null ? "" : service.safe(response.getString("errorType"));
        return "http_400".equals(type) || "http_422".equals(type);
    }

    private int countCalls(JSONArray calls) {
        return calls == null ? 0 : calls.size();
    }

    private boolean hasCurrentImage(RoleplayToolContext context) {
        if (context == null || service.safe(context.eventRaw).trim().isEmpty()) return false;
        try {
            JSONObject raw = JSONObject.parseObject(context.eventRaw);
            JSONArray message = raw == null ? null : raw.getJSONArray("message");
            if (message == null) return false;
            for (int i = 0; i < message.size(); i++) {
                JSONObject segment = message.getJSONObject(i);
                if (segment == null) continue;
                String type = segment.getString("type");
                if ("image".equals(type) || "mface".equals(type)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean canSaveSticker(RoleplayToolContext context) {
        if (hasCurrentImage(context)) return true;
        return context != null && service.recentImageForSave(context.groupID,context.userID,
                context.message) != null;
    }

    private int readOnlyCount(List<PreparedCall> calls) {
        int count = 0;
        for (PreparedCall call : calls) {
            if (call.invalid.isEmpty() && call.tool != null && !call.tool.sideEffect()) count++;
        }
        return count;
    }

    private JSONObject finishResult(String token,JSONObject result,JSONArray toolTrace,
                                    boolean success) {
        if (result != null) result.put("toolTrace",toolTrace);
        service.toolTurnService().finish(token,success ? "finished" : "interrupted");
        return result;
    }

    private int remainingSeconds(long deadline) {
        long remaining = Math.max(1000,deadline - System.currentTimeMillis());
        return (int)Math.max(5,Math.min(600,(remaining + 999) / 1000));
    }

    private void ensureExecutor(RoleplayConfig config) {
        int threads = Math.max(2,Math.min(8,config.toolParallelMax));
        if (toolExecutor != null && toolThreads == threads) return;
        ExecutorService previous = toolExecutor;
        toolExecutor = Executors.newFixedThreadPool(threads,runnable -> {
            Thread thread = new Thread(runnable,"MoBoxBot-Roleplay-Tool");
            thread.setDaemon(true);
            return thread;
        });
        toolThreads = threads;
        if (previous != null) previous.shutdown();
    }

    private ExecutorService toolExecutor() {
        ExecutorService executor = toolExecutor;
        if (executor == null) {
            ensureExecutor(service.config());
            executor = toolExecutor;
        }
        return executor;
    }

    private void awaitShutdown(ExecutorService executor) {
        if (executor == null) return;
        try {
            executor.awaitTermination(3,TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String text,int max) {
        String value = service.safe(text);
        if (max <= 0) return "";
        if (value.length() <= max) return value;
        String suffix = "\n[工具结果已按单轮总预算截断]";
        if (max <= suffix.length()) return value.substring(0,max);
        int cut = Math.max(0,max - suffix.length());
        return value.substring(0,cut)+suffix;
    }

    private String escapeJson(String value) {
        return service.safe(value).replace("\\","\\\\").replace("\"","\\\"")
                .replace("\n","\\n").replace("\r","\\r");
    }

    private JSONObject emptyUsage() {
        JSONObject usage = new JSONObject(true);
        usage.put("promptTokens",0);
        usage.put("completionTokens",0);
        usage.put("totalTokens",0);
        usage.put("cachedPromptTokens",0);
        return usage;
    }

    private void addUsage(JSONObject total,JSONObject current) {
        if (total == null || current == null) return;
        total.put("promptTokens",total.getIntValue("promptTokens")+current.getIntValue("promptTokens"));
        total.put("completionTokens",total.getIntValue("completionTokens")+current.getIntValue("completionTokens"));
        total.put("totalTokens",total.getIntValue("totalTokens")+current.getIntValue("totalTokens"));
        total.put("cachedPromptTokens",total.getIntValue("cachedPromptTokens")
                +current.getIntValue("cachedPromptTokens"));
    }

    private String redact(String text) {
        return service.safe(text).replaceAll(
                "(?i)(api[_-]?key|token|authorization|cookie)\\s*[:=]\\s*[^,}\\s]+","$1=***");
    }

    private JSONObject error(String message) {
        JSONObject result = new JSONObject(true);
        result.put("status",false);
        result.put("message",message);
        return result;
    }

    private static class ActiveTurn {
        private final String token;
        private final ConcurrentLinkedQueue<LiveMessage> messages = new ConcurrentLinkedQueue<>();
        private final Map<Integer,RoleplayToolResult> lastResults = new ConcurrentHashMap<>();
        private final Set<Future<RoleplayToolResult>> futures =
                Collections.newSetFromMap(new ConcurrentHashMap<Future<RoleplayToolResult>,Boolean>());
        private final List<String> sentMessageTexts =
                Collections.synchronizedList(new ArrayList<String>());
        private volatile Future<JSONObject> aiFuture;
        private volatile boolean substantiveToolSeen;
        private volatile boolean drawSubmitted;
        private boolean acceptingLive = true;
        private final java.util.concurrent.atomic.AtomicBoolean cancelled =
                new java.util.concurrent.atomic.AtomicBoolean();

        private ActiveTurn(String token) {
            this.token = token;
        }
    }

    private static class LiveMessage {
        private long userID;
        private String userName = "";
        private String content = "";
        private long messageID;
        private long time;

        private JSONObject toJson() {
            JSONObject result = new JSONObject(true);
            result.put("userID",userID);
            result.put("userName",userName);
            result.put("content",content);
            result.put("messageID",messageID);
            result.put("time",time);
            return result;
        }
    }

    private static class PreparedCall {
        private int index;
        private long groupID;
        private String id = "";
        private String name = "";
        private JSONObject args = new JSONObject(true);
        private RoleplayTool tool;
        private String invalid = "";
        private long startedAt;
        private Future<RoleplayToolResult> future;
        private RoleplayToolResult result;
    }
}
