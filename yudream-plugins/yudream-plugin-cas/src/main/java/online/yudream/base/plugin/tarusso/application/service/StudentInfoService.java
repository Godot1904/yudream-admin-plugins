package online.yudream.base.plugin.tarusso.application.service;

import online.yudream.base.plugin.tarusso.domain.aggregate.StudentMapping;
import online.yudream.base.plugin.tarusso.domain.aggregate.StudentProfile;
import online.yudream.base.plugin.tarusso.domain.enumerate.SsoProtocol;
import online.yudream.base.plugin.tarusso.domain.repo.StudentMappingRepository;
import online.yudream.base.plugin.tarusso.domain.repo.StudentProfileRepository;
import online.yudream.base.plugin.tarusso.domain.service.SsoProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentProfileDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.support.JsonSupport;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 学生信息映射：CAS/OIDC 登录交换时把认证属性 upsert 成学生档案（以学工号为主键）。
 * 记录动作是 best-effort——档案写入失败不影响登录主流程。
 */
public final class StudentInfoService {

    /** 常见属性键名，学校端字段未知时按序探测。 */
    private static final List<String> NAME_KEYS = List.of("name", "cn", "displayName");
    private static final List<String> DEPT_KEYS = List.of("department", "dept", "orgName", "org", "org_dn", "ou", "college", "school", "studentDepartment");
    private static final List<String> MAJOR_KEYS = List.of("major", "studentMajor", "profession", "subject", "zhuanye");
    private static final List<String> GRADE_KEYS = List.of("grade", "studentGrade", "entranceYear", "nianji", "year");
    private static final List<String> CLASS_KEYS = List.of("className", "class", "clazz", "banji", "studentClass");
    private static final List<String> EMAIL_KEYS = List.of("email", "mail");
    private static final List<String> PHONE_KEYS = List.of("phone", "mobile", "telephone");

    private final StudentMappingRepository mappings;
    private final StudentProfileDocumentRepository profiles;
    /** 绑定查询可为空（宿主未提供用户服务时），只影响管理端「绑定账号」信息。 */
    private final BindingQueryService bindings;

    public StudentInfoService(StudentMappingRepository mappings, StudentProfileRepository profiles) {
        this(mappings, profiles, null);
    }

    public StudentInfoService(
            StudentMappingRepository mappings,
            StudentProfileRepository profiles,
            BindingQueryService bindings
    ) {
        this.mappings = mappings;
        // 搜索能力是文档仓库实现细节，接口不暴露全量扫描
        if (!(profiles instanceof StudentProfileDocumentRepository documentRepository)) {
            throw new IllegalArgumentException("学生档案仓库必须基于文档存储实现");
        }
        this.profiles = documentRepository;
        this.bindings = bindings;
    }

    public StudentMapping mapping() {
        return mappings.get();
    }

    public StudentMapping saveMapping(StudentMapping mapping) {
        if (mapping == null) {
            throw new IllegalArgumentException("映射配置不能为空");
        }
        return mappings.save(mapping);
    }

    /** 登录交换时调用：upsert 学生档案。任何异常都不应中断登录。 */
    public void record(SsoProtocolClient.ExternalIdentity identity, SsoProtocol protocol) {
        try {
            StudentMapping mapping = mappings.get();
            Map<String, String> attributes = identity.attributes();
            Optional<StudentProfile> existing = profiles.find(identity.socialUid());
            long now = System.currentTimeMillis();
            StudentProfile profile = new StudentProfile(
                    identity.socialUid(),
                    pick(mapping.nameKey(), NAME_KEYS, attributes, existing.map(StudentProfile::name).orElse(""), identity.nickname(), identity.socialUid()),
                    pick(mapping.deptKey(), DEPT_KEYS, attributes, existing.map(StudentProfile::dept).orElse("")),
                    pick(mapping.majorKey(), MAJOR_KEYS, attributes, existing.map(StudentProfile::major).orElse("")),
                    pick(mapping.gradeKey(), GRADE_KEYS, attributes, existing.map(StudentProfile::grade).orElse("")),
                    pick(mapping.classKey(), CLASS_KEYS, attributes, existing.map(StudentProfile::className).orElse("")),
                    pick("", EMAIL_KEYS, attributes, existing.map(StudentProfile::email).orElse("")),
                    pick("", PHONE_KEYS, attributes, existing.map(StudentProfile::phone).orElse("")),
                    protocol == null ? "" : protocol.name(),
                    attributes.isEmpty() ? existing.map(StudentProfile::rawAttributes).orElse("") : JsonSupport.write(attributes),
                    existing.map(StudentProfile::firstSeenAt).orElse(now),
                    now,
                    existing.map(p -> p.loginCount() + 1L).orElse(1L)
            );
            profiles.save(profile);
        } catch (RuntimeException e) {
            System.err.println("[cas] 学生信息记录失败（不影响登录）: " + e.getMessage());
        }
    }

    public Map<String, Object> page(int page, int size, String keyword) {
        List<StudentProfile> items;
        long total;
        if (keyword != null && !keyword.isBlank()) {
            items = profiles.search(keyword);
            total = items.size();
            int fromIndex = Math.min((Math.max(page, 1) - 1) * Math.max(size, 1), items.size());
            int toIndex = Math.min(fromIndex + Math.max(size, 1), items.size());
            items = items.subList(fromIndex, toIndex);
        } else {
            items = profiles.page(page, size);
            total = profiles.count();
        }
        return Map.of(
                "items", attachBindings(items.stream().map(StudentInfoService::toMap).toList()),
                "total", total,
                "page", Math.max(page, 1),
                "size", Math.max(size, 1)
        );
    }

    public Optional<Map<String, Object>> detail(String socialUid) {
        return profiles.find(socialUid).map(profile -> attachBinding(profile, toMap(profile)));
    }

    /**
     * 供已绑定用户的前端预填「学生档案」使用：按学工号返回最小字段集。
     * <p>只返回预填所需字段（姓名/班级/学院/专业/年级），不含邮箱、电话与原始属性，
     * 降低按学工号遍历查询的信息泄露面。</p>
     */
    public Optional<Map<String, Object>> prefill(String socialUid) {
        if (socialUid == null || socialUid.isBlank()) {
            return Optional.empty();
        }
        return profiles.find(socialUid.trim()).map(profile -> Map.<String, Object>of(
                "studentNo", profile.socialUid(),
                "studentName", profile.name(),
                "className", profile.className(),
                "college", profile.dept(),
                "major", profile.major(),
                "grade", profile.grade()
        ));
    }

    private static Map<String, Object> toMap(StudentProfile profile) {
        return profile.toMap();
    }

    private List<Map<String, Object>> attachBindings(List<Map<String, Object>> items) {
        if (bindings == null) {
            return items;
        }
        for (Map<String, Object> item : items) {
            item.put("binding", bindings.lookup(platformType(item.get("protocol")), stringOf(item.get("socialUid"))));
        }
        return items;
    }

    private Map<String, Object> attachBinding(StudentProfile profile, Map<String, Object> view) {
        if (bindings != null) {
            view.put("binding", bindings.lookup(platformTypeOf(profile), profile.socialUid()));
        }
        return view;
    }

    /**
     * 绑定查询的 platformType = 宿主登记该绑定时使用的协议类型码。
     * 档案按登录当时的协议存储（CAS/OIDC），因此优先用档案自身的协议，避免管理员中途切换协议后查不到历史绑定。
     */
    private static String platformTypeOf(StudentProfile profile) {
        return platformType(profile.protocol());
    }

    private static String platformType(Object protocol) {
        String value = stringOf(protocol);
        return value.isBlank() ? SsoProtocol.CAS.typeCode() : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String stringOf(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 取属性值：显式键优先；否则按候选键名探测；都没有则回退旧值/兜底值。
     */
    private static String pick(String explicitKey, List<String> candidates, Map<String, String> attributes, String... fallbacks) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            String value = attributes.get(explicitKey.trim());
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        for (String candidate : candidates) {
            String value = attributes.get(candidate);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        for (String fallback : fallbacks) {
            if (fallback != null && !fallback.isBlank()) {
                return fallback.trim();
            }
        }
        return "";
    }
}
