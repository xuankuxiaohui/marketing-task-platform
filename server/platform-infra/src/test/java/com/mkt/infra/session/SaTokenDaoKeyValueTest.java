package com.mkt.infra.session;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.dao.SaTokenDao;
import com.mkt.infra.redis.MemoryKeyValueStore;
import org.junit.jupiter.api.Test;

class SaTokenDaoKeyValueTest {

    @Test
    void setGetTimeoutAndDelete() {
        SaTokenDao dao = new SaTokenDaoKeyValue(new MemoryKeyValueStore());
        dao.set("k", "v", 30);
        assertThat(dao.get("k")).isEqualTo("v");
        assertThat(dao.getTimeout("k")).isGreaterThan(0);
        dao.updateTimeout("k", 60);
        assertThat(dao.getTimeout("k")).isGreaterThan(0);
        dao.delete("k");
        assertThat(dao.get("k")).isNull();
        assertThat(dao.getTimeout("k")).isEqualTo(SaTokenDao.NOT_VALUE_EXPIRE);
    }

    @Test
    void searchDataMatchesPrefixAndKeyword() {
        SaTokenDao dao = new SaTokenDaoKeyValue(new MemoryKeyValueStore());
        dao.set("satoken:admin:token:aaa", "1", 30);
        dao.set("satoken:admin:token:bbb", "2", 30);
        dao.set("satoken:client:token:ccc", "3", 30);
        assertThat(dao.searchData("satoken:admin:token:", "", 0, -1, true))
                .containsExactlyInAnyOrder("satoken:admin:token:aaa", "satoken:admin:token:bbb");
        assertThat(dao.searchData("satoken:admin:token:", "bbb", 0, -1, true))
                .containsExactly("satoken:admin:token:bbb");
    }
}
