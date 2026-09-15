package online.yudream.base.plugin.eduroam.domain.repo;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAccount;

import java.util.List;
import java.util.Optional;

/** 登录过的 Eduroam 账号仓储：以归一化邮箱为主键，一个邮箱最多一条记录。 */
public interface EduroamAccountRepository {

    EduroamAccount save(EduroamAccount account);

    Optional<EduroamAccount> findById(String id);

    default Optional<EduroamAccount> findByEmail(String email) {
        return findById(EduroamAccount.idOf(email));
    }

    List<EduroamAccount> listAll();

    void delete(String id);

    long count();
}
