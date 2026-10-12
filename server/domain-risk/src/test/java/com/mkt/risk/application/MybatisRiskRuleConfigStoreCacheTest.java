package com.mkt.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mkt.contract.RiskAction;
import com.mkt.risk.domain.RuleSpec;
import com.mkt.risk.entity.RiskRuleConfigEntity;
import com.mkt.risk.mapper.RiskRuleConfigMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class MybatisRiskRuleConfigStoreCacheTest {

    @Test
    void listAll_singleflightCache_hitsUntilInvalidate() {
        RiskRuleConfigMapper mapper = mock(RiskRuleConfigMapper.class);
        RiskRuleConfigEntity row = new RiskRuleConfigEntity();
        row.setId(1L);
        row.setRuleCode("R-a");
        row.setEnabled(1);
        row.setThreshold(10L);
        row.setWindowSeconds(60L);
        row.setAction(RiskAction.REJECT.name());
        when(mapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(row));
        when(mapper.updateById(any(RiskRuleConfigEntity.class))).thenReturn(1);

        MybatisRiskRuleConfigStore store = new MybatisRiskRuleConfigStore(mapper);

        List<RuleSpec> a = store.listAll();
        List<RuleSpec> b = store.listAll();
        assertThat(a).hasSize(1);
        assertThat(b).isSameAs(a);
        verify(mapper, times(1)).selectList(any(LambdaQueryWrapper.class));

        store.update(row);
        List<RuleSpec> c = store.listAll();
        assertThat(c).hasSize(1);
        assertThat(c).isNotSameAs(a);
        verify(mapper, times(2)).selectList(any(LambdaQueryWrapper.class));
    }
}
