package clre20.honorBadge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * 顏色轉換：同時支援 &a 傳統代碼、&#RRGGBB HEX、§x§R§R§G§G§B§B 與 MiniMessage。
 * 做法是先把傳統代碼轉成 MiniMessage 標籤，再交給 MiniMessage 解析。
 */
public final class ColorUtil {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private static final String[] COLOR_TAGS = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"
    };

    private ColorUtil() {
    }

    /** 解析文字成 Component。 */
    public static Component parse(String input, TagResolver... resolvers) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return MINI.deserialize(toMiniMessage(input), resolvers);
    }

    /** 解析成物品名稱／說明用的 Component（預設不要斜體）。 */
    public static Component item(String input, TagResolver... resolvers) {
        return parse(input, resolvers).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** 轉成 § 傳統格式字串（HEX 會變成 §x§R§R§G§G§B§B），給 PlaceholderAPI 使用。 */
    public static String toLegacy(Component component) {
        return SECTION.serialize(component);
    }

    /**
     * 將顏色或格式字串解析為 Legacy 顏色代碼（支援 &4、&#FF5555 與 MiniMessage 樣式標籤）。
     * 避免 Adventure 在序列化無文字內容的 Component 時省略顏色代碼。
     */
    public static String toLegacyColor(String colorCode) {
        if (colorCode == null || colorCode.isEmpty()) {
            return "";
        }
        String serialized = toLegacy(parse(colorCode + "X"));
        if (serialized.endsWith("X")) {
            return serialized.substring(0, serialized.length() - 1);
        }
        return colorCode.replace('&', '§');
    }

    /** 去掉所有顏色後的純文字。 */
    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String plain(String input) {
        return plain(parse(input));
    }

    /** 把 &／§ 代碼轉為 MiniMessage 標籤。顏色代碼會先 reset，與原版行為一致。 */
    static String toMiniMessage(String in) {
        StringBuilder out = new StringBuilder(in.length() + 16);
        int len = in.length();
        for (int i = 0; i < len; i++) {
            char c = in.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < len) {
                char n = Character.toLowerCase(in.charAt(i + 1));
                // &#RRGGBB
                if (n == '#' && i + 7 < len && isHex(in, i + 2, 6)) {
                    out.append("<reset><#").append(in, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                // §x§R§R§G§G§B§B（也接受 &x&R...）
                if (n == 'x' && i + 13 < len && isRepeatedHex(in, i + 2)) {
                    out.append("<reset><#");
                    for (int k = 0; k < 6; k++) {
                        out.append(in.charAt(i + 3 + k * 2));
                    }
                    out.append('>');
                    i += 13;
                    continue;
                }
                int colorIndex = "0123456789abcdef".indexOf(n);
                if (colorIndex >= 0) {
                    out.append("<reset><").append(COLOR_TAGS[colorIndex]).append('>');
                    i++;
                    continue;
                }
                String format = switch (n) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> null;
                };
                if (format != null) {
                    out.append(format);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String s, int from, int count) {
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isRepeatedHex(String s, int from) {
        for (int k = 0; k < 6; k++) {
            char marker = s.charAt(from + k * 2);
            if (marker != '&' && marker != '§') {
                return false;
            }
            if (Character.digit(s.charAt(from + k * 2 + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
