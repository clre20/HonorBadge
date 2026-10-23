package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.edit.EditSession;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * 排序值調整頁：[-10][-1][數值][+1][+10]。
 */
public final class SortMenu extends Menu {

    public SortMenu(HonorBadge plugin, UUID viewer) {
        super(plugin, viewer);
    }

    @Override
    protected Component title() {
        EditSession s = EditMenu.session(plugin, viewer);
        return plugin.messages().get("gui.sort.title", Map.of("id", s == null ? "" : s.id()));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        EditSession s = EditMenu.session(plugin, viewer);
        if (s == null) {
            return;
        }
        Map<String, String> ph = Map.of("id", s.id(), "sort", String.valueOf(s.sort()));
        set(11, button("sort-minus", Material.RED_STAINED_GLASS_PANE, "gui.sort.minus10", ph, "add", "-10"));
        set(12, button("sort-minus", Material.RED_STAINED_GLASS_PANE, "gui.sort.minus1", ph, "add", "-1"));
        set(13, button("sort-value", Material.PAPER, "gui.sort.value", ph, null, null));
        set(14, button("sort-plus", Material.LIME_STAINED_GLASS_PANE, "gui.sort.plus1", ph, "add", "1"));
        set(15, button("sort-plus", Material.LIME_STAINED_GLASS_PANE, "gui.sort.plus10", ph, "add", "10"));
        set(22, button("back", Material.ARROW, "gui.sort.back", ph, "back", null));
        fillAll();
    }

    @Override
    public void onClick(Player player, String action, String value) {
        EditSession s = EditMenu.session(plugin, viewer);
        if (s == null) {
            plugin.sync(player::closeInventory);
            return;
        }
        switch (action) {
            case "add" -> {
                try {
                    s.setSort(s.sort() + Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                    return;
                }
                plugin.sounds().click(player);
                refresh();
            }
            case "back" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> EditMenu.switchTo(plugin, player, s, new EditMenu(plugin, viewer)));
            }
            default -> {
            }
        }
    }

    @Override
    public void onClose(Player player) {
        EditMenu.discardIfClosed(plugin, player, viewer);
    }
}
