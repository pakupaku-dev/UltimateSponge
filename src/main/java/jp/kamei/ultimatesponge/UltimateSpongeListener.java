package jp.kamei.ultimatesponge;

import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.inventory.ItemStack;

public final class UltimateSpongeListener implements Listener {
    private final UltimateSpongePlugin plugin;
    private final UltimateSpongeService spongeService;
    private final UltimateSpongeRegistry spongeRegistry;
    private final AbsorptionManager absorptionManager;

    public UltimateSpongeListener(UltimateSpongePlugin plugin,
                                  UltimateSpongeService spongeService,
                                  UltimateSpongeRegistry spongeRegistry,
                                  AbsorptionManager absorptionManager) {
        this.plugin = plugin;
        this.spongeService = spongeService;
        this.spongeRegistry = spongeRegistry;
        this.absorptionManager = absorptionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<SpongeState> stateResult = spongeService.stateOf(event.getItemInHand());
        if (stateResult.isEmpty()) {
            return;
        }
        if (!event.getPlayer().hasPermission("ultimatesponge.use")) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Component.text(
                    "最強スポンジを使う権限がありません", NamedTextColor.RED));
            return;
        }

        SpongeState state = stateResult.get();
        Block block = event.getBlockPlaced();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.SPONGE && block.getType() != Material.WET_SPONGE) {
                return;
            }
            spongeRegistry.add(block);
            if (state == SpongeState.DRY) {
                // 通常のスポンジ処理ですでに濡れていても、その状態を維持して全体を走査します。
                absorptionManager.start(block, event.getPlayer());
            } else {
                // ネザーへ置いた場合も専用の濡れたスポンジは乾燥させません。
                block.setType(Material.WET_SPONGE, false);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!spongeRegistry.contains(block)) {
            return;
        }

        SpongeState state = block.getType() == Material.WET_SPONGE
                ? SpongeState.WET : SpongeState.DRY;
        boolean shouldDrop = event.isDropItems()
                && event.getPlayer().getGameMode() != GameMode.CREATIVE;
        event.setDropItems(false);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!spongeRegistry.contains(block)
                    || block.getType() == Material.SPONGE
                    || block.getType() == Material.WET_SPONGE) {
                return; // キャンセルされた場合はブロックと登録を維持します。
            }
            spongeRegistry.remove(block);
            if (shouldDrop) {
                block.getWorld().dropItemNaturally(block.getLocation(),
                        spongeService.create(state, 1));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceStart(FurnaceStartSmeltEvent event) {
        if (spongeService.stateOf(event.getSource()).orElse(null) != SpongeState.WET) {
            return;
        }
        if (event.getRecipe().getKey().equals(plugin.dryingRecipeKey())) {
            return; // 専用レシピにはすでに設定倍率が反映されています。
        }
        long cookTime = (long) event.getTotalCookTime() * spongeService.dryingMultiplier();
        event.setTotalCookTime((int) Math.min(Integer.MAX_VALUE, cookTime));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceFinish(FurnaceSmeltEvent event) {
        if (spongeService.stateOf(event.getSource()).orElse(null) == SpongeState.WET) {
            event.setResult(spongeService.create(SpongeState.DRY, event.getResult().getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(spongeRegistry::contains)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(spongeRegistry::contains)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(spongeRegistry::contains);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(spongeRegistry::contains);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (spongeRegistry.contains(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
