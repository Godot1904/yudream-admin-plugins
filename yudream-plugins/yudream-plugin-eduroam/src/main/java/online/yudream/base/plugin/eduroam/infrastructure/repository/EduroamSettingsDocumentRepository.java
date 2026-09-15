package online.yudream.base.plugin.eduroam.infrastructure.repository;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamSettingsRepository;
import online.yudream.base.plugin.spi.system.storage.PluginDocumentStore;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 渠道配置仓储：单文档，id 固定为 settings。 */
public class EduroamSettingsDocumentRepository implements EduroamSettingsRepository {

    private static final String COLLECTION = "eduroam-settings";
    private static final String DOCUMENT_ID = "settings";

    private final PluginDocumentStore documents;

    public EduroamSettingsDocumentRepository(PluginDocumentStore documents) {
        this.documents = documents;
    }

    @Override
    public Optional<EduroamSettings> find() {
        return documents.findById(COLLECTION, DOCUMENT_ID).map(EduroamSettings::from);
    }

    @Override
    public EduroamSettings save(EduroamSettings settings) {
        EduroamSettings safe = settings == null ? EduroamSettings.defaults() : settings;
        Map<String, Object> document = new LinkedHashMap<>(safe.toDocument());
        document.put("id", DOCUMENT_ID);
        return EduroamSettings.from(documents.save(COLLECTION, DOCUMENT_ID, document));
    }
}
