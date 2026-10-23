package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.player.PlayerData;
import clre20.honorBadge.title.Title;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 玩家介面（/hb）：列出自己擁有的稱號，點一下就換上。
 */
public final class PlayerMenu extends Menu {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45, SLOT_UNEQUIP = 47, SLOT_CURRENT = 49, SLOT_CLOSE = 51, SLOT_NEXT = 53;

    private int page;
    private int pages = 1;

    public PlayerMenu(HonorBadge plugin, UUID viewer, int page) {
        super(plugin, viewer);
        this.page = Math.max(0, page);
        computePages();
    }

    private List<Title> ownedTitles() {
        PlayerData data = plugin.players().get(viewer);
        List<Title> list = new ArrayList<>();
        if (data == null) {
            return list;
        }
        for (Title title : plugin.titles().sorted()) {
            if (data.owns(title.id())) {
                list.add(title);
            }
        }
        return list;
    }

    private void computePages() {
        int count = ownedTitles().size();
        pages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.min(page, pages - 1);
    }

    @Override
    protected Component title() {
        return plugin.messages().get("gui.player.title",
                Map.of("page", String.valueOf(page + 1), "pages", String.valueOf(pages)));
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void render() {
        PlayerData data = plugin.players().get(viewer);
        List<Title> owned = ownedTitles();
        String equipped = data == null ? null : data.equipped();

        if (owned.isEmpty()) {
            set(22, button("empty", Material.STRUCTURE_VOID, "gui.player.empty", Map.of(), null, null));
        } else {
            List<Component> available = plugin.messages().itemLore("gui.player.title-lore-available", Map.of());
            List<Component> inUse = plugin.messages().itemLore("gui.player.title-lore-equipped", Map.of());
            int start = page * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE && start + i < owned.size(); i++) {
                Title title = owned.get(start + i);
                boolean isEquipped = title.id().equals(equipped);
                set(i, ItemFactory.title(plugin, title, isEquipped ? inUse : available, "equip", isEquipped));
            }
        }

        if (page > 0) {
            set(SLOT_PREV, button("previous-page", Material.ARROW, "gui.player.previous", Map.of(), "prev", null));
        }
        if (page < pages - 1) {
            set(SLOT_NEXT, button("next-page", Material.ARROW, "gui.player.next", Map.of(), "next", null));
        }
        set(SLOT_UNEQUIP, button("unequip", Material.BUCKET, "gui.player.unequip", Map.of(), "unequip", null));

        Title current = equipped == null ? null : plugin.titles().get(equipped);
        String currentText = current == null ? plugin.messages().raw("gui.player.current.none") : current.displayName();
        set(SLOT_CURRENT, button("current-info", Material.NAME_TAG, "gui.player.current",
                Map.of("title", currentText), null, null));
        set(SLOT_CLOSE, button("close", Material.BARRIER, "gui.player.close", Map.of(), "close", null));
        fillRow(5);
    }

    @Override
    public void onClick(Player player, String action, String value) {
        switch (action) {
            case "prev" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> new PlayerMenu(plugin, viewer, page - 1).open(player));
            }
            case "next" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> new PlayerMenu(plugin, viewer, page + 1).open(player));
            }
            case "close" -> {
                plugin.sounds().click(player);
                plugin.sync(player::closeInventory);
            }
            case "unequip" -> unequip(player);
            case "equip" -> equip(player, value);
            default -> {
            }
        }
    }

    private void equip(Player player, String id) {
        PlayerData data = plugin.players().get(viewer);
        Title title = plugin.titles().get(id);
        if (data == null) {
            plugin.messages().send(player, "general.data-not-ready");
            plugin.sounds().fail(player);
            return;
        }
        if (title == null || !data.owns(id)) {
            plugin.messages().send(player, "equip.not-owned");
            plugin.sounds().fail(player);
            refresh();
            return;
        }
        if (id.equals(data.equipped())) {
            plugin.messages().send(player, "equip.already");
            plugin.sounds().fail(player);
            return;
        }
        plugin.players().equip(viewer, id);
        plugin.nativeDisplay().refresh(viewer);
        plugin.messages().send(player, "equip.success", Map.of("title", title.displayName(), "id", id));
        plugin.sounds().success(player);
        plugin.sync(player::closeInventory);
    }

    private void unequip(Player player) {
        PlayerData data = plugin.players().get(viewer);
        if (data == null) {
            plugin.messages().send(player, "general.data-not-ready");
            plugin.sounds().fail(player);
            return;
        }
        if (data.equipped() == null) {
            plugin.messages().send(player, "equip.nothing-equipped");
            plugin.sounds().fail(player);
            return;
        }
        plugin.players().equip(viewer, null);
        plugin.nativeDisplay().refresh(viewer);
        plugin.messages().send(player, "equip.unequip-success");
        plugin.sounds().success(player);
        plugin.sync(player::closeInventory);
    }
}
