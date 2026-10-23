package clre20.honorBadge.listener;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.player.PlayerData;
import clre20.honorBadge.title.Title;
import clre20.honorBadge.util.ColorUtil;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * 聊天事件（非主執行緒）：
 * 1. 攔截設定稱號時的聊天輸入（最早處理並取消廣播）
 * 2. 原生聊天稱號（預設關閉，有 LPC 等聊天插件時不要開）
 */
public final class ChatListener implements Listener {

    private final HonorBadge plugin;

    public ChatListener(HonorBadge plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInput(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!plugin.chatInput().isWaiting(uuid)) {
            return;
        }
        String raw = event.signedMessage().message();
        if (plugin.chatInput().handle(uuid, raw)) {
            event.setCancelled(true);
            try {
                event.viewers().clear();
            } catch (UnsupportedOperationException ignored) {
                // 已取消，廣播不會發生
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.settings().nativeChat()) {
            return;
        }
        PlayerData data = plugin.players().get(event.getPlayer().getUniqueId());
        Title title = data == null || data.equipped() == null ? null : plugin.titles().get(data.equipped());
        Component titleComponent = title == null ? Component.empty() : title.formattedComponent();
        String format = plugin.settings().nativeChatFormat()
                .replace("{title}", "<hb_title>")
                .replace("{player}", "<hb_player>")
                .replace("{message}", "<hb_message>");
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> ColorUtil.parse(format,
                Placeholder.component("hb_title", titleComponent),
                Placeholder.component("hb_player", displayName),
                Placeholder.component("hb_message", message))));
    }
}
