package online.yudream.base.plugin.eduroam.domain.repo;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAttempt;

import java.util.List;

/** 核验尝试审计仓储：只追加，不修改。 */
public interface EduroamAttemptRepository {

    EduroamAttempt save(EduroamAttempt attempt);

    List<EduroamAttempt> listAll();

    void delete(String id);

    long count();
}
