package com.mkt.kernel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageQueryTest {

    @Test
    void defaultsAndCapsPageSize() {
        assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(1, 20));
        assertThat(new PageQuery(0, 0)).isEqualTo(new PageQuery(1, 20));
        assertThat(new PageQuery(2, 150).pageSize()).isEqualTo(100);
        assertThat(new PageQuery(3, 10).offset()).isEqualTo(20L);
    }

    @Test
    void pageDataCopiesRecords() {
        PageData<String> page = new PageData<>(1L, List.of("a"));
        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.records()).containsExactly("a");
        assertThat(new PageData<>(0L, null).records()).isEmpty();
    }
}
