package org.moboxlab.mbb.trpg.script;

import java.util.List;
import java.util.concurrent.locks.Lock;

/**
 * 剧本库读接口：内置与导入剧本的统一视图。
 *
 * <p>剧本库是读多写少的共享资源：解析剧本、开新局这类读取路径只持读锁，
 * 删除与替换的「检查 + 文件操作」整段持写锁。调用方持锁顺序固定为「先群锁后库锁」，
 * 删除与替换不取群锁。读写锁不覆盖网络发送。</p>
 *
 * <p>读取路径不抛受检异常：未知 id 返回 null，由命令层统一回 E_NOT_FOUND。
 * 写入路径校验失败抛 {@link ScriptException}，携带错误码与路径。</p>
 */
public interface ScriptCatalog {

    /**
     * 按 id 取剧本。
     *
     * @param id 剧本 id
     * @return 剧本；未知 id 或 id 为 null 时返回 null
     */
    Script get(String id);

    /**
     * 按 id 取剧本摘要（来源与节点计数）。
     *
     * @param id 剧本 id
     * @return 摘要；未知 id 时返回 null
     */
    ScriptInfo info(String id);

    /** 列出全部可用剧本摘要，按剧本 id 升序。 */
    List<ScriptInfo> list();

    /**
     * 启动加载时被跳过的剧本文件数，供 {@code script list} 提示。
     *
     * <p>只有磁盘剧本库实现有意义：文件损坏、id 冲突、文件名与 id 不一致时跳过并计数；
     * 内存实现与内置目录返回 0。</p>
     */
    default int skippedFileCount() {
        return 0;
    }

    /**
     * 删除导入剧本。
     *
     * <p>内置剧本回 E_BUILTIN；未知 id 回 E_NOT_FOUND；被未归档局引用回 E_IN_USE
     * （引用检查由实现方在写锁内完成）。</p>
     *
     * @param id 剧本 id
     * @throws ScriptException 删除被拒绝时抛出
     */
    void delete(String id) throws ScriptException;

    /**
     * 用一份已通过校验的内容替换（或新建）导入剧本，实现方负责原子写入与缓存更新。
     *
     * @param id      剧本 id
     * @param content UTF-8 剧本内容
     * @throws ScriptException 写入被拒绝时抛出
     */
    void replace(String id, byte[] content) throws ScriptException;

    /** 获取未加锁的库读锁，由调用方 try/finally 加解锁。 */
    Lock acquireReadLock();

    /** 获取未加锁的库写锁，由调用方 try/finally 加解锁。 */
    Lock acquireWriteLock();
}
