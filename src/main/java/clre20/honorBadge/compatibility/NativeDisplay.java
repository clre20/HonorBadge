package clre20.honorBadge.compatibility;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.player.PlayerData;
import clre20.honorBadge.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原生顯示（Tab 列表、頭頂名稱）。預設關閉；有 TAB 插件時請改用 PAPI 變數。
 * 只在主執行緒呼叫。
 */
public final class NativeDisplay {

    private static final String TEAM_PREFIX = "hb_";

    private final HonorBadge plugin;
    /** 被我們改過 Tab 名稱的玩家，只還原這些人，不動其他插件的設定。 */
    private final Set<UUID> tabApplied = ConcurrentHashMap.newKeySet();

    public NativeDisplay(HonorBadge plugin) {
        this.plugin = plugin;
    }

    /** 目前戴著的稱號（已套用外框），沒有則回傳 null。 */
    public Title equippedTitle(UUID uuid) {
        PlayerData data = plugin.players().get(uuid);
        if (data == null || data.equipped() == null) {
            return null;
        }
        return plugin.titles().get(data.equipped());
    }

    public void refresh(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            return;
        }
        boolean tab = plugin.settings().nativeTab();
        boolean nametag = plugin.settings().nativeNametag();
        if (!tab && !nametag) {
            clear(player);
            return;
        }
        Title title = equippedTitle(uuid);
        Component prefix = title == null ? Component.empty() : title.formattedComponent();

        if (tab && title != null) {
            player.playerListName(prefix.append(player.name()));
            tabApplied.add(uuid);
        } else if (tabApplied.remove(uuid)) {
            player.playerListName(null);
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = teamName(uuid);
        Team team = board.getTeam(teamName);
        if (nametag && title != null) {
            if (team == null) {
                team = board.registerNewTeam(teamName);
            }
            team.prefix(prefix);
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        } else if (team != null) {
            team.unregister();
        }
    }

    public void refreshWearers(String id) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.players().get(player.getUniqueId());
            if (data != null && id.equals(data.equipped())) {
                refresh(player.getUniqueId());
            }
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player.getUniqueId());
        }
    }

    /** 移除原生顯示（功能關閉、插件停用時）。只還原 HonorBadge 自己改過的部分。 */
    public void clear(Player player) {
        if (tabApplied.remove(player.getUniqueId())) {
            player.playerListName(null);
        }
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(player.getUniqueId()));
        if (team != null) {
            team.unregister();
        }
    }

    /** 玩家離線：移除隊伍，不需要還原 Tab 名稱。 */
    public void forget(Player player) {
        tabApplied.remove(player.getUniqueId());
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName(player.getUniqueId()));
        if (team != null) {
            team.unregister();
        }
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        tabApplied.clear();
    }

    private static String teamName(UUID uuid) {
        return TEAM_PREFIX + uuid.toString().replace("-", "").substring(0, 13);
    }
}
