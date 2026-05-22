package com.spuitelf;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

import javax.inject.Inject;
import java.awt.*;
import java.time.Duration;

public class CalcifiedRockDespawnTimerOverlay extends Overlay {
    private static final int TIMER_Y_OFFSET = -10;
    private static final int RESET_TEXT_SPACING = 2;

    private final CalcifiedRockDespawnTimerPlugin plugin;
    private final CalcifiedRockDespawnTimerConfig config;
    private final Client client;

    @Inject
    private CalcifiedRockDespawnTimerOverlay(CalcifiedRockDespawnTimerPlugin plugin, CalcifiedRockDespawnTimerConfig config, Client client) {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setLayer(OverlayLayer.UNDER_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        for (CalcifiedRockState rockState : plugin.uniqueRocks) {
            if (!rockState.shouldShowTimer()) {
                continue;
            }
            LocalPoint lp = LocalPoint.fromWorld(client, rockState.worldPoint);
            if (lp == null) {
                continue;
            }
            LocalPoint centeredPoint = new LocalPoint(lp.getX() + rockState.centerOffset.getX(),
                    lp.getY() + rockState.centerOffset.getY(), client.getTopLevelWorldView());
            Point point = Perspective.localToCanvas(client,
                    centeredPoint,
                    client.getTopLevelWorldView().getPlane());
            if (point == null) {
                continue;
            }
            Point renderPoint = new Point(point.getX(), point.getY() + TIMER_Y_OFFSET);
            if (config.timerType() == TimerTypes.PIE) {
                ProgressPieComponent pie = new ProgressPieComponent();
                pie.setPosition(renderPoint);
                pie.setBorderColor(rockState.getTimerColor());
                pie.setDiameter(config.uiSizeNormal());
                pie.setFill(rockState.getTimerColor());
                pie.setProgress(rockState.getTimePercent());
                pie.render(graphics);
            } else if (config.timerType() == TimerTypes.TICKS) {
                String text = rockState.getTimeTicks().toString();
                CustomTextComponent textComponent = new CustomTextComponent(text,
                        new java.awt.Point(renderPoint.getX(), renderPoint.getY()));
                textComponent.setColor(rockState.getTimerColor());
                textComponent.render(graphics);
            } else if (config.timerType() == TimerTypes.SECONDS) {
                Duration duration = Duration.ofSeconds(rockState.getTimeSeconds(plugin.getSubTick()));
                String text = String.format("%s%d:%02d",
                        duration.toSeconds() < 0 ? "-" : "",
                        Math.abs(duration.toMinutesPart()),
                        Math.abs(duration.toSecondsPart()));
                CustomTextComponent textComponent = new CustomTextComponent(text,
                        new java.awt.Point(renderPoint.getX(), renderPoint.getY()));
                textComponent.setColor(rockState.getTimerColor());
                textComponent.render(graphics);
            }

            if (shouldRenderResetText(rockState)) {
                String resetText = formatResetText(rockState);
                CustomTextComponent resetTextComponent = new CustomTextComponent(
                        resetText,
                        new java.awt.Point(renderPoint.getX(), renderPoint.getY() + config.uiSizeNormal() + RESET_TEXT_SPACING)
                );
                resetTextComponent.setColor(config.resetTimerColor());
                resetTextComponent.render(graphics);
            }
        }
        return null;
    }

    private boolean shouldRenderResetText(CalcifiedRockState rockState) {
        return config.resetTimerDisplayType() != ResetTimerDisplayTypes.OFF && rockState.shouldShowResetTimer();
    }

    private String formatResetText(CalcifiedRockState rockState) {
        if (config.resetTimerDisplayType() == ResetTimerDisplayTypes.TICKS) {
            return String.valueOf(rockState.getResetTicksRemaining());
        }

        Duration duration = Duration.ofSeconds(rockState.getResetSecondsRemaining());
        return String.format("%d:%02d", Math.abs(duration.toMinutesPart()), Math.abs(duration.toSecondsPart()));
    }
}


