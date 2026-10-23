package clre20.honorBadge.config;

import clre20.honorBadge.HonorBadge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.logging.Level;

/**
 * 讀取 jar 內的預設檔，與使用者的檔案比對，補上缺少的項目並保留原本的值。
 * 版本號不同時先備份成 .bak。
 */
public final class YamlUpdater {

    private YamlUpdater() {
    }

    /**
     * @param fileName    例如 config.yml
     * @param versionKey  版本欄位，例如 config-version
     * @return 補齊後的設定（失敗時回傳 jar 內的預設值，避免 NPE）
     */
    public static YamlConfiguration loadAndUpdate(HonorBadge plugin, String fileName, String versionKey) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration defaults = loadDefaults(plugin, fileName);
        YamlConfiguration user = new YamlConfiguration();
        try {
            user.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, fileName + " 格式錯誤，本次改用預設值：" + e.getMessage());
            return defaults;
        }

        int defaultVersion = defaults.getInt(versionKey, 1);
        int userVersion = user.getInt(versionKey, 0);
        boolean changed = false;

        if (userVersion != defaultVersion) {
            File backup = new File(plugin.getDataFolder(), fileName + ".bak");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().info(plugin.console("console.config-backup",
                        Map.of("file", fileName, "backup", backup.getName())));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "備份 " + fileName + " 失敗", e);
            }
            user.set(versionKey, defaultVersion);
            changed = true;
        }

        changed |= fillMissing(defaults, user);

        if (changed) {
            try {
                user.save(file);
                plugin.getLogger().info(plugin.console("console.config-updated", Map.of("file", fileName)));
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "寫回 " + fileName + " 失敗", e);
            }
        }
        user.setDefaults(defaults);
        return user;
    }

    public static YamlConfiguration loadDefaults(HonorBadge plugin, String fileName) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream in = plugin.getResource(fileName)) {
            if (in != null) {
                defaults.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "讀取內建 " + fileName + " 失敗", e);
        }
        return defaults;
    }

    private static boolean fillMissing(ConfigurationSection defaults, YamlConfiguration user) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                if (!user.contains(key, true)) {
                    user.createSection(key);
                    copyComments(defaults, user, key);
                    changed = true;
                }
                continue;
            }
            if (!user.contains(key, true)) {
                user.set(key, defaults.get(key));
                copyComments(defaults, user, key);
                changed = true;
            }
        }
        return changed;
    }

    private static void copyComments(ConfigurationSection defaults, YamlConfiguration user, String key) {
        try {
            user.setComments(key, defaults.getComments(key));
            user.setInlineComments(key, defaults.getInlineComments(key));
        } catch (Throwable ignored) {
            // 舊 API 沒有註解功能時略過
        }
    }
}
