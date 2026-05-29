package com.marketing.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.system.domain.entity.AdminUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {
}
