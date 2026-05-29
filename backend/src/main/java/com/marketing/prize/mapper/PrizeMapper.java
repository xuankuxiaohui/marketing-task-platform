package com.marketing.prize.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.prize.domain.entity.Prize;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrizeMapper extends BaseMapper<Prize> {
}
