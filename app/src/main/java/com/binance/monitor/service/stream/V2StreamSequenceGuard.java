/*
 * v2 stream 顺序守卫，分别按 busSeq / marketSeq 过滤重复或倒序消息。
 * MonitorService 通过它避免旧连接或重复 marketTick 回写当前运行态。
 */
package com.binance.monitor.service.stream;

public final class V2StreamSequenceGuard {

    private long lastAppliedBusSeq;
    private long lastAppliedMarketSeq;

    // 判断当前 busSeq 是否仍应被消费；0 或负值视为无法排序的消息，直接放行。
    public synchronized boolean shouldApplyBusSeq(long busSeq) {
        if (busSeq <= 0L) {
            return true;
        }
        if (busSeq <= lastAppliedBusSeq) {
            return false;
        }
        return true;
    }

    // 仅在消息真正成功应用后才推进 bus 序列，避免坏包把后续新消息一起挡掉。
    public synchronized void commitAppliedBusSeq(long busSeq) {
        if (busSeq <= 0L || busSeq <= lastAppliedBusSeq) {
            return;
        }
        lastAppliedBusSeq = busSeq;
    }

    // 判断当前 marketSeq 是否仍应被消费；0 或负值视为无法排序的消息，直接放行。
    public synchronized boolean shouldApplyMarketSeq(long marketSeq) {
        if (marketSeq <= 0L) {
            return true;
        }
        if (marketSeq <= lastAppliedMarketSeq) {
            return false;
        }
        return true;
    }

    // 仅在消息真正成功应用后才推进 market 序列，避免旧行情再次覆盖当前分钟。
    public synchronized void commitAppliedMarketSeq(long marketSeq) {
        if (marketSeq <= 0L || marketSeq <= lastAppliedMarketSeq) {
            return;
        }
        lastAppliedMarketSeq = marketSeq;
    }

    // 新连接建立后重置顺序守卫，允许消费新序列的 bootstrap。
    public synchronized void reset() {
        lastAppliedBusSeq = 0L;
        lastAppliedMarketSeq = 0L;
    }
}
