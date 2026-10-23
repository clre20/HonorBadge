package clre20.honorBadge.listener;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.update.UpdateChecker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 玩家進出伺服器：載入／釋放資料、清除草稿與等待中的輸入、登入更新提醒。
 */
public final class PlayerListener implements Listener {

    private static final long NOTIFY_DELAY_TICKS = 60L; // 登入 3 秒後

    private final HonorBadge plugin;

    public PlayerListener(HonorBadge plugin) {
        this.plugin = plugin;
    }

    /** 在登入前（非主執行緒）先載入資料，進服時就能立即使用。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        plugin.players().loadForLogin(event.getUniqueId(), event.getName());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.players().handleJoin(player);
        plugin.nativeDisplay().refresh(player.getUniqueId());

        if (plugin.settings().notifyOnJoin() && player.hasPermission("honorbadge.version")) {
            UUID uuid = player.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (Bukkit.getPlayer(uuid) == null) {
                    return;
                }
                plugin.updates().check().thenAccept(result -> plugin.sync(() -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && result.status() == UpdateChecker.Status.OUTDATED) {
                        plugin.messages().send(p, "version.join-notify", Map.of(
                                "current", result.current(),
                                "latest", result.latest(),
                                "url", result.url()));
                    }
                }));
            }, NOTIFY_DELAY_TICKS);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        plugin.chatInput().cancel(uuid);
        plugin.edits().end(uuid);
        plugin.nativeDisplay().forget(player);
        plugin.players().handleQuit(player);
    }
}
