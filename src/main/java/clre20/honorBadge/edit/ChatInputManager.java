package clre20.honorBadge.edit;

import clre20.honorBadge.HonorBadge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 聊天欄輸入：攔截玩家下一則聊天訊息當作輸入內容，逾時自動取消。
 */
public final class ChatInputManager {

    private final HonorBadge plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public ChatInputManager(HonorBadge plugin) {
        this.plugin = plugin;
    }

    private static final class Pending {
        final Consumer<String> onInput;
        final Runnable onCancel;
        final Runnable onTimeout;
        volatile BukkitTask timeoutTask;

        Pending(Consumer<String> onInput, Runnable onCancel, Runnable onTimeout) {
            this.onInput = onInput;
            this.onCancel = onCancel;
            this.onTimeout = onTimeout;
        }
    }

    /**
     * @param onInput   收到輸入（主執行緒）
     * @param onCancel  輸入了取消字（主執行緒）
     * @param onTimeout 逾時（主執行緒）
     */
    public void request(Player player, Consumer<String> onInput, Runnable onCancel, Runnable onTimeout) {
        UUID uuid = player.getUniqueId();
        cancel(uuid);
        Pending p = new Pending(onInput, onCancel, onTimeout);
        pending.put(uuid, p);
        p.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(uuid, p)) {
                p.onTimeout.run();
            }
        }, plugin.settings().inputTimeout() * 20L);
    }

    public boolean isWaiting(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /** 由聊天事件呼叫（非主執行緒）。回傳 true 表示這則訊息是輸入內容，應取消廣播。 */
    public boolean handle(UUID uuid, String raw) {
        Pending p = pending.remove(uuid);
        if (p == null) {
            return false;
        }
        BukkitTask task = p.timeoutTask;
        if (task != null) {
            task.cancel();
        }
        String text = raw == null ? "" : raw.trim();
        plugin.sync(() -> {
            if (text.equalsIgnoreCase(plugin.settings().cancelWord())) {
                p.onCancel.run();
            } else {
                p.onInput.accept(text);
            }
        });
        return true;
    }

    /** 放棄等待，不呼叫任何回呼（玩家離線、重載時）。 */
    public void cancel(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p != null && p.timeoutTask != null) {
            p.timeoutTask.cancel();
        }
    }

    public void cancelAll() {
        for (UUID uuid : pending.keySet()) {
            cancel(uuid);
        }
    }
}
