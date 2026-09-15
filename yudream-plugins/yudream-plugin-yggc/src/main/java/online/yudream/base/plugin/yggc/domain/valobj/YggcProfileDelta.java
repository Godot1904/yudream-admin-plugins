package online.yudream.base.plugin.yggc.domain.valobj;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 本地角色集合与「上次已成功推送到 Union 主服务器」快照之间的差异。
 *
 * <p>纯计算：不读仓储、不发起上游请求，便于单测与复用。{@code pushed} 是快照（uuid → 角色名），
 * {@code local} 是本次扫描到的角色（uuid → 角色名）。据此得出三类待同步条目：
 * 新角色（POST /profile）、改名角色（PUT /profile/{uuid}）、已消失角色（DELETE /profile/{uuid}）。
 *
 * <p>注意：只有当本地扫描「完整成功」时才允许把缺失的 uuid 判定为删除，否则一次扫描故障
 * 就会误删主服务器上的角色索引，这个约束由调用方保证。
 */
public record YggcProfileDelta(List<Entry> added, List<Rename> renamed, List<String> removed) {

    /** 待新增的角色。 */
    public record Entry(String uuid, String name) {
    }

    /** 待改名的角色（from 为快照中的旧名字）。 */
    public record Rename(String uuid, String from, String to) {
    }

    public static YggcProfileDelta between(Map<String, String> pushed, Map<String, String> local) {
        Map<String, String> snapshot = pushed == null ? Map.of() : pushed;
        Map<String, String> current = local == null ? Map.of() : local;
        List<Entry> added = new ArrayList<>();
        List<Rename> renamed = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, String> entry : current.entrySet()) {
            String previous = snapshot.get(entry.getKey());
            if (previous == null) {
                added.add(new Entry(entry.getKey(), entry.getValue()));
            } else if (!previous.equals(entry.getValue())) {
                renamed.add(new Rename(entry.getKey(), previous, entry.getValue()));
            }
        }
        for (String uuid : snapshot.keySet()) {
            if (!current.containsKey(uuid)) {
                removed.add(uuid);
            }
        }
        return new YggcProfileDelta(List.copyOf(added), List.copyOf(renamed), List.copyOf(removed));
    }

    /** 没有任何差异时无需调用上游。 */
    public boolean empty() {
        return added.isEmpty() && renamed.isEmpty() && removed.isEmpty();
    }

    public int size() {
        return added.size() + renamed.size() + removed.size();
    }
}
