package com.marketing.task.service.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.ListData;
import com.marketing.task.mapper.ListDataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrayServiceTest {

    @Mock
    private ListDataMapper listDataMapper;

    private GrayService grayService;

    @BeforeEach
    void setUp() {
        grayService = new GrayService(listDataMapper);
    }

    @Test
    void isInGray_none_shouldReturnTrue() {
        assertTrue(grayService.isInGray("u1", 1L, "NONE", null));
    }

    @Test
    void isInGray_percentage_shouldReturnTrue() {
        String grayConfig = "{\"percent\":100}";
        assertTrue(grayService.isInGray("u1", 1L, "PERCENTAGE", grayConfig));
    }

    @Test
    void isInGray_percentage_shouldReturnFalse() {
        String grayConfig = "{\"percent\":0}";
        assertFalse(grayService.isInGray("u1", 1L, "PERCENTAGE", grayConfig));
    }

    @Test
    void isInGray_ab_shouldAssignToGroup() {
        String grayConfig = "{\"groups\":[{\"name\":\"A\",\"percent\":100},{\"name\":\"B\",\"percent\":0}]}";
        String group = grayService.getABGroup("u1", 1L, grayConfig);
        assertEquals("A", group);
    }

    @Test
    void getABGroup_shouldBeDeterministic() {
        String grayConfig = "{\"groups\":[{\"name\":\"A\",\"percent\":50},{\"name\":\"B\",\"percent\":50}]}";
        String g1 = grayService.getABGroup("u1", 1L, grayConfig);
        String g2 = grayService.getABGroup("u1", 1L, grayConfig);
        assertEquals(g1, g2);
    }

    @Test
    void isInGray_crowd_shouldReturnTrueWhenInList() {
        String grayConfig = "{\"crowdIds\":[1, 2]}";
        when(listDataMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);
        assertTrue(grayService.isInGray("u1", 1L, "CROWD", grayConfig));
    }

    @Test
    void isInGray_crowd_shouldReturnFalseWhenNotInList() {
        String grayConfig = "{\"crowdIds\":[3]}";
        when(listDataMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        assertFalse(grayService.isInGray("u1", 1L, "CROWD", grayConfig));
    }
}
