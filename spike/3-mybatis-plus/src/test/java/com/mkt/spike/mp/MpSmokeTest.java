package com.mkt.spike.mp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.mkt.spike.mp.entity.SpikeDemo;
import com.mkt.spike.mp.mapper.SpikeDemoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MpSmokeTest {

    @Autowired
    SpikeDemoMapper mapper;
    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void createTable() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS spike_mp_demo (
                  id BIGINT PRIMARY KEY,
                  name VARCHAR(64) NOT NULL,
                  version INT NOT NULL DEFAULT 0
                )
                """);
        jdbc.execute("DELETE FROM spike_mp_demo");
    }

    @AfterEach
    void dropTable() {
        jdbc.execute("DROP TABLE IF EXISTS spike_mp_demo");
    }

    @Test
    void crudAndAssignIdAndOptimisticLock() {
        SpikeDemo row = new SpikeDemo();
        row.setName("alpha");
        row.setVersion(0);
        assertEquals(1, mapper.insert(row));
        assertNotNull(row.getId());
        assertTrue(row.getId() > 1_000_000_000_000L);

        SpikeDemo loaded = mapper.selectOne(new LambdaQueryWrapper<SpikeDemo>().eq(SpikeDemo::getName, "alpha"));
        assertEquals("alpha", loaded.getName());

        int first = mapper.update(null, new LambdaUpdateWrapper<SpikeDemo>()
                .eq(SpikeDemo::getId, loaded.getId())
                .eq(SpikeDemo::getVersion, loaded.getVersion())
                .set(SpikeDemo::getName, "beta")
                .setSql("version = version + 1"));
        int second = mapper.update(null, new LambdaUpdateWrapper<SpikeDemo>()
                .eq(SpikeDemo::getId, loaded.getId())
                .eq(SpikeDemo::getVersion, loaded.getVersion())
                .set(SpikeDemo::getName, "gamma")
                .setSql("version = version + 1"));
        assertEquals(1, first);
        assertEquals(0, second);
    }
}
