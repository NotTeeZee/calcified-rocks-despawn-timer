package com.spuitelf;

import lombok.Getter;
import net.runelite.api.GameObject;

import java.util.HashMap;
import java.util.Map;

public enum CalcifiedRockConfig {
    CALCIFIED_ROCK(70, new int[]{
            51485,
            51487,
            51491,
    });

    @Getter
    private final int maxTicks;
    private final int[] rockIds;
    private static final Map<Integer, CalcifiedRockConfig> rockMap = new HashMap<>();

    static {
        for (CalcifiedRockConfig rockConfig : values()) {
            for (int rockId : rockConfig.rockIds) {
                rockMap.put(rockId, rockConfig);
            }
        }
    }

    CalcifiedRockConfig(int maxSeconds, int[] rockIds) {
        this.maxTicks = (int) Math.round(maxSeconds / 0.6d);
        this.rockIds = rockIds;
    }

    static CalcifiedRockConfig getRockById(int gameObjectId) {
        return rockMap.get(gameObjectId);
    }

    static boolean isCalcifiedRock(GameObject gameObject) {
        return rockMap.containsKey(gameObject.getId());
    }
}


