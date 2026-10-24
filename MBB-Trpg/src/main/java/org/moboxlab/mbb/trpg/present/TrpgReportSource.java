package org.moboxlab.mbb.trpg.present;

import org.moboxlab.mbb.trpg.script.Script;
import org.moboxlab.mbb.trpg.session.SessionEvent;

/** 战报数据来源端口：把收尾事件补齐成图与纯文本共用的字段。 */
public interface TrpgReportSource {

    /** 组装战报；剧本缺失时返回只含基础字段的战报，不返回 null。 */
    TrpgReport report(SessionEvent event, Script script);
}
