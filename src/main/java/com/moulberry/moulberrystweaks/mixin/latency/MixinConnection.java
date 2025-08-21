package com.moulberry.moulberrystweaks.mixin.latency;

import com.moulberry.moulberrystweaks.DelayedConnectionTask;
import com.moulberry.moulberrystweaks.MoulberrysTweaks;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Mixin(Connection.class)
public abstract class MixinConnection {

    @Unique
    private final ReentrantLock schedulerLock = new ReentrantLock();
    @Unique
    private final List<DelayedConnectionTask> delayedTasks = new ArrayList<>();
    @Unique
    private long lastFlushedNanos = 0L;
    @Unique
    private volatile boolean doTaskDelay = true;

    @Shadow
    private Channel channel;
    @Shadow
    @Final
    private PacketFlow receiving;

    @Shadow
    private volatile @Nullable DisconnectionDetails delayedDisconnect;
    @Shadow
    private @Nullable DisconnectionDetails disconnectionDetails;

    @Shadow
    protected abstract void doSendPacket(Packet<?> packet, @Nullable PacketSendListener sendListener, boolean flush);
    @Shadow
    protected abstract void channelRead0(ChannelHandlerContext context, Packet<?> packet);

    @Unique
    private boolean shouldPerformDelay() {
        if (this.receiving == PacketFlow.CLIENTBOUND && this.channel != null && this.channel.isOpen() &&
                this.delayedDisconnect == null && this.disconnectionDetails == null) {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null && player.hasClientLoaded() && player.hasPermissions(2) && MoulberrysTweaks.additionalLatencyMs > 0;
        }
        return false;
    }

    @Inject(method = {
        "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V",
        "setupCompression",
        "handleDisconnection",
        "setReadOnly"
    }, at = @At("HEAD"))
    public void forceFlushDelayedTaskMethods(CallbackInfo ci) {
        this.forceFlushDelayedTasks();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V", at = @At(value = "HEAD"), cancellable = true)
    public void delayChannelRead0(ChannelHandlerContext context, Packet<?> packet, CallbackInfo ci) {
        if (!this.doTaskDelay) {
            return;
        }
        if (this.shouldPerformDelay()) {
            this.scheduleDelayedTask(MoulberrysTweaks.additionalLatencyMs * 500000, () -> {
                this.channelRead0(context, packet);
            });
            ci.cancel();
        } else {
            this.forceFlushDelayedTasks();
        }
    }

    @Inject(method = "doSendPacket", at = @At("HEAD"), cancellable = true)
    public void delayDoSendPacket(Packet<?> packet, @Nullable PacketSendListener sendListener, boolean flush, CallbackInfo ci) {
        if (!this.doTaskDelay) {
            return;
        }
        if (this.shouldPerformDelay()) {
            this.scheduleDelayedTask(MoulberrysTweaks.additionalLatencyMs * 500000, () -> {
                this.doSendPacket(packet, sendListener, flush);
            });
            ci.cancel();
        } else {
            this.forceFlushDelayedTasks();
        }
    }

    @Unique
    private void scheduleDelayedTask(long delayNs, Runnable task) {
        if (delayNs <= 0L) {
            throw new IllegalArgumentException();
        }
        this.schedulerLock.lock();
        try {
            if (this.delayedTasks.isEmpty()) {
                this.lastFlushedNanos = System.nanoTime();
                this.delayedTasks.add(new DelayedConnectionTask(delayNs, task));
                this.channel.eventLoop().schedule(this::processDelayedTasks, delayNs, TimeUnit.NANOSECONDS);
            } else {
                long delta = System.nanoTime() - this.lastFlushedNanos;
                this.delayedTasks.add(new DelayedConnectionTask(delayNs + delta, task));
            }
        } finally {
            this.schedulerLock.unlock();
        }
    }

    @Unique
    private void forceFlushDelayedTasks() {
        this.schedulerLock.lock();
        try {
            Iterator<DelayedConnectionTask> iterator = this.delayedTasks.iterator();
            while (iterator.hasNext()) {
                DelayedConnectionTask task = iterator.next();
                iterator.remove();

                try {
                    this.doTaskDelay = false;
                    task.task.run();
                    this.doTaskDelay = true;
                } catch (Throwable t) {
                    MoulberrysTweaks.LOGGER.error("Error while force flushing delayed tasks", t);
                    sneakyThrow(t);
                }
            }
        } finally {
            this.schedulerLock.unlock();
        }
    }

    @Unique
    private void processDelayedTasks() {
        if (!this.shouldPerformDelay()) {
            this.forceFlushDelayedTasks();
            return;
        }
        this.schedulerLock.lock();
        try {
            long nanoTime = System.nanoTime();
            long delta = nanoTime - this.lastFlushedNanos;
            this.lastFlushedNanos = nanoTime;

            if (this.delayedTasks.isEmpty()) {
                return;
            }

            Throwable error = null;

            boolean stillHasPending = false;
            boolean canRunTasks = true;
            long nextPendingNanos = 0L;

            Iterator<DelayedConnectionTask> iterator = this.delayedTasks.iterator();
            while (iterator.hasNext()) {
                DelayedConnectionTask task = iterator.next();
                task.delayNanos -= delta;
                if (stillHasPending) {
                    continue;
                }
                if (task.delayNanos <= 0L && canRunTasks) {
                    iterator.remove();
                    try {
                        this.doTaskDelay = false;
                        task.task.run();
                        this.doTaskDelay = true;
                    } catch (Throwable t) {
                        error = t;
                        canRunTasks = false;
                    }
                } else {
                    nextPendingNanos = Math.max(1L, task.delayNanos);
                    stillHasPending = true;
                }
            }

            if (stillHasPending) {
                this.channel.eventLoop().schedule(this::processDelayedTasks, nextPendingNanos, TimeUnit.NANOSECONDS);
            }

            if (error != null) {
                MoulberrysTweaks.LOGGER.error("Error while processing delayed tasks", error);
                sneakyThrow(error);
            }
        } finally {
            this.schedulerLock.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

}
