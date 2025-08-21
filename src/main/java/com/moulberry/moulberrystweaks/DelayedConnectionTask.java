package com.moulberry.moulberrystweaks;

import net.minecraft.network.Connection;

import java.util.function.Consumer;

public class DelayedConnectionTask {

    public long delayNanos;
    public Runnable task;

    public DelayedConnectionTask(long delayNanos, Runnable task) {
        this.delayNanos = delayNanos;
        this.task = task;
    }

}
