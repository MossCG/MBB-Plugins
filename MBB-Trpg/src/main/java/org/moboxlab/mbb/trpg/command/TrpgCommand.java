package org.moboxlab.mbb.trpg.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.moboxlab.moboxbot.API.Command.BotCommand;
import org.moboxlab.moboxbot.API.Command.CommandPermission;
import org.moboxlab.moboxbot.API.Command.CommandSender;

/**
 * /trpg 指令入口：只注册前缀与权限，业务全部委托 {@link TrpgCommandService}。
 *
 * <p>框架冷却恒为 0，插件内冷却（30 秒开团、1 秒其余）由局面服务按动作分别判定，
 * 这样才能与「本群正在处理上一条」等拒绝共用同一套去重。</p>
 */
public final class TrpgCommand extends BotCommand {

    private final TrpgCommandService service;

    public TrpgCommand(TrpgCommandService service) {
        if (service == null) {
            throw new IllegalArgumentException("指令服务不能为空");
        }
        this.service = service;
    }

    @Override
    public List<String> prefix() {
        return Collections.singletonList("trpg");
    }

    @Override
    public CommandPermission permission() {
        return CommandPermission.EVERYONE;
    }

    @Override
    public int cooldownSeconds() {
        return 0;
    }

    @Override
    public String description() {
        return "MBB-Trpg 跑团指令";
    }

    @Override
    public List<String> usage() {
        List<String> usages = new ArrayList<String>();
        for (TrpgSubcommand sub : TrpgSubcommand.values()) {
            usages.add("/trpg " + sub.label()
                    + (sub.usage().isEmpty() ? "" : " " + sub.usage()));
        }
        return Collections.unmodifiableList(usages);
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        return service.handle(sender, args);
    }
}
