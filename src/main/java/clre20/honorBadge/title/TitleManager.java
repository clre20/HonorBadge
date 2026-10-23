package clre20.honorBadge.title;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.util.FileUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 稱號資料：全部放在記憶體，只在建立、儲存、刪除時寫檔（非同步）。
 */
public final class TitleManager {

    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1,32}$");
    public static final Comparator<Title> ORDER = Comparator.comparingInt(Title::sort)
            .thenComparingLong(Title::created)
            .thenComparing(Title::id);

    private final HonorBadge plugin;
    private final File folder;
    private final Map<String, Title> titles = new ConcurrentHashMap<>();
    private volatile List<Title> sortedCache = List.of();

    public TitleManager(HonorBadge plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "data");
    }

    /** 啟動與 reload 時同步讀取（檔案很小，數量有限）。 */
    public void load() {
        titles.clear();
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("無法建立 data 資料夾");
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files != null) {
            String format = plugin.settings().titleFormat();
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4);
                if (!ID_PATTERN.matcher(id).matches()) {
                    continue;
                }
                try {
                    YamlConfiguration yaml = new YamlConfiguration();
                    yaml.load(file);
                    titles.put(id, fromYaml(id, yaml, format));
                } catch (Exception e) {
                    plugin.getLogger().warning(plugin.console("console.title-load-failed",
                            Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
                }
            }
        }
        rebuildSorted();
    }

    private Title fromYaml(String id, YamlConfiguration yaml, String format) {
        String name = yaml.getString("display-name", id);
        Material icon = Material.matchMaterial(yaml.getString("icon", "NAME_TAG"));
        if (icon == null || !icon.isItem() || icon.isAir()) {
            icon = Material.NAME_TAG;
        }
        List<String> lore = yaml.getStringList("lore");
        int sort = yaml.getInt("sort", 0);
        long created = parseTime(yaml.getString("created"));
        return new Title(id, name, icon, lore, sort, created, format);
    }

    private static long parseTime(String text) {
        if (text == null) {
            return 0L;
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (DateTimeParseException e) {
            return 0L;
        }
    }

    private void rebuildSorted() {
        List<Title> list = new ArrayList<>(titles.values());
        list.sort(ORDER);
        sortedCache = List.copyOf(list);
    }

    public Title get(String id) {
        return id == null ? null : titles.get(id);
    }

    public boolean exists(String id) {
        return id != null && titles.containsKey(id);
    }

    /** 依排序值排好的所有稱號（快取，不要修改）。 */
    public List<Title> sorted() {
        return sortedCache;
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(titles.keySet());
        ids.sort(String::compareTo);
        return ids;
    }

    public int size() {
        return titles.size();
    }

    /** 儲存：記憶體立即更新，檔案在 I/O 執行緒寫入。 */
    public void save(Title title) {
        titles.put(title.id(), title);
        rebuildSorted();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("display-name", title.displayName());
        yaml.set("icon", title.icon().name());
        yaml.set("lore", title.lore());
        yaml.set("sort", title.sort());
        yaml.set("created", OffsetDateTime.ofInstant(Instant.ofEpochMilli(title.created()), ZoneId.systemDefault()).toString());
        String content = yaml.saveToString();
        File file = new File(folder, title.id() + ".yml");
        plugin.io().execute(() -> {
            try {
                FileUtil.writeAtomic(file.toPath(), content);
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.console("console.save-failed",
                        Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
            }
        });
    }

    public Title delete(String id) {
        Title removed = titles.remove(id);
        if (removed == null) {
            return null;
        }
        rebuildSorted();
        File file = new File(folder, id + ".yml");
        plugin.io().execute(() -> {
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.console("console.save-failed",
                        Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
            }
        });
        return removed;
    }
}
