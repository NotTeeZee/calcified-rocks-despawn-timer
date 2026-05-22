package com.spuitelf;

import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public class CalcifiedRockState {
    static final int RESET_AFTER_UNMINED_TICKS = 56;
    static final int RESET_DISPLAY_AFTER_UNMINED_TICKS = 5;

    WorldPoint worldPoint;
    Point centerOffset;
    List<WorldPoint> points;
    String rockName;
    boolean timerStarted = false;

    private int ticksLeft;
    private final int maxTicks;
    private int unminedTicks = 0;
    private boolean minedThisTick = false;
    private boolean streamTimerActive = false;
    private final Client client;
    private final CalcifiedRockDespawnTimerConfig config;

    public CalcifiedRockState(GameObject rock, Client client, CalcifiedRockDespawnTimerConfig config) {
        worldPoint = rock.getWorldLocation();
        this.client = client;
        this.config = config;
        CalcifiedRockConfig rockConfig = CalcifiedRockConfig.getRockById(rock.getId());
        rockName = rockConfig.name();
        maxTicks = rockConfig.getMaxTicks();
        ticksLeft = maxTicks;
        centerOffset = getCenterOffset(rock);
        points = getPoints(rock);
    }

    void startTimer() {
        if (ticksLeft <= 0) {
            ticksLeft = maxTicks;
        }
        if (!timerStarted) {
            timerStarted = true;
        }
        unminedTicks = 0;
    }

    void forceTimerSeconds(int seconds) {
        int forcedTicks = (int) Math.ceil((seconds * 1000d) / Constants.GAME_TICK_LENGTH);
        ticksLeft = Math.max(forcedTicks, 0);
        timerStarted = true;
        streamTimerActive = false;
        unminedTicks = 0;
    }

    void forceStreamTimerSeconds(int seconds) {
        int forcedTicks = (int) Math.ceil((seconds * 1000d) / Constants.GAME_TICK_LENGTH);
        ticksLeft = Math.max(forcedTicks, 0);
        timerStarted = true;
        streamTimerActive = true;
        unminedTicks = 0;
    }

    void clearStreamTimer() {
        streamTimerActive = false;
    }

    void setStreamTimerActive(boolean streamTimerActive) {
        this.streamTimerActive = streamTimerActive;
        if (streamTimerActive) {
            unminedTicks = 0;
        }
    }

    void tick(boolean minedThisTick) {
        this.minedThisTick = minedThisTick;
        if (!timerStarted) {
            unminedTicks = 0;
            return;
        }

        if (streamTimerActive) {
            unminedTicks = 0;
            if (ticksLeft > 0) {
                ticksLeft--;
            }
            return;
        }

        if (minedThisTick) {
            unminedTicks = 0;
            if (ticksLeft > 0) {
                ticksLeft--;
            }
            return;
        }

        unminedTicks++;
        if (unminedTicks >= RESET_AFTER_UNMINED_TICKS) {
            ticksLeft = maxTicks;
            timerStarted = false;
            unminedTicks = 0;
            return;
        }

        if (ticksLeft > 0) {
            ticksLeft--;
        }
    }

    boolean shouldShowTimer(DebugLevel debugLevel) {
        if (DebugLevel.BASIC.shouldShow(debugLevel) && !timerStarted) {
            return true;
        }
        return timerStarted;
    }

    boolean shouldShowTimer() {
        return shouldShowTimer(config.debugLevel());
    }

    Color getTimerColor() {
        double percent = getTimePercent() * 100;
        if (percent < 15) {
            return config.timerColorLow();
        }
        if (percent < 40) {
            return config.timerColorMedium();
        }
        if (percent < 80) {
            return config.timerColorHigh();
        }
        return config.timerColorFull();
    }

    Float getTimePercent() {
        return Math.max(ticksLeft / (float) maxTicks, 0f);
    }

    Integer getTimeTicks() {
        return Math.max(ticksLeft, 0);
    }

    Integer getTimeSeconds(int subTickMs) {
        int tickDelta = timerStarted && ticksLeft > 0 ? -1 : 0;
        int secondsLeft = (int) Math.floor((ticksLeft * Constants.GAME_TICK_LENGTH + subTickMs * tickDelta) / 1000f);
        return Math.max(secondsLeft, 0);
    }

    boolean shouldShowResetTimer() {
        return timerStarted
                && !streamTimerActive
                && !minedThisTick
                && unminedTicks >= RESET_DISPLAY_AFTER_UNMINED_TICKS;
    }

    Integer getResetTicksRemaining() {
        return Math.max(RESET_AFTER_UNMINED_TICKS - unminedTicks, 0);
    }

    Integer getResetSecondsRemaining() {
        return (int) Math.ceil((getResetTicksRemaining() * Constants.GAME_TICK_LENGTH) / 1000d);
    }

    private List<WorldPoint> getPoints(GameObject gameObject) {
        WorldPoint minPoint = getSWWorldPoint(gameObject);
        WorldPoint maxPoint = getNEWorldPoint(gameObject);

        if (minPoint.equals(maxPoint)) {
            return Collections.singletonList(minPoint);
        }

        final int plane = minPoint.getPlane();
        final List<WorldPoint> list = new ArrayList<>();
        for (int x = minPoint.getX(); x <= maxPoint.getX(); x++) {
            for (int y = minPoint.getY(); y <= maxPoint.getY(); y++) {
                list.add(new WorldPoint(x, y, plane));
            }
        }
        return list;
    }

    private Point getCenterOffset(GameObject gameObject) {
        int x = 0;
        int y = 0;
        if (gameObject.sizeX() % 2 == 0) {
            x = (gameObject.sizeX() - 1) * Perspective.LOCAL_HALF_TILE_SIZE;
        }
        if (gameObject.sizeY() % 2 == 0) {
            y = (gameObject.sizeY() - 1) * Perspective.LOCAL_HALF_TILE_SIZE;
        }
        return new Point(x, y);
    }

    private WorldPoint getSWWorldPoint(GameObject gameObject) {
        return getWorldPoint(gameObject, GameObject::getSceneMinLocation);
    }

    private WorldPoint getNEWorldPoint(GameObject gameObject) {
        return getWorldPoint(gameObject, GameObject::getSceneMaxLocation);
    }

    private WorldPoint getWorldPoint(GameObject gameObject, Function<GameObject, Point> pointFunction) {
        Point point = pointFunction.apply(gameObject);
        return WorldPoint.fromScene(client.getTopLevelWorldView(),  point.getX(), point.getY(), gameObject.getPlane());
    }

}


