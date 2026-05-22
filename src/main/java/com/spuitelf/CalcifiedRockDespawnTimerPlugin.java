package com.spuitelf;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.Angle;
import net.runelite.api.coords.Direction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@PluginDescriptor(
        name = "Calcified Rock Despawn Timer",
        description = "Show an estimate of the remaining time until a calcified rock despawns",
        configName = CalcifiedRockDespawnTimerConfig.GROUP_NAME
)
public class CalcifiedRockDespawnTimerPlugin extends Plugin {
    private static final int CAM_TORUM_REGION = 6037;
    private static final int CAM_TORUM_STREAM_OBJECT_ID = 51493;
    private static final int STREAM_TIMER_SECONDS = 30;
    private static final Set<Integer> MINING_WALL_ANIMATIONS = ImmutableSet.of(
            AnimationID.HUMAN_MINING_3A_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_ZALCANO_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,
            AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL,
            AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL,
            AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL
    );

    @Inject
    private Client client;

    @Inject
    private CalcifiedRockDespawnTimerConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private CalcifiedRockDespawnTimerOverlay calcifiedRockDespawnTimerOverlay;

    @Getter
    private int subTick = 0;

    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> subTickFuture;
    private final HashMap<WorldPoint, CalcifiedRockState> rockAtLocation = new HashMap<>();
    protected HashSet<CalcifiedRockState> uniqueRocks = new HashSet<>();
    private final HashSet<WorldPoint> streamLocations = new HashSet<>();
    private final HashMap<Player, CalcifiedRockState> playerMiningRock = new HashMap<>();
    private final ArrayList<Runnable> deferTickQueue = new ArrayList<>();
    private final HashSet<Player> startupSuppressedMiningPlayers = new HashSet<>();
    private boolean sceneStateSynchronized = false;
    private boolean synchronizingScene = false;
    private int lastLocalMiningXp = -1;
    private int nextGarbageCollect = 25;
    private int nextAnimationRecheck = 0;

    @Provides
    CalcifiedRockDespawnTimerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(CalcifiedRockDespawnTimerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        CustomTextComponent.updateFontSizes(config.uiSizeNormal(), config.uiSizeNormal());
        overlayManager.add(calcifiedRockDespawnTimerOverlay);
        deferTickQueue.add(() -> client.getTopLevelWorldView().players().forEach(this::handlePlayerMining));
        subTickFuture = executor.scheduleAtFixedRate(
                () -> subTick += Constants.CLIENT_TICK_LENGTH,
                0,
                Constants.CLIENT_TICK_LENGTH,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(calcifiedRockDespawnTimerOverlay);
        clearTrackedState();
        subTickFuture.cancel(false);
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!event.getGroup().equals(CalcifiedRockDespawnTimerConfig.GROUP_NAME)) {
            return;
        }
        if (event.getKey().equals(CalcifiedRockDespawnTimerConfig.UI_SIZE_NORMAL)) {
            CustomTextComponent.updateFontSizes(config.uiSizeNormal(), config.uiSizeNormal());
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.HOPPING || event.getGameState() == GameState.LOGGING_IN) {
            clearTrackedState();
        }
    }

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        if (!inCamTorumMiningArea()) {
            clearTrackedState();
            subTick = 0;
            return;
        }

        if (!sceneStateSynchronized) {
            synchronizeSceneState();
            sceneStateSynchronized = true;
        }

        deferTickQueue.forEach(Runnable::run);
        deferTickQueue.clear();

        HashSet<CalcifiedRockState> minedRocks = new HashSet<>(playerMiningRock.values());
        uniqueRocks.forEach(rock -> rock.tick(minedRocks.contains(rock)));
        nextGarbageCollect--;
        if (nextGarbageCollect <= 0) {
            ArrayList<CalcifiedRockState> toDelete = new ArrayList<>();
            nextGarbageCollect = 25; // 15 seconds
            Player localPlayer = client.getLocalPlayer();
            uniqueRocks.forEach(rock -> {
                boolean isFarAway = localPlayer != null
                        && rock.worldPoint.getPlane() == localPlayer.getWorldLocation().getPlane()
                        && rock.worldPoint.distanceTo(localPlayer.getWorldLocation()) > 150;
                if (isFarAway && !rock.shouldShowTimer(DebugLevel.NONE)) {
                    toDelete.add(rock);
                }
            });
            toDelete.forEach(this::deleteRock);
        }
        // Mining animations can initially face the wrong direction, so recheck periodically.
        nextAnimationRecheck--;
        if (nextAnimationRecheck <= 0) {
            nextAnimationRecheck = 4;
            client.getTopLevelWorldView().players().forEach(this::handlePlayerMining);
        }
        subTick = 0;
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        GameObject gameObject = event.getGameObject();
        if (!CalcifiedRockConfig.isCalcifiedRock(gameObject)) {
            return;
        }
        trackRock(gameObject);
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        GameObject gameObject = event.getGameObject();
        if (!CalcifiedRockConfig.isCalcifiedRock(gameObject)) {
            return;
        }
        CalcifiedRockState rockState = rockAtLocation.get(gameObject.getWorldLocation());
        if (rockState == null) {
            return;
        }
        if (DebugLevel.BASIC.shouldShow(config.debugLevel())) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE,
                    "CRDT DEBUG",
                    rockState.rockName.toLowerCase() + " despawned with " + rockState.getTimeSeconds(getSubTick()) + "s remaining",
                    "");
        }
        deleteRock(rockState);
    }

    @Subscribe
    public void onDecorativeObjectSpawned(DecorativeObjectSpawned event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        DecorativeObject decorativeObject = event.getDecorativeObject();
        if (decorativeObject.getId() != CAM_TORUM_STREAM_OBJECT_ID) {
            return;
        }

        WorldPoint streamPoint = decorativeObject.getWorldLocation();
        streamLocations.add(streamPoint);

        CalcifiedRockState streamRock = rockAtLocation.get(streamPoint);
        if (streamRock != null) {
            streamRock.forceStreamTimerSeconds(STREAM_TIMER_SECONDS);
        }
    }

    @Subscribe
    public void onDecorativeObjectDespawned(DecorativeObjectDespawned event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        DecorativeObject decorativeObject = event.getDecorativeObject();
        if (decorativeObject.getId() != CAM_TORUM_STREAM_OBJECT_ID) {
            return;
        }

        WorldPoint streamPoint = decorativeObject.getWorldLocation();
        streamLocations.remove(streamPoint);

        CalcifiedRockState streamRock = rockAtLocation.get(streamPoint);
        if (streamRock != null && !isRockUnderStream(streamRock)) {
            streamRock.clearStreamTimer();
        }
    }

    @Subscribe
    public void onAnimationChanged(AnimationChanged event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        if (event.getActor() instanceof Player) {
            Player player = (Player) event.getActor();
            deferTickQueue.add(() -> this.handlePlayerMining(player));
        }
    }

    @Subscribe
    public void onPlayerDespawned(PlayerDespawned event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        playerMiningRock.remove(event.getPlayer());
        startupSuppressedMiningPlayers.remove(event.getPlayer());
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        if (!inCamTorumMiningArea()) {
            return;
        }

        if (event.getSkill() != Skill.MINING) {
            return;
        }

        int currentXp = event.getXp();
        if (lastLocalMiningXp < 0) {
            lastLocalMiningXp = currentXp;
            return;
        }
        if (currentXp <= lastLocalMiningXp) {
            return;
        }
        lastLocalMiningXp = currentXp;

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return;
        }

        if (startupSuppressedMiningPlayers.contains(localPlayer)) {
            return;
        }

        CalcifiedRockState interactingRock = playerMiningRock.get(localPlayer);
        if (interactingRock == null) {
            interactingRock = findClosestFacingRock(localPlayer);
            if (interactingRock != null) {
                playerMiningRock.put(localPlayer, interactingRock);
            }
        }

        if (interactingRock != null) {
            interactingRock.startTimer();
        }
    }

    void handlePlayerMining(Player player) {
        if (!isMiningCalcifiedRock(player)) {
            playerMiningRock.remove(player);
            startupSuppressedMiningPlayers.remove(player);
            return;
        }

        if (startupSuppressedMiningPlayers.contains(player)) {
            playerMiningRock.remove(player);
            return;
        }

        CalcifiedRockState interactingRock = findClosestFacingRock(player);
        if (interactingRock == null) {
            playerMiningRock.remove(player);
            return;
        }

        if (player == client.getLocalPlayer()) {
            playerMiningRock.put(player, interactingRock);
            return;
        }

        interactingRock.startTimer();
        playerMiningRock.put(player, interactingRock);
    }

    void deleteRock(CalcifiedRockState rockState) {
        playerMiningRock.entrySet().removeIf(entry -> entry.getValue() == rockState);
        rockState.points.forEach(rockAtLocation::remove);
        uniqueRocks.remove(rockState);
    }

    private void clearTrackedState() {
        rockAtLocation.clear();
        uniqueRocks.clear();
        streamLocations.clear();
        playerMiningRock.clear();
        startupSuppressedMiningPlayers.clear();
        deferTickQueue.clear();
        sceneStateSynchronized = false;
        lastLocalMiningXp = -1;
    }

    private void synchronizeSceneState() {
        Scene scene = client.getTopLevelWorldView().getScene();
        Tile[][][] tiles = scene.getTiles();
        HashSet<WorldPoint> scannedRockLocations = new HashSet<>();

        synchronizingScene = true;
        try {
            for (Tile[][] planeTiles : tiles) {
                for (Tile[] columnTiles : planeTiles) {
                    for (Tile tile : columnTiles) {
                        if (tile == null) {
                            continue;
                        }

                        DecorativeObject decorativeObject = tile.getDecorativeObject();
                        if (decorativeObject != null && decorativeObject.getId() == CAM_TORUM_STREAM_OBJECT_ID) {
                            // Existing streams at login/hop have unknown age, so only track presence.
                            streamLocations.add(decorativeObject.getWorldLocation());
                        }

                        GameObject[] gameObjects = tile.getGameObjects();
                        for (GameObject gameObject : gameObjects) {
                            if (gameObject == null || !CalcifiedRockConfig.isCalcifiedRock(gameObject)) {
                                continue;
                            }

                            if (scannedRockLocations.add(gameObject.getWorldLocation())) {
                                trackRock(gameObject);
                            }
                        }
                    }
                }
            }
        } finally {
            synchronizingScene = false;
        }

        // Existing streams at login/hop have unknown age; still suppress reset tracking while present.
        uniqueRocks.forEach(rock -> rock.setStreamTimerActive(isRockUnderStream(rock)));

        client.getTopLevelWorldView().players().forEach(player -> {
            if (isMiningCalcifiedRock(player)) {
                startupSuppressedMiningPlayers.add(player);
            }
        });
    }

    private void trackRock(GameObject gameObject) {
        CalcifiedRockState existing = rockAtLocation.get(gameObject.getWorldLocation());
        if (existing != null) {
            deleteRock(existing);
        }

        CalcifiedRockState rockState = new CalcifiedRockState(gameObject, client, config);
        rockState.points.forEach(point -> rockAtLocation.put(point, rockState));
        uniqueRocks.add(rockState);
        if (!synchronizingScene) {
            applyStreamTimerIfPresent(rockState);
        }
    }

    private void applyStreamTimerIfPresent(CalcifiedRockState rockState) {
        if (isRockUnderStream(rockState)) {
            rockState.forceStreamTimerSeconds(STREAM_TIMER_SECONDS);
        }
    }

    private boolean isRockUnderStream(CalcifiedRockState rockState) {
        return rockState.points.stream().anyMatch(streamLocations::contains);
    }

    private boolean inCamTorumMiningArea() {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null) {
            return false;
        }

        WorldPoint worldPoint = localPlayer.getWorldLocation();
        return worldPoint != null && worldPoint.getRegionID() == CAM_TORUM_REGION;
    }

    @Nullable
    CalcifiedRockState findClosestFacingRock(Player player) {
        WorldPoint actorLocation = player.getWorldLocation();
        Direction direction = new Angle(player.getOrientation()).getNearestDirection();
        WorldPoint facingPoint = neighborPoint(actorLocation, direction);
        return rockAtLocation.get(facingPoint);
    }

    private WorldPoint neighborPoint(WorldPoint point, Direction direction) {
        switch (direction) {
            case NORTH:
                return point.dy(1);
            case SOUTH:
                return point.dy(-1);
            case EAST:
                return point.dx(1);
            case WEST:
                return point.dx(-1);
            default:
                throw new IllegalStateException();
        }
    }

    private boolean isMiningCalcifiedRock(Player player) {
        return MINING_WALL_ANIMATIONS.contains(player.getAnimation());
    }
}

