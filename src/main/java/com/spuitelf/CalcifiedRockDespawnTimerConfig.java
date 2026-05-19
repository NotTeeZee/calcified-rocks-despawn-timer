package com.spuitelf;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

import java.awt.*;

@ConfigGroup(CalcifiedRockDespawnTimerConfig.GROUP_NAME)
public interface CalcifiedRockDespawnTimerConfig extends Config {
    String GROUP_NAME = "calcifiedrock-despawn-timer";

    @ConfigItem(
            keyName = "timerType",
            name = "Timer Display Type",
            position = 1,
            description = "The UI style for displaying the estimated remaining time on each calcified rock."
    )
    default TimerTypes timerType() {
        return TimerTypes.PIE;
    }

    @ConfigSection(
            name = "Advanced UI Customization",
            description = "Detailed options for customizing timer overlay",
            position = 2,
            closedByDefault = true
    )
    String uiCustomizationSection = "uiCustomizationSection";

    @ConfigItem(
            keyName = "timerColorLow",
            name = "Timer Color Low",
            position = 0,
            description = "Overlay color when the rock is about to despawn.",
            section = uiCustomizationSection
    )
    default Color timerColorLow() {
        return new Color(220, 0, 0);
    }

    @ConfigItem(
            keyName = "timerColorMedium",
            name = "Timer Color Medium",
            position = 1,
            description = "Overlay color when the rock is nearly running out of time.",
            section = uiCustomizationSection
    )
    default Color timerColorMedium() {
        return new Color(230, 160, 0);
    }

    @ConfigItem(
            keyName = "timerColorHigh",
            name = "Timer Color High",
            position = 2,
            description = "Overlay color when the rock is fairly new.",
            section = uiCustomizationSection
    )
    default Color timerColorHigh() {
        return new Color(230, 230, 0);
    }

    @ConfigItem(
            keyName = "timerColorFull",
            name = "Timer Color Full",
            position = 3,
            description = "Overlay color when the rock timer is newly started.",
            section = uiCustomizationSection
    )
    default Color timerColorFull() {
        return new Color(0, 255, 0);
    }

    String UI_SIZE_NORMAL = "uiSizeNormal";

    @ConfigItem(
            keyName = UI_SIZE_NORMAL,
            name = "UI Size Normal",
            position = 4,
            description = "Size of the timer.",
            section = uiCustomizationSection
    )
    default int uiSizeNormal() {
        return 16;
    }

    @ConfigSection(
            name = "Debug",
            description = "Basic debugging features.",
            position = 3,
            closedByDefault = true
    )
    String debugSection = "debugSection";

    @ConfigItem(
            keyName = "debugLevel",
            name = "Debug Level",
            description = "Controls the amount of debug information displayed.",
            section = debugSection
    )
    default DebugLevel debugLevel() {
        return DebugLevel.NONE;
    }

}

