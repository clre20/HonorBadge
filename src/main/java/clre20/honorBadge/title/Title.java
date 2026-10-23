package clre20.honorBadge.title;

import clre20.honorBadge.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;

/**
 * 一個稱號（不可變）。顯示用的 Component 與 PAPI 字串在建立時就算好，之後直接讀取。
 */
public final class Title {

    private final String id;
    private final String displayName;
    private final Material icon;
    private final List<String> lore;
    private final int sort;
    private final long created;

    // 快取的顯示結果
    private final Component nameComponent;
    private final Component formattedComponent;
    private final String legacyRaw;
    private final String legacyFormatted;
    private final String formattedSource;

    public Title(String id, String displayName, Material icon, List<String> lore, int sort, long created, String titleFormat) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = List.copyOf(lore);
        this.sort = sort;
        this.created = created;

        this.nameComponent = ColorUtil.parse(displayName);
        this.formattedSource = titleFormat.replace("{title}", displayName);
        this.formattedComponent = ColorUtil.parse(formattedSource);
        this.legacyRaw = ColorUtil.toLegacy(nameComponent);
        this.legacyFormatted = ColorUtil.toLegacy(formattedComponent);
    }

    public Title withFormat(String titleFormat) {
        return new Title(id, displayName, icon, lore, sort, created, titleFormat);
    }

    public String id() { return id; }
    /** 原始顯示名稱（含顏色標記），用於訊息中的 {title}。 */
    public String displayName() { return displayName; }
    public Material icon() { return icon; }
    public List<String> lore() { return lore; }
    public int sort() { return sort; }
    public long created() { return created; }
    public Component nameComponent() { return nameComponent; }
    /** 套用 title-format 後的 Component。 */
    public Component formattedComponent() { return formattedComponent; }
    /** 套用 title-format 前的原始標記，用於原生聊天格式。 */
    public String formattedSource() { return formattedSource; }
    public String legacyRaw() { return legacyRaw; }
    public String legacyFormatted() { return legacyFormatted; }
}
