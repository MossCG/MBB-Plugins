package org.moboxlab.mbb.roleplay;

import com.alibaba.fastjson.JSONObject;
import org.moboxlab.moboxbot.API.Command.CommandSender;

/**
 * /role train 训练语料与本地小模型管理
 */
class RoleplayTrainCommandHandler {
    private final RoleplayCommand command;

    RoleplayTrainCommandHandler(RoleplayCommand command) {
        this.command = command;
    }

    void handleTrain(CommandSender sender,String[] args) {
        RoleplayService service = command.service();
        RoleplayTrainService train = service.trainService();
        String sub = args.length > 2 ? args[2].toLowerCase() : "status";

        if ("corpus".equals(sub)) {
            int limit = args.length > 3 ? parseInt(args[3],0) : 0;
            submitCorpus(sender,limit,false);
            return;
        }

        if ("fresh".equals(sub)) {
            int limit = args.length > 3 ? parseInt(args[3],0) : 0;
            submitCorpus(sender,limit,true);
            return;
        }

        if ("learn".equals(sub)) {
            sender.sendMessage("正在提交 /role learn 采集的语料...");
            JSONObject result = train.trainLearned();
            if (!result.getBooleanValue("status")) {
                sender.sendMessage("提交失败："+result.getString("message"));
                return;
            }
            StringBuilder builder = new StringBuilder("特挑语料已提交训练");
            builder.append("\n样本：").append(result.getIntValue("samples")).append(" 条");
            if (result.getIntValue("skipped") > 0) {
                builder.append("，跳过 ").append(result.getIntValue("skipped")).append(" 条");
            }
            builder.append("\n数据集：").append(result.getString("datasetId"));
            if (result.getBooleanValue("started")) {
                String jobId = result.getString("jobId");
                builder.append("\n训练任务：").append(jobId);
                builder.append("\n训练结束后会自动回报结果，也可以用 /role train 查看进度");
                train.trackJob(sender,jobId);
            }
            sender.sendMessage(builder.toString());
            return;
        }

        if ("cutoff".equals(sub)) {
            if (args.length <= 3) {
                sender.sendMessage("当前训练截止："+train.cutoffText()
                        +"\n下次 /role train corpus 会从这里往后取新消息。"
                        +"\n用法：/role train cutoff <yyyy-MM-dd HH:mm:ss> 或 /role train cutoff reset");
                return;
            }
            String value = command.joinArgs(args,3);
            if ("reset".equalsIgnoreCase(value)) {
                train.resetCutoff();
                sender.sendMessage("截止游标已重置，下次训练会从最早的群消息开始。");
                return;
            }
            JSONObject result = train.setCutoffByTime(value);
            sender.sendMessage(result.getBooleanValue("status")
                    ? "截止已设为 "+result.getString("cutoffText")+"，下次从这里往后取。"
                    : "设置失败："+result.getString("message"));
            return;
        }

        if ("reload".equals(sub)) {
            JSONObject result = train.reloadAdapter();
            sender.sendMessage(result.getBooleanValue("status")
                    ? "已通知 MBB-Model 重新加载 adapter。"
                    : "重载失败："+result.getString("message"));
            return;
        }

        JSONObject status = train.serviceStatus();
        if (!status.getBooleanValue("status")) {
            JSONObject health = status.getJSONObject("health");
            sender.sendMessage("MBB-Model 不可用"
                    +"\n地址："+service.config().modelServiceUrl
                    +"\n原因："+(health == null ? "没有返回" : service.safe(health.getString("message")))
                    +"\n先确认模型服务已经启动（run.ps1）。");
            return;
        }
        JSONObject health = status.getJSONObject("health");
        JSONObject job = status.getJSONObject("train");
        StringBuilder builder = new StringBuilder("本地小模型状态");
        builder.append("\n服务：").append(service.config().modelServiceUrl).append(" 正常");
        builder.append("\n后端：").append(service.safe(health.getString("backend")))
                .append("，模型：").append(service.safe(health.getString("model")))
                .append("，已加载：").append(health.getBooleanValue("backendReady") ? "是" : "否");
        builder.append("\n当前 adapter：").append(service.safe(health.getString("adapter")));
        builder.append("\n训练器：").append(service.safe(health.getString("trainer")));
        if (job != null && job.containsKey("jobId")) {
            builder.append("\n最近任务：").append(job.getString("jobId"));
            builder.append("\n  状态：").append(job.getString("state"))
                    .append("（").append(job.getIntValue("progress")).append("%）");
            String message = service.safe(job.getString("message"));
            if (!message.isEmpty()) builder.append("\n  信息：").append(message);
            if (job.getIntValue("samples") > 0) {
                builder.append("\n  样本：").append(job.getIntValue("samples")).append(" 条");
            }
            if (job.getIntValue("trainedSamples") > 0) {
                builder.append("\n  实际训练：").append(job.getIntValue("trainedSamples")).append(" 条");
            }
            if (job.getIntValue("skippedSamples") > 0) {
                builder.append("\n  跳过异常样本：").append(job.getIntValue("skippedSamples")).append(" 条");
            }
            if (job.getIntValue("steps") > 0) {
                builder.append("\n  步数：").append(job.getIntValue("steps"));
            }
            if (job.get("finalLoss") != null) {
                builder.append("\n  最终 loss：").append(job.get("finalLoss"));
            }
            String requestedMode = service.safe(job.getString("requestedMode"));
            String resolvedMode = service.safe(job.getString("resolvedMode"));
            if (!requestedMode.isEmpty()) {
                builder.append("\n  训练方式：").append("fresh".equals(requestedMode)
                        ? "新开 adapter" : "继续上一个 adapter");
                if (!resolvedMode.isEmpty() && !resolvedMode.equals(requestedMode)) {
                    builder.append("（实际 ").append(resolvedMode).append("）");
                }
            }
            String initialAdapter = service.safe(job.getString("initialAdapter"));
            if (!initialAdapter.isEmpty()) {
                builder.append("\n  起点 adapter：").append(initialAdapter);
            }
        } else {
            builder.append("\n最近任务：暂无");
        }
        builder.append("\n训练截止：").append(train.cutoffText());
        sender.sendMessage(builder.toString());
    }

    private void submitCorpus(CommandSender sender,int limit,boolean fresh) {
        RoleplayService service = command.service();
        RoleplayTrainService train = service.trainService();
        sender.sendMessage(fresh
                ? "正在从最早群消息导出语料并新开 adapter，稍等一下..."
                : "正在导出群聊语料并继续训练上一个 adapter，稍等一下...");
        JSONObject result = fresh ? train.trainCorpusFresh(limit) : train.trainCorpus(limit);
        if (!result.getBooleanValue("status")) {
            sender.sendMessage("训练提交失败："+result.getString("message"));
            return;
        }
        StringBuilder builder = new StringBuilder(fresh
                ? "新 adapter 训练已提交"
                : "群聊语料已提交继续训练");
        builder.append("\n样本：").append(result.getIntValue("samples")).append(" 条")
                .append("（扫描 ").append(result.getIntValue("scanned"))
                .append(" 条，跳过 ").append(result.getIntValue("skipped")).append(" 条）");
        builder.append("\n截止时间推进到：").append(result.getString("cutoffText"));
        builder.append("\n数据集：").append(result.getString("datasetId"));
        if (result.getBooleanValue("started")) {
            String jobId = result.getString("jobId");
            builder.append("\n训练任务：").append(jobId);
            builder.append("\n训练结束后会自动回报结果，也可以用 /role train 查看进度");
            train.trackJob(sender,jobId);
        } else if (result.containsKey("startMessage")) {
            builder.append("\n训练未启动：").append(result.getString("startMessage"));
        } else {
            builder.append("\n训练未自动启动（trainAutoStart=false）");
        }
        sender.sendMessage(builder.toString());
    }

    private int parseInt(String value,int defaultValue) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
