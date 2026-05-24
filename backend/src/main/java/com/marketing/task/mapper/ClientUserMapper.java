package com.marketing.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.domain.entity.ClientUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ClientUserMapper extends BaseMapper<ClientUser> {
}
