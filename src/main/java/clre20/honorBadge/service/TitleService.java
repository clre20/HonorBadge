package clre20.honorBadge.service;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * 給予、移除、刪除稱號的共用流程（指令與介面共用），結果訊息都回到主執行緒送出。
 */
public final class TitleService {

    /** 指令中指定的玩家。 */
    public record Target(UUID uuid, String name) {
    }

    private final HonorBadge plugin;

    public TitleService(HonorBadge plugin) {
        this.plugin = plugin;
    }

    /** 依名稱找玩家：線上 → HonorBadge 的資料 → 伺服器快取（曾進過伺服器）。找不到回傳 null。 */
    public Target resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        UUID known = plugin.players().findByName(name);
        if (known != null) {
            return new Target(known, plugin.players().nameOf(known));
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && (cached.hasPlayedBefore() || cached.isOnline())) {
            return new Target(cached.getUniqueId(), cached.getName() == null ? name : cached.getName());
        }
        return null;
    }

    public void give(CommandSender sender, Title title, Target target) {
        plugin.players().give(target.uuid(), target.name(), title.id()).whenComplete((added, error) -> plugin.sync(() -> {
            if (error != null) {
                fail(sender, error);
                return;
            }
            Map<String, String> ph = Map.of("player", target.name(), "title", title.displayName(), "id", title.id());
            if (Boolean.TRUE.equals(added)) {
                plugin.messages().send(sender, "give.success", ph);
                Player p = Bukkit.getPlayer(target.uuid());
                if (p != null) {
                    plugin.messages().send(p, "give.notify", ph);
                    plugin.sounds().success(p);
                }
            } else {
                plugin.messages().send(sender, "give.already-owned", ph);
            }
        }));
    }

    public void remove(CommandSender sender, Title title, Target target) {
        plugin.players().remove(target.uuid(), target.name(), title.id()).whenComplete((removed, error) -> plugin.sync(() -> {
            if (error != null) {
                fail(sender, error);
                return;
            }
            Map<String, String> ph = Map.of("player", target.name(), "title", title.displayName(), "id", title.id());
            if (Boolean.TRUE.equals(removed)) {
                plugin.messages().send(sender, "remove.success", ph);
                Player p = Bukkit.getPlayer(target.uuid());
                if (p != null) {
                    plugin.messages().send(p, "remove.notify", ph);
                    plugin.nativeDisplay().refresh(p.getUniqueId());
                }
            } else {
                plugin.messages().send(sender, "remove.not-owned", ph);
            }
        }));
    }

    public void delete(CommandSender sender, Title title) {
        String id = title.id();
        var future = plugin.players().removeTitleFromAll(id);
        plugin.titles().delete(id);
        future.whenComplete((count, error) -> plugin.sync(() -> {
            if (error != null) {
                fail(sender, error);
                return;
            }
            plugin.nativeDisplay().refreshAll();
            plugin.messages().send(sender, "delete.success",
                    Map.of("title", title.displayName(), "id", id, "count", String.valueOf(count)));
            if (sender instanceof Player p) {
                plugin.sounds().success(p);
            }
        }));
    }

    private void fail(CommandSender sender, Throwable error) {
        plugin.getLogger().log(Level.WARNING, "HonorBadge 操作失敗", error);
        plugin.messages().send(sender, "general.failed");
    }
}
