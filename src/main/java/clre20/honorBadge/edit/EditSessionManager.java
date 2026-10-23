package clre20.honorBadge.edit;

import clre20.honorBadge.title.Title;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 編輯草稿與鎖定：同一個稱號同時只能一人編輯。只在主執行緒使用。
 */
public final class EditSessionManager {

    private final Map<String, UUID> locks = new HashMap<>();
    private final Map<UUID, EditSession> sessions = new HashMap<>();

    /** 開始編輯；若已被其他人鎖定則回傳 null。 */
    public EditSession start(UUID editor, String id, Title base, Material defaultIcon) {
        UUID owner = locks.get(id);
        if (owner != null && !owner.equals(editor)) {
            return null;
        }
        end(editor);
        EditSession session = new EditSession(editor, id, base, defaultIcon);
        sessions.put(editor, session);
        locks.put(id, editor);
        return session;
    }

    public EditSession get(UUID editor) {
        return sessions.get(editor);
    }

    public UUID lockedBy(String id) {
        return locks.get(id);
    }

    public void end(UUID editor) {
        EditSession session = sessions.remove(editor);
        if (session != null) {
            locks.remove(session.id(), editor);
        }
    }

    public void endAll() {
        sessions.clear();
        locks.clear();
    }
}
