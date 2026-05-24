package com.marketing.task.service.filter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.domain.entity.ListData;
import com.marketing.task.mapper.ListDataMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ListDataService {
    private final ListDataMapper listDataMapper;

    public boolean isInList(String listType, String listKey, String userId) {
        return listDataMapper.exists(new LambdaQueryWrapper<ListData>()
                .eq(ListData::getListType, listType)
                .eq(ListData::getListKey, listKey)
                .eq(ListData::getUserId, userId));
    }
}
