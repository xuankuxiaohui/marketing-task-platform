package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.mapper.AdMaterialMapper;
import com.mkt.ad.mapper.AdPositionMapper;
import com.mkt.ad.mapper.AdPositionMaterialMapper;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/** Store adapters: duplicate-key translation plus mapper delegation. No database. */
class MybatisAdStoreTest {

    @Test
    void positionInsertTranslatesDuplicateAndDelegatesReads() {
        AdPositionMapper mapper = mock(AdPositionMapper.class);
        MybatisAdPositionStore store = new MybatisAdPositionStore(mapper);
        AdPositionEntity entity = new AdPositionEntity();
        entity.setCode("home_banner");

        RuntimeException duplicateMessage = new RuntimeException("Duplicate entry 'home_banner' for key 'uk_code'");
        doThrow(duplicateMessage).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessage("uk_code")
                .hasCause(duplicateMessage);

        RuntimeException sqlState =
                new RuntimeException("wrapped", new SQLIntegrityConstraintViolationException("1062"));
        doThrow(sqlState).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity)).isInstanceOf(DuplicateKeyException.class).hasCause(sqlState);

        DuplicateKeyException already = new DuplicateKeyException("uk_code");
        doThrow(already).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity)).isSameAs(already);

        IllegalStateException other = new IllegalStateException("deadlock");
        doThrow(other).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity)).isSameAs(other);

        doReturn(1).when(mapper).insert(entity);
        when(mapper.selectById(4L)).thenReturn(entity);
        when(mapper.selectByCode("home_banner")).thenReturn(entity);
        when(mapper.updateById(entity)).thenReturn(1);
        when(mapper.selectCountByQuery("home", "IMAGE", "ENABLED")).thenReturn(2L);
        when(mapper.selectByQuery("home", "IMAGE", "ENABLED", 20L, 10)).thenReturn(List.of(entity));
        assertThat(store.insert(entity)).isEqualTo(1);
        assertThat(store.getById(4L)).isSameAs(entity);
        assertThat(store.getByCode("home_banner")).isSameAs(entity);
        assertThat(store.update(entity)).isEqualTo(1);
        assertThat(store.countByQuery("home", "IMAGE", "ENABLED")).isEqualTo(2L);
        assertThat(store.listByQuery("home", "IMAGE", "ENABLED", 20L, 10)).containsExactly(entity);
    }

    @Test
    void placementInsertTranslatesDuplicateAndDelegates() {
        AdPositionMaterialMapper mapper = mock(AdPositionMaterialMapper.class);
        MybatisAdPlacementStore store = new MybatisAdPlacementStore(mapper);
        AdPositionMaterialEntity entity = new AdPositionMaterialEntity();
        entity.setPositionId(3L);
        entity.setMaterialId(9L);

        RuntimeException duplicateMessage = new RuntimeException("Duplicate entry for key 'uk_position_material'");
        doThrow(duplicateMessage).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessage("uk_position_material")
                .hasCause(duplicateMessage);

        DuplicateKeyException already = new DuplicateKeyException("uk_position_material");
        doThrow(already).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity)).isSameAs(already);

        IllegalStateException other = new IllegalStateException("timeout");
        doThrow(other).when(mapper).insert(entity);
        assertThatThrownBy(() -> store.insert(entity)).isSameAs(other);

        doReturn(1).when(mapper).insert(entity);
        when(mapper.selectByPositionId(3L)).thenReturn(List.of(entity));
        when(mapper.selectByPositionAndMaterial(3L, 9L)).thenReturn(entity);
        when(mapper.updateById(entity)).thenReturn(1);
        when(mapper.deleteByPositionAndMaterial(3L, 9L)).thenReturn(1);
        when(mapper.countByMaterialId(9L)).thenReturn(2);
        assertThat(store.insert(entity)).isEqualTo(1);
        assertThat(store.listByPositionId(3L)).containsExactly(entity);
        assertThat(store.getByPositionAndMaterial(3L, 9L)).isSameAs(entity);
        assertThat(store.update(entity)).isEqualTo(1);
        assertThat(store.delete(3L, 9L)).isEqualTo(1);
        assertThat(store.countByMaterialId(9L)).isEqualTo(2);
    }

    @Test
    void materialListByIdsSkipsEmptyAndDelegatesOtherwise() {
        AdMaterialMapper mapper = mock(AdMaterialMapper.class);
        MybatisAdMaterialStore store = new MybatisAdMaterialStore(mapper);
        assertThat(store.listByIds(null)).isEmpty();
        assertThat(store.listByIds(List.of())).isEmpty();
        verify(mapper, never()).selectByIds(any());

        AdMaterialEntity entity = new AdMaterialEntity();
        entity.setId(9L);
        when(mapper.selectById(9L)).thenReturn(entity);
        when(mapper.insert(entity)).thenReturn(1);
        when(mapper.updateById(entity)).thenReturn(1);
        when(mapper.selectCountByQuery("国庆", "ENABLED")).thenReturn(1L);
        when(mapper.selectByQuery("国庆", "ENABLED", 0L, 20)).thenReturn(List.of(entity));
        when(mapper.selectByIds(List.of(9L))).thenReturn(List.of(entity));
        assertThat(store.getById(9L)).isSameAs(entity);
        assertThat(store.insert(entity)).isEqualTo(1);
        assertThat(store.update(entity)).isEqualTo(1);
        assertThat(store.countByQuery("国庆", "ENABLED")).isEqualTo(1L);
        assertThat(store.listByQuery("国庆", "ENABLED", 0L, 20)).containsExactly(entity);
        assertThat(store.listByIds(List.of(9L))).containsExactly(entity);
    }
}
