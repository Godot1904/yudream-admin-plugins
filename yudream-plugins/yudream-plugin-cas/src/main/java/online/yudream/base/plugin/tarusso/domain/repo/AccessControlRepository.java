package online.yudream.base.plugin.tarusso.domain.repo;

import online.yudream.base.plugin.tarusso.domain.aggregate.AccessControl;

public interface AccessControlRepository {

    AccessControl get();

    AccessControl save(AccessControl control);
}
