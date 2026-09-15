package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamSettingsRepository;

import java.util.Optional;

/** 测试用配置仓储：未写入时返回空，模拟真实部署里「首次启用尚未落库」。 */
public class FakeSettingsRepository implements EduroamSettingsRepository {

    private EduroamSettings settings;

    @Override
    public Optional<EduroamSettings> find() {
        return Optional.ofNullable(settings);
    }

    @Override
    public EduroamSettings save(EduroamSettings settings) {
        this.settings = settings;
        return settings;
    }
}
