package clre20.honorBadge.compatibility;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.player.PlayerData;
import clre20.honorBadge.title.Title;
import clre20.honorBadge.util.ColorUtil;
import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI 變數。可能被 TAB 等插件在非主執行緒呼叫，所以只讀取執行緒安全的快取。
 * <ul>
 *   <li>%honorbadge_title%、%honorbadge_title_raw%、%honorbadge_id%、%honorbadge_count%</li>
 *   <li>%honorbadge_display%、%honorbadge_display_&lt;名稱&gt;%</li>
 * </ul>
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private static final int FALLBACK_CACHE_LIMIT = 512;
    private static final java.util.regex.Pattern UNRESOLVED = java.util.regex.Pattern.compile("%[A-Za-z0-9]+_[A-Za-z0-9_:.\\-]+%");

    private final HonorBadge plugin;
    /** 備用文字解析結果快取（解析後字串 → § 格式），上限 512 筆，超過就清空，避免無限增長。 */
    private final Map<String, String> fallbackCache = new ConcurrentHashMap<>();

    public PlaceholderHook(HonorBadge plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "honorbadge";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Clre20";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    public void clearCache() {
        fallbackCache.clear();
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase();
        Title title = null;
        PlayerData data = null;
        if (player != null) {
            data = plugin.players().get(player.getUniqueId());
            if (data != null && data.equipped() != null) {
                title = plugin.titles().get(data.equipped());
            }
        }

        switch (key) {
            case "title":
                return title == null ? "" : title.legacyFormatted();
            case "title_raw":
                return title == null ? "" : title.legacyRaw();
            case "id":
                return title == null ? "" : title.id();
            case "id_color":
                return plugin.settings().idColorLegacy();
            case "count":
                return String.valueOf(data == null ? 0 : data.ownedCount());
            default:
                break;
        }

        if (key.equals("display") || key.startsWith("display_")) {
            if (title != null) {
                return title.legacyFormatted();
            }
            String fallbackKey = key.equals("display") ? "default" : key.substring("display_".length());
            return fallback(player, plugin.settings().fallback(fallbackKey));
        }
        return null;
    }

    private String fallback(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String resolved = text;
        // 避免備用文字又呼叫自己造成無限迴圈
        if (resolved.indexOf('%') >= 0 && !resolved.toLowerCase().contains("%honorbadge_")) {
            resolved = player instanceof Player online
                    ? PlaceholderAPI.setPlaceholders(online, resolved)
                    : PlaceholderAPI.setPlaceholders(player, resolved);
            // 沒裝對應擴充時變數會原樣留下，這裡把它拿掉，避免顯示出 %luckperms_prefix% 這種字
            resolved = UNRESOLVED.matcher(resolved).replaceAll("");
        }
        String cached = fallbackCache.get(resolved);
        if (cached != null) {
            return cached;
        }
        String legacy = ColorUtil.toLegacy(ColorUtil.parse(resolved));
        if (fallbackCache.size() >= FALLBACK_CACHE_LIMIT) {
            fallbackCache.clear();
        }
        fallbackCache.put(resolved, legacy);
        return legacy;
    }
}
