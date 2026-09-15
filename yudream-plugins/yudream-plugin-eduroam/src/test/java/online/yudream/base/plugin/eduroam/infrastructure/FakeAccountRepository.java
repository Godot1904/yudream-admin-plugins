package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAccount;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAccountRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用内存仓储：语义与文档仓储一致（邮箱为主键）。 */
public class FakeAccountRepository implements EduroamAccountRepository {

    private final Map<String, EduroamAccount> records = new LinkedHashMap<>();

    @Override
    public EduroamAccount save(EduroamAccount account) {
        records.put(account.id(), account);
        return account;
    }

    @Override
    public Optional<EduroamAccount> findById(String id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public List<EduroamAccount> listAll() {
        return List.copyOf(records.values());
    }

    @Override
    public void delete(String id) {
        records.remove(id);
    }

    @Override
    public long count() {
        return records.size();
    }
}
