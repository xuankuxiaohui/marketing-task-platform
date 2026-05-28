package com.marketing.task.signin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.marketing.task.signin.domain.entity.SignInRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SignInRecordMapper extends BaseMapper<SignInRecord> {
}
