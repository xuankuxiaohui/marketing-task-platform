package com.marketing.signin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.signin.domain.entity.SignInConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SignInConfigMapper extends BaseMapper<SignInConfig> {
}
