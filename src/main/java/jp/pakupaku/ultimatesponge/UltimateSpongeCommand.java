package jp.pakupaku.ultimatesponge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class UltimateSpongeCommand implements CommandExecutor, TabCompleter {
    private final UltimateSpongePlugin plugin;
    private final UltimateSpongeService spongeService;

    public UltimateSpongeCommand(UltimateSpongePlugin plugin,
                                 UltimateSpongeService spongeService) {
        this.plugin = plugin;
        this.spongeService = spongeService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, label, args);
            case "reload" -> reload(sender);
            default -> {
                sendHelp(sender, label);
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("ultimatesponge.give")) {
            error(sender, "このコマンドを使う権限がありません");
            return true;
        }
        if (args.length < 2) {
            sendGiveUsage(sender, label);
            return true;
        }

        Player target;
        int stateIndex;
        SpongeState firstState = parseState(args[1]);
        if (sender instanceof Player player && firstState != null) {
            target = player;
            stateIndex = 1;
        } else {
            if (args.length < 3) {
                sendGiveUsage(sender, label);
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                error(sender, "プレイヤー「" + args[1] + "」はオンラインではありません");
                return true;
            }
            stateIndex = 2;
        }

        SpongeState state = parseState(args[stateIndex]);
        if (state == null) {
            error(sender, "種類はdryまたはwetで指定してください");
            return true;
        }
        int amount = 1;
        if (args.length > stateIndex + 1) {
            Integer parsed = parseInteger(args[stateIndex + 1]);
            if (parsed == null || parsed < 1 || parsed > 64) {
                error(sender, "個数は1～64で指定してください");
                return true;
            }
            amount = parsed;
        }

        ItemStack sponge = spongeService.create(state, amount);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(sponge);
        leftovers.values().forEach(item ->
                target.getWorld().dropItemNaturally(target.getLocation(), item));
        String name = state == SpongeState.DRY ? "最強スポンジ" : "濡れた最強スポンジ";
        sender.sendMessage(Component.text(target.getName() + " に " + name + " を "
                + amount + " 個渡しました", NamedTextColor.GREEN));
        if (!sender.equals(target)) {
            target.sendMessage(Component.text(name + " を受け取りました", NamedTextColor.AQUA));
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("ultimatesponge.reload")) {
            error(sender, "このコマンドを使う権限がありません");
            return true;
        }
        plugin.reloadConfig();
        spongeService.reload();
        plugin.refreshDryingRecipe();
        sender.sendMessage(Component.text("UltimateSpongeの設定を再読み込みしました",
                NamedTextColor.GREEN));
        return true;
    }

    private static void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(Component.text("UltimateSponge コマンド", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/" + label + " give <dry|wet> [個数]",
                NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label
                + " give <プレイヤー> <dry|wet> [個数]", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.YELLOW));
    }

    private static void sendGiveUsage(CommandSender sender, String label) {
        error(sender, "使い方: /" + label + " give [プレイヤー] <dry|wet> [個数]");
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    private static @Nullable SpongeState parseState(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "dry", "乾燥" -> SpongeState.DRY;
            case "wet", "濡れ" -> SpongeState.WET;
            default -> null;
        };
    }

    private static @Nullable Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                 @NotNull Command command,
                                                 @NotNull String alias,
                                                 @NotNull String[] args) {
        List<String> candidates = new ArrayList<>();
        if (args.length == 1) {
            if (sender.hasPermission("ultimatesponge.give")) {
                candidates.add("give");
            }
            if (sender.hasPermission("ultimatesponge.reload")) {
                candidates.add("reload");
            }
        } else if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                if (sender instanceof Player) {
                    candidates.addAll(List.of("dry", "wet"));
                }
                Bukkit.getOnlinePlayers().forEach(player -> candidates.add(player.getName()));
            } else {
                boolean selfSyntax = sender instanceof Player && parseState(args[1]) != null;
                int stateIndex = selfSyntax ? 1 : 2;
                if (args.length - 1 == stateIndex) {
                    candidates.addAll(List.of("dry", "wet"));
                } else if (args.length - 1 == stateIndex + 1) {
                    candidates.addAll(List.of("1", "2", "4", "8", "16", "32", "64"));
                }
            }
        }

        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        candidates.removeIf(candidate -> !candidate.toLowerCase(Locale.ROOT).startsWith(current));
        Collections.sort(candidates);
        return candidates;
    }
}
