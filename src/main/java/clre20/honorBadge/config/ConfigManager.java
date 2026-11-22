package clre20.honorBadge.config;

import clre20.honorBadge.HonorBadge;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * config.yml 的讀取結果。每次 reload 重新建立，所有欄位都有預設值防呆。
 */
public final class ConfigManager {

    private final HonorBadge plugin;
    private YamlConfiguration yaml;

    private String prefix;
    private boolean checkOnStartup;
    private boolean notifyOnJoin;
    private String titleFormat;
    private String idColor;
    private String idColorLegacy;
    private Map<String, String> fallbacks;
    private boolean nativeChat;
    private String nativeChatFormat;
    private boolean nativeTab;
    private boolean nativeNametag;
    private int inputTimeout;
    private String cancelWord;
    private String clearWord;
    private String loreSeparator;
    private int maxLength;
    private int autosaveInterval;
    private boolean soundsEnabled;
    private String soundClick;
    private String soundSuccess;
    private String soundFail;

    public ConfigManager(HonorBadge plugin) {
        this.plugin = plugin;
    }

    public void load() {
        yaml = YamlUpdater.loadAndUpdate(plugin, "config.yml", "config-version");

        prefix = yaml.getString("prefix", "&8[&6HonorBadge&8] &r");
        checkOnStartup = yaml.getBoolean("update-checker.check-on-startup", true);
        notifyOnJoin = yaml.getBoolean("update-checker.notify-on-join", true);
        titleFormat = yaml.getString("title-format", "&8[{title}&8] ");
        idColor = yaml.getString("id-color", "&b");
        if (idColor == null || idColor.isEmpty()) idColor = "&b";
        idColorLegacy = clre20.honorBadge.util.ColorUtil.toLegacyColor(idColor);

        Map<String, String> fb = new HashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("display-fallback");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                fb.put(key.toLowerCase(), section.getString(key, ""));
            }
        }
        fb.putIfAbsent("default", "");
        fallbacks = Collections.unmodifiableMap(fb);

        nativeChat = yaml.getBoolean("native-display.chat", false);
        nativeChatFormat = yaml.getString("native-display.chat-format", "{title}&f{player} &8» &f{message}");
        nativeTab = yaml.getBoolean("native-display.tab", false);
        nativeNametag = yaml.getBoolean("native-display.nametag", false);

        inputTimeout = Math.max(5, yaml.getInt("chat-input.timeout", 60));
        cancelWord = yaml.getString("chat-input.cancel-word", "取消");
        clearWord = yaml.getString("chat-input.clear-word", "清除");
        loreSeparator = yaml.getString("chat-input.lore-separator", "|");
        if (loreSeparator == null || loreSeparator.isEmpty()) {
            loreSeparator = "|";
        }
        maxLength = Math.max(1, yaml.getInt("title-limits.max-length", 32));
        autosaveInterval = Math.max(30, yaml.getInt("autosave-interval", 300));

        soundsEnabled = yaml.getBoolean("sounds.enabled", true);
        soundClick = yaml.getString("sounds.click", "ui.button.click");
        soundSuccess = yaml.getString("sounds.success", "entity.player.levelup");
        soundFail = yaml.getString("sounds.fail", "entity.villager.no");
    }

    /** 介面按鈕物品，找不到時用備用物品。 */
    public Material guiItem(String key, Material fallback) {
        String name = yaml.getString("gui-items." + key);
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        return material == null || material.isAir() || !material.isItem() ? fallback : material;
    }

    public String fallback(String key) {
        String value = fallbacks.get(key == null ? "default" : key.toLowerCase());
        return value != null ? value : fallbacks.get("default");
    }

    public String prefix() { return prefix; }
    public boolean checkOnStartup() { return checkOnStartup; }
    public boolean notifyOnJoin() { return notifyOnJoin; }
    public String titleFormat() { return titleFormat; }
    public String idColor() { return idColor; }
    public String idColorLegacy() { return idColorLegacy; }
    public boolean nativeChat() { return nativeChat; }
    public String nativeChatFormat() { return nativeChatFormat; }
    public boolean nativeTab() { return nativeTab; }
    public boolean nativeNametag() { return nativeNametag; }
    public int inputTimeout() { return inputTimeout; }
    public String cancelWord() { return cancelWord; }
    public String clearWord() { return clearWord; }
    public String loreSeparator() { return loreSeparator; }
    public int maxLength() { return maxLength; }
    public int autosaveInterval() { return autosaveInterval; }
    public boolean soundsEnabled() { return soundsEnabled; }
    public String soundClick() { return soundClick; }
    public String soundSuccess() { return soundSuccess; }
    public String soundFail() { return soundFail; }
}
