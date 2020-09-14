/*
 *       _____  _       _    _____                                _
 *      |  __ \| |     | |  / ____|                              | |
 *      | |__) | | ___ | |_| (___   __ _ _   _  __ _ _ __ ___  __| |
 *      |  ___/| |/ _ \| __|\___ \ / _` | | | |/ _` | '__/ _ \/ _` |
 *      | |    | | (_) | |_ ____) | (_| | |_| | (_| | | |  __/ (_| |
 *      |_|    |_|\___/ \__|_____/ \__, |\__,_|\__,_|_|  \___|\__,_|
 *                                    | |
 *                                    |_|
 *            PlotSquared plot management system for Minecraft
 *                  Copyright (C) 2020 IntellectualSites
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.plotsquared.core.queue.subscriber;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AtomicDouble;
import com.google.inject.Inject;
import com.plotsquared.core.configuration.Settings;
import com.plotsquared.core.configuration.caption.Caption;
import com.plotsquared.core.configuration.caption.TranslatableCaption;
import com.plotsquared.core.player.PlotPlayer;
import com.plotsquared.core.queue.ChunkCoordinator;
import com.plotsquared.core.util.task.PlotSquaredTask;
import com.plotsquared.core.util.task.TaskManager;
import com.plotsquared.core.util.task.TaskTime;
import net.kyori.adventure.text.minimessage.Template;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The default PlotSquared Progress Subscriber. Can be used for both console and player tasks.
 * It is the {@link ProgressSubscriber} returned by {@link com.plotsquared.core.inject.factory.ProgressSubscriberFactory}.
 * Runs a repeating synchronous task notifying the given actor about any updates, saving updates notified by the ChunkCoordinator.
 */
public class DefaultProgressSubscriber implements ProgressSubscriber {

    @Nonnull private final AtomicDouble progress = new AtomicDouble(0);
    @Nonnull private final AtomicBoolean started = new AtomicBoolean(false);
    @Nonnull private final TaskTime interval;
    @Nonnull private final TaskTime wait;
    @Nonnull private final PlotPlayer<?> actor;
    @Nonnull private final Caption caption;
    @Inject @Nonnull private TaskManager taskManager;
    private PlotSquaredTask task;

    @Inject public DefaultProgressSubscriber(@Nonnull final PlotPlayer<?> actor) {
        Preconditions.checkNotNull(actor,
            "Actor cannot be null when using DefaultProgressSubscriber! Make sure if attempting to use custom Subscribers it is correctly parsed to the queue!");
        this.actor = actor;
        this.interval = TaskTime.ms(Settings.QUEUE.NOTIFY_INTERVAL);
        this.wait = TaskTime.ms(Settings.QUEUE.NOTIFY_WAIT);
        this.caption = TranslatableCaption.of("working.progress");
    }

    public DefaultProgressSubscriber(@Nonnull final PlotPlayer<?> actor,
                                     @Nonnull final TaskManager taskManager,
                                     final long interval,
                                     final long wait,
                                     @Nullable final Caption caption) {
        Preconditions.checkNotNull(actor,
            "Actor cannot be null when using DefaultProgressSubscriber! Make sure if attempting to use custom Subscribers it is correctly parsed to the queue!");
        this.actor = actor;
        this.interval = TaskTime.ms(interval);
        this.wait = TaskTime.ms(wait);
        this.taskManager = taskManager;
        if (caption == null) {
            this.caption = TranslatableCaption.of("working.progress");
        } else {
            this.caption = caption;
        }
    }

    @Override public void notifyProgress(@Nonnull ChunkCoordinator coordinator, float progress) {
        this.progress.set(progress);
        if (coordinator.isCancelled() || progress >= 1) {
            if (task != null) {
                task.cancel();
            }
        } else if (started.compareAndSet(false, true)) {
            taskManager.taskLater(() -> task = taskManager.taskRepeat(() -> {
                if (!started.get()) {
                    return;
                }
                actor.sendMessage(caption, Template.of("progress", String.valueOf((double) Math.round(this.progress.doubleValue() * 100) / 100)));
            }, interval), wait);
        }
    }
}
