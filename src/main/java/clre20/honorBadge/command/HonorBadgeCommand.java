package clre20.honorBadge.command;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.gui.AdminListMenu;
import clre20.honorBadge.gui.DeleteMenu;
import clre20.honorBadge.gui.EditMenu;
import clre20.honorBadge.gui.PlayerMenu;
import clre20.honorBadge.player.PlayerData;
import clre20.honorBadge.service.TitleService;
import clre20.honorBadge.title.Title;
import clre20.honorBadge.title.TitleManager;
import clre20.honorBadge.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /honorbadge（別名 /hb）所有子指令與 Tab 補全。
 */
public final class HonorBadgeCommand implements CommandExecutor, TabCompleter {

    /** 子指令 → 權限（依 help 顯示順序）。 */
    private static final Map<String, String> SUBCOMMANDS = new LinkedHashMap<>();

    static {
        SUBCOMMANDS.put("help", "honorbadge.help");
        SUBCOMMANDS.put("version", "honorbadge.version");
        SUBCOMMANDS.put("list", "honorbadge.list");
        SUBCOMMANDS.put("create", "honorbadge.create");
        SUBCOMMANDS.put("edit", "honorbadge.edit");
        SUBCOMMANDS.put("del", "honorbadge.delete");
        SUBCOMMANDS.put("player", "honorbadge.player");
        SUBCOMMANDS.put("playerlist", "honorbadge.playerlist");
        SUBCOMMANDS.put("give", "honorbadge.give");
        SUBCOMMANDS.put("remove", "honorbadge.remove");
        SUBCOMMANDS.put("reload", "honorbadge.reload");
    }

    private final HonorBadge plugin;

    public HonorBadgeCommand(HonorBadge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
                             @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!check(sender, "honorbadge.use")) {
                    return true;
                }
                openPlayerMenu(player);
            } else {
                help(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("delete")) {
            sub = "del";
        }
        String permission = SUBCOMMANDS.get(sub);
        if (permission == null) {
            plugin.messages().send(sender, "general.unknown-subcommand");
            return true;
        }
        if (!check(sender, permission)) {
            return true;
        }

        switch (sub) {
            case "help" -> help(sender);
            case "version" -> version(sender);
            case "list" -> list(sender);
            case "create" -> create(sender, args);
            case "edit" -> edit(sender, args);
            case "del" -> delete(sender, args);
            case "player" -> player(sender, args);
            case "playerlist" -> playerList(sender, args);
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "reload" -> reload(sender);
            default -> plugin.messages().send(sender, "general.unknown-subcommand");
        }
        return true;
    }

    private boolean check(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        plugin.messages().send(sender, "general.no-permission");
        return false;
    }

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "general.player-only");
        return null;
    }

    private Title requireTitle(CommandSender sender, String id) {
        Title title = plugin.titles().get(id.toLowerCase(Locale.ROOT));
        if (title == null) {
            plugin.messages().send(sender, "general.title-not-found", Map.of("id", id));
        }
        return title;
    }

    // ==================== 子指令 ====================

    private void openPlayerMenu(Player player) {
        if (plugin.players().get(player.getUniqueId()) == null) {
            plugin.messages().send(player, "general.data-not-ready");
            return;
        }
        new PlayerMenu(plugin, player.getUniqueId(), 0).open(player);
        plugin.sounds().click(player);
    }

    private void help(CommandSender sender) {
        plugin.messages().send(sender, "help.header", Map.of("version", plugin.updates().currentVersion()));
        if (sender instanceof Player && sender.hasPermission("honorbadge.use")) {
            plugin.messages().send(sender, "help.commands.open");
        }
        for (Map.Entry<String, String> e : SUBCOMMANDS.entrySet()) {
            if (sender.hasPermission(e.getValue())) {
                String key = e.getKey().equals("del") ? "delete" : e.getKey();
                plugin.messages().send(sender, "help.commands." + key);
            }
        }
        plugin.messages().send(sender, "help.footer");
    }

    private void version(CommandSender sender) {
        UpdateChecker checker = plugin.updates();
        plugin.messages().send(sender, "version.current", Map.of("current", checker.currentVersion()));
        plugin.messages().send(sender, "version.checking");
        checker.check().thenAccept(result -> plugin.sync(() -> {
            Map<String, String> ph = Map.of(
                    "current", result.current(),
                    "latest", result.latest() == null ? "" : result.latest(),
                    "url", result.url() == null ? UpdateChecker.RELEASES_URL : result.url());
            String key = switch (result.status()) {
                case LATEST -> "version.latest";
                case OUTDATED -> "version.outdated";
                case DEV_NEWER -> "version.dev-newer";
                case NO_RELEASE -> "version.no-release";
                case FAILED -> "version.failed";
            };
            plugin.messages().send(sender, key, ph);
        }));
    }

    private void list(CommandSender sender) {
        if (sender instanceof Player player) {
            new AdminListMenu(plugin, player.getUniqueId(), 0).open(player);
            plugin.sounds().click(player);
            return;
        }
        List<Title> titles = plugin.titles().sorted();
        if (titles.isEmpty()) {
            plugin.messages().send(sender, "list.none");
            return;
        }
        plugin.messages().send(sender, "list.header", Map.of("count", String.valueOf(titles.size())));
        for (Title t : titles) {
            plugin.messages().send(sender, "list.entry", Map.of(
                    "id", t.id(),
                    "title", t.displayName(),
                    "holders", String.valueOf(plugin.players().holderCount(t.id())),
                    "sort", String.valueOf(t.sort())));
        }
    }

    private void create(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.create");
            return;
        }
        String id = args[1];
        if (!TitleManager.ID_PATTERN.matcher(id).matches()) {
            plugin.messages().send(sender, "general.invalid-id");
            return;
        }
        if (plugin.titles().exists(id)) {
            plugin.messages().send(sender, "general.title-exists", Map.of("id", id));
            return;
        }
        EditMenu.start(plugin, player, id, true);
    }

    private void edit(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.edit");
            return;
        }
        Title title = requireTitle(sender, args[1]);
        if (title != null) {
            EditMenu.start(plugin, player, title.id(), false);
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.delete");
            return;
        }
        Title title = requireTitle(sender, args[1]);
        if (title == null) {
            return;
        }
        if (sender instanceof Player player) {
            new DeleteMenu(plugin, player.getUniqueId(), title.id()).open(player);
            plugin.sounds().click(player);
            return;
        }
        // 主控台沒有介面，直接刪除
        if (plugin.edits().lockedBy(title.id()) != null) {
            plugin.messages().send(sender, "general.title-being-edited", Map.of("id", title.id(),
                    "editor", plugin.players().nameOf(plugin.edits().lockedBy(title.id()))));
            return;
        }
        plugin.titleService().delete(sender, title);
    }

    private void player(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.player");
            return;
        }
        Title title = requireTitle(sender, args[1]);
        if (title == null) {
            return;
        }
        if (!plugin.players().indexReady()) {
            plugin.messages().send(sender, "general.index-not-ready");
        }
        List<String> names = plugin.players().holderNames(title.id());
        if (names.isEmpty()) {
            plugin.messages().send(sender, "player.none", Map.of("title", title.displayName()));
            return;
        }
        plugin.messages().send(sender, "player.header",
                Map.of("title", title.displayName(), "count", String.valueOf(names.size())));
        String separator = plugin.messages().raw("player.separator");
        plugin.messages().send(sender, "player.list", Map.of("players", String.join(separator, names)));
    }

    private void playerList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "usage.playerlist");
            return;
        }
        TitleService.Target target = plugin.titleService().resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Map.of("player", args[1]));
            return;
        }
        plugin.players().peek(target.uuid()).thenAccept(data -> plugin.sync(() -> showPlayerList(sender, target, data)));
    }

    private void showPlayerList(CommandSender sender, TitleService.Target target, PlayerData data) {
        List<Title> owned = new ArrayList<>();
        if (data != null) {
            for (Title t : plugin.titles().sorted()) {
                if (data.owns(t.id())) {
                    owned.add(t);
                }
            }
        }
        if (owned.isEmpty()) {
            plugin.messages().send(sender, "playerlist.none", Map.of("player", target.name()));
            return;
        }
        plugin.messages().send(sender, "playerlist.header",
                Map.of("player", target.name(), "count", String.valueOf(owned.size())));
        String equipped = data.equipped();
        for (Title t : owned) {
            String key = t.id().equals(equipped) ? "playerlist.entry-equipped" : "playerlist.entry";
            plugin.messages().send(sender, key, Map.of("title", t.displayName(), "id", t.id()));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "usage.give");
            return;
        }
        Title title = requireTitle(sender, args[1]);
        if (title == null) {
            return;
        }
        TitleService.Target target = plugin.titleService().resolve(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Map.of("player", args[2]));
            return;
        }
        plugin.titleService().give(sender, title, target);
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.messages().send(sender, "usage.remove");
            return;
        }
        Title title = requireTitle(sender, args[1]);
        if (title == null) {
            return;
        }
        TitleService.Target target = plugin.titleService().resolve(args[2]);
        if (target == null) {
            plugin.messages().send(sender, "general.player-not-found", Map.of("player", args[2]));
            return;
        }
        plugin.titleService().remove(sender, title, target);
    }

    private void reload(CommandSender sender) {
        plugin.reload();
        plugin.messages().send(sender, "reload.done", Map.of("count", String.valueOf(plugin.titles().size())));
    }

    // ==================== Tab 補全 ====================

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            for (Map.Entry<String, String> e : SUBCOMMANDS.entrySet()) {
                if (sender.hasPermission(e.getValue())) {
                    subs.add(e.getKey());
                }
            }
            return filter(subs, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("delete")) {
            sub = "del";
        }
        String permission = SUBCOMMANDS.get(sub);
        if (permission == null || !sender.hasPermission(permission)) {
            return List.of();
        }
        if (args.length == 2) {
            return switch (sub) {
                case "edit", "del", "player", "give", "remove" -> filter(plugin.titles().ids(), args[1]);
                case "playerlist" -> filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "give" -> filter(onlineNames(), args[2]);
                case "remove" -> filter(plugin.players().holderNames(args[1].toLowerCase(Locale.ROOT)), args[2]);
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
