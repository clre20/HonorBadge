package clre20.honorBadge.player;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.util.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 玩家資料管理。
 * <ul>
 *   <li>線上玩家的資料放在記憶體快取；離線玩家只在需要時讀檔，處理完即釋放。</li>
 *   <li>所有讀寫檔案都在單一 I/O 執行緒依序執行，所以不會讀到寫到一半的資料。</li>
 *   <li>變更只標記為髒資料，由定時存檔、玩家離線與關服時批次寫入。</li>
 * </ul>
 */
public final class PlayerDataManager {

    private static final long STALE_MILLIS = 60_000L;

    private final HonorBadge plugin;
    private final File folder;

    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();

    // 稱號 → 持有者索引，與玩家名稱索引（只存 UUID 與字串，不持有 Player）
    private final Map<String, Set<UUID>> holders = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<String, UUID> nameLookup = new ConcurrentHashMap<>();
    private volatile boolean indexReady;

    private BukkitTask autosaveTask;

    public PlayerDataManager(HonorBadge plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "playerdata");
    }

    // ==================== 生命週期 ====================

    public void start() {
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("無法建立 playerdata 資料夾");
        }
        // 支援插件熱重載：已在線上的玩家直接載入
        for (Player p : Bukkit.getOnlinePlayers()) {
            online.add(p.getUniqueId());
            loadAsync(p.getUniqueId(), p.getName());
        }
        plugin.io().execute(this::buildIndex);
        scheduleAutosave();
    }

    public void scheduleAutosave() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
        }
        long ticks = plugin.settings().autosaveInterval() * 20L;
        // 主執行緒只負責排入 I/O 佇列，實際寫檔在 I/O 執行緒
        autosaveTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> plugin.io().execute(this::saveDirtyAndEvictStale), ticks, ticks);
    }

    /** 關服：停止排程後，在主執行緒把剩下的髒資料寫完。 */
    public void shutdown() {
        if (autosaveTask != null) {
            autosaveTask.cancel();
            autosaveTask = null;
        }
        for (PlayerData data : cache.values()) {
            synchronized (data) {
                if (data.isDirty()) {
                    write(data);
                }
            }
        }
        cache.clear();
        online.clear();
        holders.clear();
        names.clear();
        nameLookup.clear();
    }

    // ==================== 進出伺服器 ====================

    /** 在 AsyncPlayerPreLoginEvent（非主執行緒）呼叫，等待資料載入完成，進服時資料就已就緒。 */
    public void loadForLogin(UUID uuid, String name) {
        try {
            loadAsync(uuid, name).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().warning("載入玩家 " + name + " 的資料逾時，將在進服後重試");
        }
    }

    public void handleJoin(Player player) {
        online.add(player.getUniqueId());
        if (!cache.containsKey(player.getUniqueId())) {
            loadAsync(player.getUniqueId(), player.getName())
                    .thenRun(() -> plugin.sync(() -> plugin.nativeDisplay().refresh(player.getUniqueId())));
        }
    }

    public void handleQuit(Player player) {
        UUID uuid = player.getUniqueId();
        online.remove(uuid);
        plugin.io().execute(() -> evict(uuid));
    }

    private CompletableFuture<PlayerData> loadAsync(UUID uuid, String name) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerData cached = cache.get(uuid);
            if (cached != null && !cached.isEvicted()) {
                cached.setName(name);
                updateName(uuid, name);
                return cached;
            }
            PlayerData data = readFile(uuid);
            if (data == null) {
                data = new PlayerData(uuid, name);
            }
            data.setName(name);
            data.cleanup(plugin.titles()::exists);
            for (String id : data.ownedSnapshot()) {
                indexAdd(id, uuid);
            }
            updateName(uuid, name);
            cache.put(uuid, data);
            return data;
        }, plugin.io());
    }

    // ==================== 查詢 ====================

    /** 線上玩家的資料；未載入或離線時回傳 null。 */
    public PlayerData get(UUID uuid) {
        PlayerData data = cache.get(uuid);
        return data == null || data.isEvicted() ? null : data;
    }

    /** 讀取任何玩家的資料（離線則讀檔，不放入快取）。沒有資料時回傳 null。 */
    public CompletableFuture<PlayerData> peek(UUID uuid) {
        PlayerData data = get(uuid);
        if (data != null) {
            return CompletableFuture.completedFuture(data);
        }
        return CompletableFuture.supplyAsync(() -> {
            PlayerData cached = get(uuid);
            if (cached != null) {
                return cached;
            }
            PlayerData file = readFile(uuid);
            if (file != null) {
                file.cleanup(plugin.titles()::exists);
            }
            return file;
        }, plugin.io());
    }

    public Set<UUID> holders(String id) {
        Set<UUID> set = holders.get(id);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    public int holderCount(String id) {
        Set<UUID> set = holders.get(id);
        return set == null ? 0 : set.size();
    }

    public String nameOf(UUID uuid) {
        String name = names.get(uuid);
        return name == null || name.isEmpty() ? uuid.toString() : name;
    }

    /** 依名稱（不分大小寫）找曾經進過伺服器並有資料的玩家。 */
    public UUID findByName(String name) {
        return nameLookup.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> holderNames(String id) {
        List<String> list = new ArrayList<>();
        for (UUID uuid : holders(id)) {
            list.add(nameOf(uuid));
        }
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    public boolean indexReady() {
        return indexReady;
    }

    // ==================== 修改 ====================

    /** 給予稱號。結果：true＝已給予、false＝本來就有。 */
    public CompletableFuture<Boolean> give(UUID uuid, String nameHint, String id) {
        return mutate(uuid, nameHint, true, data -> {
            boolean added = data.add(id);
            if (added) {
                indexAdd(id, uuid);
            }
            return added;
        }, false);
    }

    /** 移除稱號。結果：true＝已移除、false＝本來就沒有。 */
    public CompletableFuture<Boolean> remove(UUID uuid, String nameHint, String id) {
        return mutate(uuid, nameHint, false, data -> {
            boolean removed = data.remove(id);
            indexRemove(id, uuid);
            return removed;
        }, false);
    }

    /** 線上玩家換稱號（主執行緒）。id 為 null 表示卸下。 */
    public boolean equip(UUID uuid, String id) {
        PlayerData data = get(uuid);
        return data != null && data.setEquipped(id);
    }

    /** 刪除稱號時，從所有持有者身上移除。結果為受影響的人數。 */
    public CompletableFuture<Integer> removeTitleFromAll(String id) {
        Set<UUID> set = holders.remove(id);
        if (set == null || set.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(set.size());
        for (UUID uuid : set) {
            futures.add(mutate(uuid, null, false, data -> data.remove(id), false));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> (int) futures.stream().filter(f -> Boolean.TRUE.equals(f.join())).count());
    }

    /**
     * 修改玩家資料：在快取中就直接改（只標記髒資料）；否則在 I/O 執行緒讀檔、修改、寫回。
     */
    private <R> CompletableFuture<R> mutate(UUID uuid, String nameHint, boolean createIfMissing,
                                            Function<PlayerData, R> op, R noData) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            synchronized (cached) {
                if (!cached.isEvicted()) {
                    return CompletableFuture.completedFuture(op.apply(cached));
                }
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            PlayerData c = cache.get(uuid);
            if (c != null) {
                synchronized (c) {
                    if (!c.isEvicted()) {
                        return op.apply(c);
                    }
                }
            }
            PlayerData data = readFile(uuid);
            if (data == null) {
                if (!createIfMissing) {
                    return noData;
                }
                data = new PlayerData(uuid, nameHint == null ? "" : nameHint);
            }
            R result = op.apply(data);
            data.cleanup(plugin.titles()::exists);
            if (data.isDirty()) {
                write(data);
            }
            if (nameHint != null) {
                updateName(uuid, data.name().isEmpty() ? nameHint : data.name());
            }
            return result;
        }, plugin.io());
    }

    // ==================== I/O（只在 I/O 執行緒或關服時呼叫） ====================

    private void evict(UUID uuid) {
        PlayerData data = cache.get(uuid);
        if (data == null) {
            return;
        }
        synchronized (data) {
            if (online.contains(uuid)) {
                return;
            }
            if (data.isDirty()) {
                write(data);
            }
            data.markEvicted();
            cache.remove(uuid, data);
        }
    }

    private void saveDirtyAndEvictStale() {
        long now = System.currentTimeMillis();
        for (PlayerData data : cache.values()) {
            UUID uuid = data.uuid();
            if (!online.contains(uuid) && now - data.loadedAt() > STALE_MILLIS) {
                evict(uuid);
                continue;
            }
            synchronized (data) {
                if (data.isDirty() && !data.isEvicted()) {
                    write(data);
                }
            }
        }
    }

    private void buildIndex() {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        int count = 0;
        if (files != null) {
            for (File file : files) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                PlayerData cached = get(uuid);
                List<String> owned;
                String name;
                if (cached != null) {
                    owned = cached.ownedSnapshot();
                    name = cached.name();
                } else {
                    YamlConfiguration yaml = new YamlConfiguration();
                    try {
                        yaml.load(file);
                    } catch (Exception e) {
                        plugin.getLogger().warning(plugin.console("console.player-load-failed",
                                Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
                        continue;
                    }
                    owned = yaml.getStringList("owned");
                    name = yaml.getString("name", "");
                }
                updateName(uuid, name);
                for (String id : owned) {
                    if (plugin.titles().exists(id)) {
                        indexAdd(id, uuid);
                    }
                }
                count++;
            }
        }
        indexReady = true;
        plugin.getLogger().info(plugin.console("console.index-ready", Map.of("count", String.valueOf(count))));
    }

    private PlayerData readFile(UUID uuid) {
        File file = new File(folder, uuid + ".yml");
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning(plugin.console("console.player-load-failed",
                    Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
            return null;
        }
        return PlayerData.fromYaml(uuid, yaml);
    }

    private void write(PlayerData data) {
        File file = new File(folder, data.uuid() + ".yml");
        try {
            FileUtil.writeAtomic(file.toPath(), data.serializeAndClean());
        } catch (IOException e) {
            plugin.getLogger().warning(plugin.console("console.save-failed",
                    Map.of("file", file.getName(), "error", String.valueOf(e.getMessage()))));
        }
    }

    // ==================== 索引 ====================

    private void indexAdd(String id, UUID uuid) {
        holders.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    private void indexRemove(String id, UUID uuid) {
        Set<UUID> set = holders.get(id);
        if (set != null) {
            set.remove(uuid);
        }
    }

    private void updateName(UUID uuid, String name) {
        if (name == null || name.isEmpty()) {
            return;
        }
        String old = names.put(uuid, name);
        if (old != null && !old.equalsIgnoreCase(name)) {
            nameLookup.remove(old.toLowerCase(Locale.ROOT), uuid);
        }
        nameLookup.put(name.toLowerCase(Locale.ROOT), uuid);
    }
}
