package jp.kamei.ultimatesponge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class AbsorptionManager {
    private final UltimateSpongePlugin plugin;
    private final UltimateSpongeService spongeService;
    private final UltimateSpongeRegistry spongeRegistry;
    private final Map<ChunkKey, RunningAbsorption> running = new HashMap<>();

    public AbsorptionManager(UltimateSpongePlugin plugin, UltimateSpongeService spongeService,
                             UltimateSpongeRegistry spongeRegistry) {
        this.plugin = plugin;
        this.spongeService = spongeService;
        this.spongeRegistry = spongeRegistry;
    }

    public void start(Block spongeBlock, Player owner) {
        Chunk chunk = spongeBlock.getChunk();
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (running.containsKey(key)) {
            owner.sendActionBar(Component.text("このチャンクはすでに吸収中です", NamedTextColor.YELLOW));
            return;
        }

        chunk.addPluginChunkTicket(plugin);
        RunningAbsorption absorption = new RunningAbsorption(key, chunk, spongeBlock, owner.getUniqueId());
        BukkitTask task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, absorption::runBatch, 1L, 1L);
        absorption.task = task;
        running.put(key, absorption);
    }

    public void shutdown() {
        for (RunningAbsorption absorption : running.values()) {
            if (absorption.task != null) {
                absorption.task.cancel();
            }
            absorption.chunk.removePluginChunkTicket(plugin);
        }
        running.clear();
    }

    private final class RunningAbsorption {
        private final ChunkKey key;
        private final Chunk chunk;
        private final Block spongeBlock;
        private final UUID ownerId;
        private final int minHeight;
        private final int totalBlocks;
        private int cursor;
        private int absorbed;
        private BukkitTask task;

        private RunningAbsorption(ChunkKey key, Chunk chunk, Block spongeBlock, UUID ownerId) {
            this.key = key;
            this.chunk = chunk;
            this.spongeBlock = spongeBlock;
            this.ownerId = ownerId;
            minHeight = chunk.getWorld().getMinHeight();
            totalBlocks = 16 * 16 * (chunk.getWorld().getMaxHeight() - minHeight);
        }

        private void runBatch() {
            if (!spongeRegistry.contains(spongeBlock)
                    || (spongeBlock.getType() != Material.SPONGE
                    && spongeBlock.getType() != Material.WET_SPONGE)) {
                finish(false);
                return;
            }

            int limit = Math.min(totalBlocks, cursor + spongeService.blocksPerTick());
            while (cursor < limit) {
                int yOffset = cursor >> 8;
                int horizontal = cursor & 255;
                int localX = horizontal >> 4;
                int localZ = horizontal & 15;
                if (spongeService.absorb(chunk.getBlock(localX, minHeight + yOffset, localZ))) {
                    absorbed++;
                }
                cursor++;
            }
            if (cursor >= totalBlocks) {
                finish(true);
            }
        }

        private void finish(boolean completed) {
            if (task != null) {
                task.cancel();
            }
            running.remove(key);
            chunk.removePluginChunkTicket(plugin);
            if (!completed || !spongeRegistry.contains(spongeBlock)) {
                return;
            }

            if (absorbed > 0 && spongeBlock.getType() == Material.SPONGE) {
                spongeBlock.setType(Material.WET_SPONGE, false);
            }
            spongeBlock.getWorld().playSound(spongeBlock.getLocation(),
                    Sound.BLOCK_SPONGE_ABSORB, 1.0f, 0.8f);
            spongeBlock.getWorld().spawnParticle(Particle.SPLASH,
                    spongeBlock.getLocation().add(0.5, 0.8, 0.5),
                    30, 0.45, 0.45, 0.45, 0.05);

            Player owner = plugin.getServer().getPlayer(ownerId);
            if (owner != null) {
                owner.sendActionBar(Component.text(
                        absorbed > 0 ? absorbed + "ブロック分の水を吸収しました"
                                : "このチャンクに水は見つかりませんでした",
                        absorbed > 0 ? NamedTextColor.AQUA : NamedTextColor.YELLOW));
            }
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {
    }
}
