package jp.kamei.ultimatesponge;

import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.java.JavaPlugin;

public final class UltimateSpongePlugin extends JavaPlugin {
    private NamespacedKey itemStateKey;
    private NamespacedKey chunkPositionsKey;
    private NamespacedKey dryingRecipeKey;
    private UltimateSpongeService spongeService;
    private UltimateSpongeRegistry spongeRegistry;
    private AbsorptionManager absorptionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        itemStateKey = new NamespacedKey(this, "sponge_state");
        chunkPositionsKey = new NamespacedKey(this, "placed_sponge_positions");
        dryingRecipeKey = new NamespacedKey(this, "dry_ultimate_sponge");

        spongeService = new UltimateSpongeService(this);
        spongeService.reload();
        refreshDryingRecipe();
        spongeRegistry = new UltimateSpongeRegistry(this);
        absorptionManager = new AbsorptionManager(this, spongeService, spongeRegistry);

        UltimateSpongeListener listener = new UltimateSpongeListener(
                this, spongeService, spongeRegistry, absorptionManager);
        getServer().getPluginManager().registerEvents(listener, this);

        UltimateSpongeCommand commandHandler = new UltimateSpongeCommand(this, spongeService);
        PluginCommand command = getCommand("ultimatesponge");
        if (command == null) {
            throw new IllegalStateException("plugin.ymlにultimatespongeコマンドがありません");
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getLogger().info("UltimateSponge 1.0.0 を有効化しました");
    }

    @Override
    public void onDisable() {
        if (absorptionManager != null) {
            absorptionManager.shutdown();
        }
        if (dryingRecipeKey != null) {
            getServer().removeRecipe(dryingRecipeKey);
        }
    }

    public NamespacedKey itemStateKey() {
        return itemStateKey;
    }

    public NamespacedKey chunkPositionsKey() {
        return chunkPositionsKey;
    }

    public NamespacedKey dryingRecipeKey() {
        return dryingRecipeKey;
    }

    public void refreshDryingRecipe() {
        getServer().removeRecipe(dryingRecipeKey);
        int vanillaCookTime = findVanillaCookTime();
        long configuredCookTime = (long) vanillaCookTime * spongeService.dryingMultiplier();
        int cookTime = (int) Math.min(Integer.MAX_VALUE, configuredCookTime);

        FurnaceRecipe recipe = new FurnaceRecipe(dryingRecipeKey,
                spongeService.create(SpongeState.DRY, 1),
                new RecipeChoice.ExactChoice(spongeService.create(SpongeState.WET, 1)),
                0.0f, cookTime);
        recipe.setGroup("ultimate_sponge");
        if (!getServer().addRecipe(recipe)) {
            getLogger().warning("最強スポンジの乾燥レシピを登録できませんでした");
        }
    }

    private int findVanillaCookTime() {
        for (Recipe recipe : getServer().getRecipesFor(new ItemStack(Material.WET_SPONGE))) {
            if (recipe instanceof CookingRecipe<?> cookingRecipe
                    && cookingRecipe.getResult().getType() == Material.SPONGE) {
                return cookingRecipe.getCookingTime();
            }
        }
        return 200;
    }
}
