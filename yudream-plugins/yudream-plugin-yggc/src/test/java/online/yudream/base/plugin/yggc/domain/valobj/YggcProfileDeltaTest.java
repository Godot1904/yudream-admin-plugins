package online.yudream.base.plugin.yggc.domain.valobj;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link YggcProfileDelta} 的差异计算：新增 / 改名 / 删除三类，以及无差异的稳定情形。 */
class YggcProfileDeltaTest {

    private static Map<String, String> profiles(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void firstRunTreatsEveryLocalProfileAsNew() {
        YggcProfileDelta delta = YggcProfileDelta.between(Map.of(), profiles("uuid-1", "Steve", "uuid-2", "Alex"));

        assertEquals(2, delta.added().size());
        assertEquals("uuid-1", delta.added().get(0).uuid());
        assertEquals("Steve", delta.added().get(0).name());
        assertTrue(delta.renamed().isEmpty());
        assertTrue(delta.removed().isEmpty());
        assertFalse(delta.empty());
    }

    @Test
    void identicalSetsProduceNoWork() {
        Map<String, String> snapshot = profiles("uuid-1", "Steve", "uuid-2", "Alex");

        YggcProfileDelta delta = YggcProfileDelta.between(snapshot, profiles("uuid-2", "Alex", "uuid-1", "Steve"));

        assertTrue(delta.empty());
        assertEquals(0, delta.size());
    }

    @Test
    void renamedProfileKeepsItsUuidAndReportsTheOldName() {
        YggcProfileDelta delta = YggcProfileDelta.between(
                profiles("uuid-1", "Steve"), profiles("uuid-1", "SteveTheSecond"));

        assertTrue(delta.added().isEmpty());
        assertTrue(delta.removed().isEmpty());
        assertEquals(1, delta.renamed().size());
        assertEquals("uuid-1", delta.renamed().get(0).uuid());
        assertEquals("Steve", delta.renamed().get(0).from());
        assertEquals("SteveTheSecond", delta.renamed().get(0).to());
    }

    @Test
    void profilesMissingLocallyAreScheduledForRemoval() {
        YggcProfileDelta delta = YggcProfileDelta.between(
                profiles("uuid-1", "Steve", "uuid-2", "Alex"), profiles("uuid-1", "Steve"));

        assertEquals(java.util.List.of("uuid-2"), delta.removed());
        assertTrue(delta.added().isEmpty());
        assertTrue(delta.renamed().isEmpty());
        assertEquals(1, delta.size());
    }

    @Test
    void mixedChangesAreReportedTogether() {
        YggcProfileDelta delta = YggcProfileDelta.between(
                profiles("uuid-1", "Steve", "uuid-2", "Alex", "uuid-3", "Herobrine"),
                profiles("uuid-1", "Steve", "uuid-2", "Alexandra", "uuid-4", "Notch"));

        assertEquals(1, delta.added().size());
        assertEquals("uuid-4", delta.added().get(0).uuid());
        assertEquals(1, delta.renamed().size());
        assertEquals("uuid-2", delta.renamed().get(0).uuid());
        assertEquals(java.util.List.of("uuid-3"), delta.removed());
        assertEquals(3, delta.size());
    }

    @Test
    void nullInputsAreTreatedAsEmptySets() {
        YggcProfileDelta delta = YggcProfileDelta.between(null, null);

        assertTrue(delta.empty());
    }
}
