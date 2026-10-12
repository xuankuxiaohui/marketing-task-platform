package com.mkt.risk.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.mkt.contract.RiskAction;
import com.mkt.risk.domain.RiskRuleCode;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import com.mkt.risk.mapper.RiskRuleConfigMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisRiskRuleConfigStore implements RiskRuleConfigStore {

    /**
     * Architect 2026-10-12 after #194: grant path {@code listAll} under risk-on was hot
     * (Δ≈213 cold-symmetric). Caffeine TTL 3s (2–5s band) + {@code get(key, loader)}
     * singleflight coalesces concurrent rule loads. Admin {@link #update} invalidates.
     * Caps locked (禁降Δ; Redisson not bumped; 1536M not raised).
     */
    private static final Duration LIST_ALL_TTL = Duration.ofSeconds(3);

    private static final String LIST_ALL_KEY = "all";

    private final RiskRuleConfigMapper mapper;

    private final Cache<String, List<RuleSpec>> listAllCache =
            Caffeine.newBuilder().expireAfterWrite(LIST_ALL_TTL).maximumSize(1).build();

    public MybatisRiskRuleConfigStore(RiskRuleConfigMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RuleSpec> listAll() {
        return listAllCache.get(LIST_ALL_KEY, k -> loadAll());
    }

    private List<RuleSpec> loadAll() {
        List<RiskRuleConfigEntity> rows = listRows();
        List<RuleSpec> specs = new ArrayList<>(rows.size());
        for (RiskRuleConfigEntity row : rows) {
            specs.add(toSpec(row));
        }
        return List.copyOf(specs);
    }

    @Override
    public List<RiskRuleConfigEntity> listRows() {
        return mapper.selectList(new LambdaQueryWrapper<RiskRuleConfigEntity>().orderByAsc(RiskRuleConfigEntity::getId));
    }

    @Override
    public RiskRuleConfigEntity getByCode(String ruleCode) {
        return mapper.selectOne(
                new LambdaQueryWrapper<RiskRuleConfigEntity>().eq(RiskRuleConfigEntity::getRuleCode, ruleCode));
    }

    @Override
    public int update(RiskRuleConfigEntity entity) {
        int n = mapper.updateById(entity);
        listAllCache.invalidate(LIST_ALL_KEY);
        return n;
    }

    private static RuleSpec toSpec(RiskRuleConfigEntity row) {
        return new RuleSpec(
                RiskRuleCode.fromCode(row.getRuleCode()),
                row.enabledFlag(),
                row.getThreshold(),
                row.getWindowSeconds(),
                RiskAction.valueOf(row.getAction()));
    }
}
