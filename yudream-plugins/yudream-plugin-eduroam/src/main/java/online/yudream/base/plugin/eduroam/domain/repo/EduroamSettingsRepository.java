package online.yudream.base.plugin.eduroam.domain.repo;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;

import java.util.Optional;

/** 渠道配置仓储：单文档（id = settings）。 */
public interface EduroamSettingsRepository {

    /** 尚未落库时返回空，供首次启用播种默认配置。 */
    Optional<EduroamSettings> find();

    EduroamSettings save(EduroamSettings settings);

    default EduroamSettings get() {
        return find().orElseGet(EduroamSettings::defaults);
    }
}
