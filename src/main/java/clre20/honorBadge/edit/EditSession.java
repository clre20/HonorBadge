package clre20.honorBadge.edit;

import clre20.honorBadge.title.Title;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 建立／編輯中的稱號草稿。按下「確認儲存」前都不會寫入資料。
 */
public final class EditSession {

    private final UUID editor;
    private final String id;
    private final boolean isNew;
    private final long created;

    private String displayName;
    private Material icon;
    private List<String> lore;
    private int sort;

    /** 正在切換介面或等待聊天輸入，此時關閉介面不算取消。 */
    private boolean transitioning;

    EditSession(UUID editor, String id, Title base, Material defaultIcon) {
        this.editor = editor;
        this.id = id;
        this.isNew = base == null;
        if (base == null) {
            this.created = System.currentTimeMillis();
            this.displayName = "&f" + id;
            this.icon = defaultIcon;
            this.lore = new ArrayList<>();
            this.sort = 0;
        } else {
            this.created = base.created();
            this.displayName = base.displayName();
            this.icon = base.icon();
            this.lore = new ArrayList<>(base.lore());
            this.sort = base.sort();
        }
    }

    public Title toTitle(String titleFormat) {
        return new Title(id, displayName, icon, lore, sort, created, titleFormat);
    }

    public UUID editor() { return editor; }
    public String id() { return id; }
    public boolean isNew() { return isNew; }
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public List<String> lore() { return lore; }
    public int sort() { return sort; }
    public boolean transitioning() { return transitioning; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setIcon(Material icon) { this.icon = icon; }
    public void setLore(List<String> lore) { this.lore = new ArrayList<>(lore); }
    public void setSort(int sort) { this.sort = Math.max(-100000, Math.min(100000, sort)); }
    public void setTransitioning(boolean transitioning) { this.transitioning = transitioning; }
}
