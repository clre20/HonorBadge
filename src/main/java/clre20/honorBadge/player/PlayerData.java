package clre20.honorBadge.player;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 一位玩家的稱號資料。寫入動作都加鎖；equipped 為 volatile，PAPI 可在任何執行緒讀取。
 */
public final class PlayerData {

    private final UUID uuid;
    private final Set<String> owned = new LinkedHashSet<>();
    private volatile String name;
    private volatile String equipped;
    private boolean dirty;
    private boolean evicted;
    private final long loadedAt = System.currentTimeMillis();

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public String equipped() { return equipped; }
    long loadedAt() { return loadedAt; }

    public synchronized void setName(String name) {
        if (name != null && !name.equals(this.name)) {
            this.name = name;
            dirty = true;
        }
    }

    public synchronized boolean owns(String id) {
        return owned.contains(id);
    }

    public synchronized List<String> ownedSnapshot() {
        return new ArrayList<>(owned);
    }

    public synchronized int ownedCount() {
        return owned.size();
    }

    synchronized boolean add(String id) {
        if (owned.add(id)) {
            dirty = true;
            return true;
        }
        return false;
    }

    /** 移除稱號；若正在使用則一併卸下。 */
    synchronized boolean remove(String id) {
        if (!owned.remove(id)) {
            return false;
        }
        if (id.equals(equipped)) {
            equipped = null;
        }
        dirty = true;
        return true;
    }

    synchronized boolean setEquipped(String id) {
        if (id != null && !owned.contains(id)) {
            return false;
        }
        if (id == null ? equipped == null : id.equals(equipped)) {
            return false;
        }
        equipped = id;
        dirty = true;
        return true;
    }

    synchronized boolean isDirty() { return dirty; }
    synchronized boolean isEvicted() { return evicted; }
    synchronized void markEvicted() { evicted = true; }

    /** 取得存檔內容並清除髒標記。 */
    synchronized String serializeAndClean() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("name", name);
        yaml.set("equipped", equipped == null ? "" : equipped);
        yaml.set("owned", new ArrayList<>(owned));
        dirty = false;
        return yaml.saveToString();
    }

    static PlayerData fromYaml(UUID uuid, YamlConfiguration yaml) {
        PlayerData data = new PlayerData(uuid, yaml.getString("name", ""));
        data.owned.addAll(yaml.getStringList("owned"));
        String eq = yaml.getString("equipped", "");
        data.equipped = eq == null || eq.isEmpty() ? null : eq;
        return data;
    }

    /** 移除已不存在的稱號（例如刪除時玩家資料沒被更新到）。 */
    synchronized void cleanup(java.util.function.Predicate<String> exists) {
        if (owned.removeIf(id -> !exists.test(id))) {
            dirty = true;
        }
        if (equipped != null && (!owned.contains(equipped))) {
            equipped = null;
            dirty = true;
        }
    }
}
