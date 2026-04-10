package net.blueva.arcade.modules.bridge_race.listener;

import net.blueva.arcade.api.game.GameContext;
import net.blueva.arcade.api.game.GamePhase;
import net.blueva.arcade.modules.bridge_race.game.BridgeRaceGameManager;
import net.blueva.arcade.modules.bridge_race.state.BridgeRaceArenaState;
import net.blueva.arcade.modules.bridge_race.state.BridgeRaceStateRegistry;
import net.blueva.arcade.modules.bridge_race.support.BridgeRaceStatsService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

public class BridgeRaceListener implements Listener {

    private final BridgeRaceGameManager gameManager;
    private final BridgeRaceStateRegistry stateRegistry;
    private final BridgeRaceStatsService statsService;

    public BridgeRaceListener(BridgeRaceGameManager gameManager, BridgeRaceStateRegistry stateRegistry, BridgeRaceStatsService statsService) {
        this.gameManager = gameManager;
        this.stateRegistry = stateRegistry;
        this.statsService = statsService;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);
        if (context == null || !context.isPlayerPlaying(player)) {
            return;
        }

        gameManager.handlePlayerMove(context, player, event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);
        if (context == null) return;

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Allow placing blocks within bounds
        Location blockLoc = event.getBlock().getLocation();
        if (!context.isInsideBounds(blockLoc)) {
            event.setCancelled(true);
            return;
        }

        // Un-cancel the event (core cancels by default)
        event.setCancelled(false);

        // Track the placed block for cleanup
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId != null) {
            BridgeRaceArenaState state = stateRegistry.getState(arenaId);
            if (state != null) {
                state.trackPlacedBlock(blockLoc);
                statsService.recordBlockPlaced(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        GameContext<Player, Location, World, Material, ItemStack, Sound, Block, Entity> context =
                gameManager.getGameContext(player);
        if (context == null) return;

        if (context.getPhase() != GamePhase.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Only allow breaking blocks that were placed by players
        Location blockLoc = event.getBlock().getLocation();
        Integer arenaId = stateRegistry.getArenaId(player);
        if (arenaId != null) {
            BridgeRaceArenaState state = stateRegistry.getState(arenaId);
            if (state != null && state.getPlacedBlocks().contains(blockLoc)) {
                event.setCancelled(false);
                state.getPlacedBlocks().remove(blockLoc);
                return;
            }
        }
        event.setCancelled(true);
    }
}
