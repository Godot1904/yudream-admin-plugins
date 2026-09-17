package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.infrastructure.repository.EduroamLoginTicketDocumentRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EduroamTicketConcurrencyTest {
    @Test
    void twoNodesReadingSameProofOnlyConsumeOnce() throws Exception {
        var row = new AtomicReference<Map<String, Object>>(new HashMap<>(Map.of(
                "id", "proof", "email", "student@site.edu.cn", "identity", "student@school.edu.cn",
                "state", "state", "createdAt", 100L, "expiresAt", 300_100L)));
        var bothRead = new CyclicBarrier(2);
        PluginDocumentStore store = (PluginDocumentStore) Proxy.newProxyInstance(
                PluginDocumentStore.class.getClassLoader(), new Class<?>[]{PluginDocumentStore.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> {
                        var snapshot = new HashMap<>(row.get());
                        bothRead.await(10, TimeUnit.SECONDS);
                        yield Optional.of(snapshot);
                    }
                    case "updateIfFieldAtMost" -> {
                        synchronized (row) {
                            var current = row.get();
                            if (current == null || (long) current.get(args[2]) > (long) args[3]) yield false;
                            @SuppressWarnings("unchecked") var updates = (Map<String, Object>) args[4];
                            current.putAll(updates);
                            yield true;
                        }
                    }
                    case "delete" -> { row.set(null); yield null; }
                    default -> throw new AssertionError(method.getName());
                });
        var node1 = new EduroamLoginTicketDocumentRepository(store);
        var node2 = new EduroamLoginTicketDocumentRepository(store);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> node1.consume("proof"));
            var second = executor.submit(() -> node2.consume("proof"));
            Optional<EduroamLoginTicket> a = first.get(10, TimeUnit.SECONDS);
            Optional<EduroamLoginTicket> b = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(a.isPresent(), b.isPresent());
        }
        assertNull(row.get());
    }
}
