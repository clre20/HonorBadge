package clre20.honorBadge.gui;

import clre20.honorBadge.HonorBadge;
import clre20.honorBadge.edit.EditSession;
import clre20.honorBadge.title.Title;
import clre20.honorBadge.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 稱號設定介面（create／edit）。
 */
public final class EditMenu extends Menu {

    private static final int SLOT_PREVIEW = 4, SLOT_ICON = 10, SLOT_NAME = 12, SLOT_LORE = 14, SLOT_SORT = 16,
            SLOT_CANCEL = 18, SLOT_CHAT_PREVIEW = 22, SLOT_SAVE = 26;

    public EditMenu(HonorBadge plugin, UUID viewer) {
        super(plugin, viewer);
    }

    // ==================== 進入點 ====================

    /** 開始建立或編輯稱號（主執行緒）。 */
    public static void start(HonorBadge plugin, Player player, String id, boolean create) {
        UUID lock = plugin.edits().lockedBy(id);
        if (lock != null && !lock.equals(player.getUniqueId())) {
            OfflinePlayer editor = Bukkit.getOfflinePlayer(lock);
            plugin.messages().send(player, "general.title-being-edited",
                    Map.of("id", id, "editor", editor.getName() == null ? lock.toString() : editor.getName()));
            plugin.sounds().fail(player);
            return;
        }
        plugin.chatInput().cancel(player.getUniqueId());
        Title base = create ? null : plugin.titles().get(id);
        Material defaultIcon = plugin.settings().guiItem("default-icon", Material.NAME_TAG);
        EditSession session = plugin.edits().start(player.getUniqueId(), id, base, defaultIcon);
        if (session == null) {
            return;
        }
        switchTo(plugin, player, session, new EditMenu(plugin, player.getUniqueId()));
    }

    /** 在草稿流程內切換介面，切換時的關閉事件不算取消。 */
    static void switchTo(HonorBadge plugin, Player player, EditSession session, Menu next) {
        session.setTransitioning(true);
        try {
            next.open(player);
        } finally {
            session.setTransitioning(false);
        }
    }

    /** 取得目前玩家的草稿；介面與草稿不一致時回傳 null。 */
    static EditSession session(HonorBadge plugin, UUID viewer) {
        return plugin.edits().get(viewer);
    }

    // ==================== 介面 ====================

    @Override
    protected Component title() {
        EditSession s = session(plugin, viewer);
        String id = s == null ? "" : s.id();
        boolean isNew = s != null && s.isNew();
        return plugin.messages().get(isNew ? "gui.edit.title-create" : "gui.edit.title-edit", Map.of("id", id));
    }

    @Override
    protected int rows() {
        return 3;
    }

    @Override
    protected void render() {
        EditSession s = session(plugin, viewer);
        if (s == null) {
            return;
        }
        Title draft = s.toTitle(plugin.settings().titleFormat());
        Map<String, String> ph = Map.of(
                "id", s.id(),
                "icon", s.icon().name(),
                "title", s.displayName(),
                "sort", String.valueOf(s.sort()));

        List<Component> previewLore = new ArrayList<>(
                plugin.messages().itemLore("gui.edit.preview-lore-header", ph));
        previewLore.addAll(plugin.messages().itemLore("gui.player.title-lore-available", ph));
        set(SLOT_PREVIEW, ItemFactory.title(plugin, draft, previewLore, null, false));

        set(SLOT_ICON, button("edit-icon", Material.ITEM_FRAME, "gui.edit.icon", ph, "icon", null));
        set(SLOT_NAME, button("edit-name", Material.OAK_SIGN, "gui.edit.name", ph, "name", null));

        List<String> loreLines = s.lore().isEmpty() ? List.of(plugin.messages().raw("gui.edit.lore-none")) : s.lore();
        set(SLOT_LORE, ItemFactory.create(plugin,
                plugin.settings().guiItem("edit-lore", Material.WRITABLE_BOOK),
                plugin.messages().itemName("gui.edit.lore.name", ph),
                plugin.messages().itemLore("gui.edit.lore.lore", ph, loreLines),
                "lore", null, false));

        set(SLOT_SORT, button("edit-sort", Material.COMPARATOR, "gui.edit.sort", ph, "sort", null));
        set(SLOT_CANCEL, button("edit-cancel", Material.RED_CONCRETE, "gui.edit.cancel", ph, "cancel", null));
        set(SLOT_CHAT_PREVIEW, button("edit-preview", Material.SPYGLASS, "gui.edit.preview", ph, "preview", null));
        set(SLOT_SAVE, button("edit-save", Material.LIME_CONCRETE, "gui.edit.save", ph, "save", null));
        fillAll();
    }

    @Override
    public void onClick(Player player, String action, String value) {
        EditSession s = session(plugin, viewer);
        if (s == null) {
            plugin.sync(player::closeInventory);
            return;
        }
        switch (action) {
            case "icon" -> setIcon(player, s);
            case "name" -> promptName(player, s);
            case "lore" -> promptLore(player, s);
            case "sort" -> {
                plugin.sounds().click(player);
                plugin.sync(() -> switchTo(plugin, player, s, new SortMenu(plugin, viewer)));
            }
            case "preview" -> preview(player, s);
            case "cancel" -> {
                plugin.edits().end(viewer);
                plugin.messages().send(player, "edit.cancelled");
                plugin.sounds().click(player);
                plugin.sync(player::closeInventory);
            }
            case "save" -> save(player, s);
            default -> {
            }
        }
    }

    @Override
    public void onClose(Player player) {
        discardIfClosed(plugin, player, viewer);
    }

    /** 不是切換介面造成的關閉（例如按 Esc），就捨棄草稿。 */
    static void discardIfClosed(HonorBadge plugin, Player player, UUID viewer) {
        EditSession s = session(plugin, viewer);
        if (s != null && !s.transitioning()) {
            plugin.edits().end(viewer);
            plugin.chatInput().cancel(viewer);
            plugin.messages().send(player, "edit.discarded");
        }
    }

    // ==================== 動作 ====================

    private void setIcon(Player player, EditSession s) {
        Material type = player.getInventory().getItemInMainHand().getType();
        if (type.isAir()) {
            plugin.messages().send(player, "edit.icon-empty-hand");
            plugin.sounds().fail(player);
            return;
        }
        s.setIcon(type);
        plugin.messages().send(player, "edit.icon-set", Map.of("icon", type.name()));
        plugin.sounds().success(player);
        refresh();
    }

    private void preview(Player player, EditSession s) {
        Title draft = s.toTitle(plugin.settings().titleFormat());
        plugin.messages().send(player, "edit.preview");
        plugin.messages().send(player, "edit.preview-line",
                Map.of("title", draft.formattedSource(), "player", player.getName()));
        plugin.sounds().click(player);
    }

    private void promptName(Player player, EditSession s) {
        plugin.sounds().click(player);
        s.setTransitioning(true);
        plugin.sync(player::closeInventory);
        requestName(player, s);
    }

    private void requestName(Player player, EditSession s) {
        plugin.messages().send(player, "edit.input-name", inputPlaceholders());
        plugin.chatInput().request(player, text -> {
            if (!stillActive(s)) {
                return;
            }
            String plain = ColorUtil.plain(text).trim();
            if (plain.isEmpty()) {
                plugin.messages().send(player, "edit.name-empty");
                plugin.sounds().fail(player);
                requestName(player, s);
                return;
            }
            if (plain.length() > plugin.settings().maxLength()) {
                plugin.messages().send(player, "edit.name-too-long",
                        Map.of("max", String.valueOf(plugin.settings().maxLength())));
                plugin.sounds().fail(player);
                requestName(player, s);
                return;
            }
            s.setDisplayName(text);
            plugin.messages().send(player, "edit.name-set", Map.of("title", text));
            plugin.sounds().success(player);
            reopen(player, s);
        }, () -> reopen(player, s), () -> timeout(player, s));
    }

    private void promptLore(Player player, EditSession s) {
        plugin.sounds().click(player);
        s.setTransitioning(true);
        plugin.sync(player::closeInventory);
        plugin.messages().send(player, "edit.input-lore", inputPlaceholders());
        plugin.chatInput().request(player, text -> {
            if (!stillActive(s)) {
                return;
            }
            if (text.equalsIgnoreCase(plugin.settings().clearWord())) {
                s.setLore(List.of());
                plugin.messages().send(player, "edit.lore-cleared");
            } else {
                String sep = plugin.settings().loreSeparator();
                List<String> lines = Arrays.stream(text.split(java.util.regex.Pattern.quote(sep), -1))
                        .map(String::trim)
                        .toList();
                s.setLore(lines);
                plugin.messages().send(player, "edit.lore-set");
            }
            plugin.sounds().success(player);
            reopen(player, s);
        }, () -> reopen(player, s), () -> timeout(player, s));
    }

    private Map<String, String> inputPlaceholders() {
        return Map.of(
                "cancel", plugin.settings().cancelWord(),
                "clear", plugin.settings().clearWord(),
                "separator", plugin.settings().loreSeparator(),
                "timeout", String.valueOf(plugin.settings().inputTimeout()));
    }

    private boolean stillActive(EditSession s) {
        return plugin.edits().get(viewer) == s;
    }

    private void reopen(Player player, EditSession s) {
        if (!stillActive(s) || !player.isOnline()) {
            return;
        }
        switchTo(plugin, player, s, new EditMenu(plugin, viewer));
    }

    private void timeout(Player player, EditSession s) {
        if (!stillActive(s)) {
            return;
        }
        plugin.edits().end(viewer);
        if (player.isOnline()) {
            plugin.messages().send(player, "edit.input-timeout");
            plugin.sounds().fail(player);
        }
    }

    private void save(Player player, EditSession s) {
        if (s.isNew() && plugin.titles().exists(s.id())) {
            plugin.messages().send(player, "general.title-exists", Map.of("id", s.id()));
            plugin.sounds().fail(player);
            return;
        }
        Title title = s.toTitle(plugin.settings().titleFormat());
        plugin.titles().save(title);
        plugin.edits().end(viewer);
        plugin.messages().send(player, s.isNew() ? "edit.created" : "edit.saved",
                Map.of("title", title.displayName(), "id", title.id()));
        plugin.sounds().success(player);
        plugin.nativeDisplay().refreshWearers(title.id());
        plugin.sync(player::closeInventory);
    }
}
