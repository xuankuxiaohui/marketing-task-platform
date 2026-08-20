package com.mkt.reward.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mkt.reward.entity.PntTransactionEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PntTransactionMapper extends BaseMapper<PntTransactionEntity> {

    long countExpireByBizId(@Param("bizId") String bizId);

    List<PntTransactionEntity> listExpireDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    long selectCountFiltered(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    List<PntTransactionEntity> selectPageFiltered(
            @Param("userId") Long userId,
            @Param("type") String type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("offset") long offset,
            @Param("limit") int limit);
}
