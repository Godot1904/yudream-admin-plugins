package online.yudream.base.plugin.eduroam.infrastructure.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内的滑动窗口限流器，按客户端 IP 统计<b>失败</b>的核验尝试次数。
 *
 * <p>只统计失败：正常用户连续输错两次密码不该被计入「都算一次尝试」而被拦住成功的那一次，
 * 而暴力破解恰恰表现为大量失败，所以按失败计数既挡得住攻击也不误伤。
 *
 * <p>只放在内存里：限流是临时措施，不值得为它写一次数据库；进程重启后归零可以接受。
 * 传入 {@code now} 而不是内部取时间，测试里能精确控制窗口。
 */
public class AttemptRateLimiter {

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** 该 key 在窗口内是否已达到上限。 */
    public boolean blocked(String key, int maxPerWindow, long windowMillis, long now) {
        if (key == null || key.isBlank() || maxPerWindow <= 0 || windowMillis <= 0) {
            return false;
        }
        Deque<Long> window = failures.get(key);
        if (window == null) {
            return false;
        }
        synchronized (window) {
            prune(window, windowMillis, now);
            return window.size() >= maxPerWindow;
        }
    }

    /** 记一次失败。 */
    public void record(String key, long now) {
        if (key == null || key.isBlank()) {
            return;
        }
        Deque<Long> window = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            window.addLast(now);
        }
    }

    /** 核验成功后清空该 IP 的失败窗口。 */
    public void reset(String key) {
        if (key != null && !key.isBlank()) {
            failures.remove(key);
        }
    }

    /** 当前窗口内的失败次数，供测试与诊断使用。 */
    public int count(String key, long windowMillis, long now) {
        Deque<Long> window = failures.get(key);
        if (window == null) {
            return 0;
        }
        synchronized (window) {
            prune(window, windowMillis, now);
            return window.size();
        }
    }

    private static void prune(Deque<Long> window, long windowMillis, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
    }
}
