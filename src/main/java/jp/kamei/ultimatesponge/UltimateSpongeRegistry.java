package jp.kamei.ultimatesponge;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class UltimateSpongeRegistry {
    private final UltimateSpongePlugin plugin;

    public UltimateSpongeRegistry(UltimateSpongePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean contains(Block block) {
        int[] positions = positions(block.getChunk());
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        for (int index = 0; index + 2 < positions.length; index += 3) {
            if (positions[index] == localX && positions[index + 1] == block.getY()
                    && positions[index + 2] == localZ) {
                return true;
            }
        }
        return false;
    }

    public void add(Block block) {
        if (contains(block)) {
            return;
        }
        Chunk chunk = block.getChunk();
        int[] current = positions(chunk);
        int[] updated = new int[current.length + 3];
        System.arraycopy(current, 0, updated, 0, current.length);
        updated[current.length] = block.getX() & 15;
        updated[current.length + 1] = block.getY();
        updated[current.length + 2] = block.getZ() & 15;
        chunk.getPersistentDataContainer().set(plugin.chunkPositionsKey(),
                PersistentDataType.INTEGER_ARRAY, updated);
    }

    public void remove(Block block) {
        Chunk chunk = block.getChunk();
        int[] current = positions(chunk);
        int localX = block.getX() & 15;
        int localZ = block.getZ() & 15;
        List<Integer> remaining = new ArrayList<>(current.length);
        for (int index = 0; index + 2 < current.length; index += 3) {
            if (current[index] == localX && current[index + 1] == block.getY()
                    && current[index + 2] == localZ) {
                continue;
            }
            remaining.add(current[index]);
            remaining.add(current[index + 1]);
            remaining.add(current[index + 2]);
        }

        PersistentDataContainer container = chunk.getPersistentDataContainer();
        if (remaining.isEmpty()) {
            container.remove(plugin.chunkPositionsKey());
            return;
        }
        int[] updated = remaining.stream().mapToInt(Integer::intValue).toArray();
        container.set(plugin.chunkPositionsKey(), PersistentDataType.INTEGER_ARRAY, updated);
    }

    private int[] positions(Chunk chunk) {
        int[] positions = chunk.getPersistentDataContainer().get(plugin.chunkPositionsKey(),
                PersistentDataType.INTEGER_ARRAY);
        return positions == null ? new int[0] : positions;
    }
}
