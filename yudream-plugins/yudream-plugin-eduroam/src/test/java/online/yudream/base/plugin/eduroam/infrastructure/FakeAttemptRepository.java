package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAttempt;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAttemptRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 测试用内存审计仓储。 */
public class FakeAttemptRepository implements EduroamAttemptRepository {

    private final Map<String, EduroamAttempt> attempts = new LinkedHashMap<>();

    @Override
    public EduroamAttempt save(EduroamAttempt attempt) {
        attempts.put(attempt.id(), attempt);
        return attempt;
    }

    @Override
    public List<EduroamAttempt> listAll() {
        return new ArrayList<>(attempts.values());
    }

    @Override
    public void delete(String id) {
        attempts.remove(id);
    }

    @Override
    public long count() {
        return attempts.size();
    }
}
