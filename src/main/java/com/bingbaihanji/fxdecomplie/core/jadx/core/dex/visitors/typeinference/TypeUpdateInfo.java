package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 类型更新信息管理类，负责收集 排队 应用和回滚类型更新
 * 维护一个更新映射表，以 IdentityHashMap 按参数对象身份存储待应用的更新，
 * 并在更新序列超过限制时抛出溢出异常支持队列化请求和回调处理
 */
public class TypeUpdateInfo {
    private final MethodNode mth;
    private final TypeUpdateFlags flags;
    private final Map<InsnArg, TypeUpdateEntry> updateMap = new IdentityHashMap<>();
    private final List<TypeUpdateRequest> queue = new ArrayList<>();
    private final List<TypeUpdateRequest> callbackQueue = new ArrayList<>();
    private final int updatesLimitCount;
    private int updateSeq = 0;

    /**
     * 构造类型更新信息实例
     *
     * @param mth   所属方法节点
     * @param flags 类型更新标志
     * @param args  Jadx 运行参数，用于计算更新次数上限
     */
    public TypeUpdateInfo(MethodNode mth, TypeUpdateFlags flags, JadxArgs args) {
        this.mth = mth;
        this.flags = flags;
        this.updatesLimitCount = mth.getInsnsCount() * args.getTypeUpdatesLimitCount();
    }

    public void queueRequest(TypeUpdateRequest request) {
        queue.add(request);
    }

    public void saveCallback(TypeUpdateRequest request) {
        if (request.getCallback() != null) {
            callbackQueue.add(request);
        }
    }

    public @Nullable TypeUpdateRequest pollNextRequest() {
        return ListUtils.removeLast(queue);
    }

    public @Nullable TypeUpdateRequest pollNextCallback() {
        return ListUtils.removeLast(callbackQueue);
    }

    /**
     * 请求对指定参数进行类型更新记录更新序列号和变更类型，
     * 如果同一参数被重复更新则抛出异常，并在达到更新上限时抛出溢出异常
     *
     * @param arg        要更新的 InsnArg 参数
     * @param changeType 新的类型值
     */
    public void requestUpdate(InsnArg arg, ArgType changeType) {
        TypeUpdateEntry prev = updateMap.put(arg, new TypeUpdateEntry(updateSeq++, arg, changeType));
        if (prev != null) {
            throw new JadxRuntimeException("Unexpected type update override for arg: " + arg
                    + " types: prev=" + prev.getType() + ", new=" + changeType
                    + ", insn: " + arg.getParentInsn());
        }
        if (updateSeq > updatesLimitCount) {
            throw new JadxOverflowException("Type inference error: updates count limit reached"
                    + " with updateSeq = " + updateSeq + ". Try increasing type updates limit count.");
        }
        if (updateSeq % 100 == 0) {
            // 偶尔检查线程中断 (每次更新都检查过于频繁)
            Utils.checkThreadInterrupt();
        }
    }

    /**
     * 回滚对指定参数的更新，移除此参数及其之后的所有更新 (按序列号)
     *
     * @param arg 要回滚更新的 InsnArg 参数
     */
    public void rollbackUpdate(InsnArg arg) {
        TypeUpdateEntry removed = updateMap.remove(arg);
        if (removed != null) {
            int seq = removed.getSeq();
            updateMap.values().removeIf(upd -> upd.getSeq() > seq);
        }
    }

    /**
     * 按更新序列号排序后，将所有待定更新统一应用到对应的参数上
     */
    public void applyUpdates() {
        updateMap.values().stream().sorted()
                .forEach(upd -> upd.getArg().setType(upd.getType()));
    }

    public boolean isProcessed(InsnArg arg) {
        return updateMap.containsKey(arg);
    }

    public boolean hasUpdateWithType(InsnArg arg, ArgType type) {
        TypeUpdateEntry updateEntry = updateMap.get(arg);
        if (updateEntry != null) {
            return updateEntry.getType().equals(type);
        }
        return false;
    }

    public ArgType getType(InsnArg arg) {
        TypeUpdateEntry updateEntry = updateMap.get(arg);
        if (updateEntry != null) {
            return updateEntry.getType();
        }
        return arg.getType();
    }

    public MethodNode getMth() {
        return mth;
    }

    public boolean isEmpty() {
        return updateMap.isEmpty();
    }

    public List<TypeUpdateEntry> getSortedUpdates() {
        return updateMap.values().stream().sorted().collect(Collectors.toList());
    }

    public TypeUpdateFlags getFlags() {
        return flags;
    }

    @Override
    public String toString() {
        return "TypeUpdateInfo{" + flags + ' ' + getSortedUpdates() + '}';
    }
}
