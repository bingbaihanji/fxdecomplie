package com.bingbaihanji.fxdecomplie.service.task;

/** 任务池快照，用于可观测性 */
public record TaskSnapshot(int activeCount, int queueLength, int poolSize) {
    public static final TaskSnapshot EMPTY = new TaskSnapshot(0, 0, 0);
}
