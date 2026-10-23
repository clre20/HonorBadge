package clre20.honorBadge.config;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * messages.yml：所有顯示文字都從這裡取得，缺少時使用 jar 內的預設文字。
 */
public final class MessageManager {

    private final HonorBadge plugin;
    private YamlConfiguration yaml;
    private YamlConfiguration defaults;

    public MessageManager(HonorBadge plugin) {
        this.plugin = plugin;
    }

    public void load() {
        defaults = YamlUpdater.loadDefaults(plugin, "messages.yml");
        yaml = YamlUpdater.loadAndUpdate(plugin, "messages.yml", "messages-version");
    }

    // ---------- 原始文字 ----------

    public String raw(String key) {
        String value = yaml == null ? null : yaml.getString(key);
        if (value == null && defaults != null) {
            value = defaults.getString(key);
        }
        return value == null ? key : value;
    }

    public List<String> rawList(String key) {
        List<String> list = yaml == null ? null : (yaml.isList(key) ? yaml.getStringList(key) : null);
        if (list == null && defaults != null && defaults.isList(key)) {
            list = defaults.getStringList(key);
        }
        return list == null ? Collections.emptyList() : list;
    }

    public String apply(String text, Map<String, String> placeholders) {
        String out = text;
        if (out.contains("{prefix}")) {
            out = out.replace("{prefix}", plugin.settings() == null ? "" : plugin.settings().prefix());
        }
        if (placeholders != null && !placeholders.isEmpty() && out.indexOf('{') >= 0) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    // ---------- 聊天訊息 ----------

    public Component get(String key, Map<String, String> placeholders) {
        return ColorUtil.parse(apply(raw(key), placeholders));
    }

    public Component get(String key) {
        return get(key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String text = raw(key);
        if (text.isEmpty()) {
            return;
        }
        sender.sendMessage(ColorUtil.parse(apply(text, placeholders)));
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    /** 主控台日誌用的純文字。 */
    public String console(String key, Map<String, String> placeholders) {
        return ColorUtil.plain(apply(raw(key), placeholders));
    }

    // ---------- 介面物品 ----------

    public Component itemName(String key, Map<String, String> placeholders) {
        return ColorUtil.item(apply(raw(key), placeholders));
    }

    public List<Component> itemLore(String key, Map<String, String> placeholders) {
        return itemLore(key, placeholders, null);
    }

    /**
     * @param loreExpansion 單獨一行的 {lore} 會展開成這些行；null 或空則該行移除
     */
    public List<Component> itemLore(String key, Map<String, String> placeholders, List<String> loreExpansion) {
        return toLore(rawList(key), placeholders, loreExpansion);
    }

    public List<Component> toLore(List<String> lines, Map<String, String> placeholders, List<String> loreExpansion) {
        List<Component> out = new ArrayList<>(lines.size() + 4);
        for (String line : lines) {
            if (line.trim().equals("{lore}")) {
                if (loreExpansion != null) {
                    for (String l : loreExpansion) {
                        out.add(ColorUtil.item(l));
                    }
                }
                continue;
            }
            out.add(ColorUtil.item(apply(line, placeholders)));
        }
        return out;
    }
}
