package jp.pakupaku.ultimatesponge;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class UltimateSpongeService {
    private final UltimateSpongePlugin plugin;
    private int blocksPerTick;
    private boolean removeWaterlogged;
    private int dryingMultiplier;

    public UltimateSpongeService(UltimateSpongePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        blocksPerTick = Math.max(256, Math.min(32768,
                plugin.getConfig().getInt("absorption.blocks-per-tick", 8192)));
        removeWaterlogged = plugin.getConfig()
                .getBoolean("absorption.remove-waterlogged", true);
        dryingMultiplier = Math.max(1, Math.min(100,
                plugin.getConfig().getInt("drying.multiplier", 20)));
    }

    public ItemStack create(SpongeState state, int amount) {
        Material material = state == SpongeState.DRY ? Material.SPONGE : Material.WET_SPONGE;
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(plugin.itemStateKey(),
                PersistentDataType.STRING, state.name().toLowerCase(Locale.ROOT));

        if (state == SpongeState.DRY) {
            meta.displayName(plain(Component.text("最強スポンジ", NamedTextColor.AQUA)
                    .decorate(TextDecoration.BOLD)));
            meta.lore(List.of(
                    plain(Component.text("設置したチャンク全体の水を吸収", NamedTextColor.YELLOW)),
                    plain(Component.text("全高度・水没ブロック対応", NamedTextColor.GRAY))));
        } else {
            meta.displayName(plain(Component.text("濡れた最強スポンジ", NamedTextColor.BLUE)
                    .decorate(TextDecoration.BOLD)));
            meta.lore(List.of(
                    plain(Component.text("かまどで乾燥できます", NamedTextColor.YELLOW)),
                    plain(Component.text("乾燥時間: 通常の" + dryingMultiplier + "倍",
                            NamedTextColor.GRAY))));
        }
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public Optional<SpongeState> stateOf(ItemStack item) {
        if (item == null || (item.getType() != Material.SPONGE
                && item.getType() != Material.WET_SPONGE) || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(plugin.itemStateKey(), PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "dry" -> Optional.of(SpongeState.DRY);
            case "wet" -> Optional.of(SpongeState.WET);
            default -> Optional.empty();
        };
    }

    public boolean absorb(Block block) {
        Material material = block.getType();
        if (material == Material.WATER || material == Material.BUBBLE_COLUMN
                || material == Material.KELP || material == Material.KELP_PLANT
                || material == Material.SEAGRASS || material == Material.TALL_SEAGRASS) {
            block.setType(Material.AIR, false);
            return true;
        }

        if (removeWaterlogged) {
            BlockData data = block.getBlockData();
            if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
                waterlogged.setWaterlogged(false);
                block.setBlockData(waterlogged, false);
                return true;
            }
        }
        return false;
    }

    public int blocksPerTick() {
        return blocksPerTick;
    }

    public int dryingMultiplier() {
        return dryingMultiplier;
    }

    private static Component plain(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
