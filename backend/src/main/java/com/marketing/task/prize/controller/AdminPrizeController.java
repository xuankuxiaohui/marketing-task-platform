package com.marketing.task.prize.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.mapper.PrizeMapper;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import com.marketing.task.prize.service.ClaimService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/prize")
@RequiredArgsConstructor
public class AdminPrizeController {
    private final PrizeMapper prizeMapper;
    private final PrizeRecordMapper recordMapper;
    private final ClaimService claimService;

    @GetMapping
    public Result<Page<Prize>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Prize> result = prizeMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<Prize>().orderByDesc(Prize::getId));
        return Result.ok(result);
    }

    @PostMapping
    public Result<Prize> create(@RequestBody Prize prize) {
        prizeMapper.insert(prize);
        return Result.ok(prize);
    }

    @PutMapping("/{id}")
    public Result<Prize> update(@PathVariable Long id, @RequestBody Prize prize) {
        prize.setId(id);
        prizeMapper.updateById(prize);
        return Result.ok(prize);
    }

    @PostMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        Prize prize = prizeMapper.selectById(id);
        if (prize != null) {
            prize.setEnabled(!(prize.getEnabled() != null && prize.getEnabled()));
            prizeMapper.updateById(prize);
        }
        return Result.ok(null);
    }

    @GetMapping("/{id}")
    public Result<Prize> detail(@PathVariable Long id) {
        return Result.ok(prizeMapper.selectById(id));
    }

    @GetMapping("/{id}/records")
    public Result<List<PrizeRecord>> records(@PathVariable Long id) {
        List<PrizeRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<PrizeRecord>()
                        .eq(PrizeRecord::getPrizeId, id)
                        .orderByDesc(PrizeRecord::getWonAt));
        return Result.ok(records);
    }

    @GetMapping("/records")
    public Result<Page<PrizeRecord>> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Long prizeId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LambdaQueryWrapper<PrizeRecord> qw = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isBlank()) qw.eq(PrizeRecord::getUserId, userId);
        if (prizeId != null) qw.eq(PrizeRecord::getPrizeId, prizeId);
        if (status != null && !status.isBlank()) qw.eq(PrizeRecord::getStatus, status);
        if (startDate != null && !startDate.isBlank())
            qw.ge(PrizeRecord::getWonAt, LocalDate.parse(startDate).atStartOfDay());
        if (endDate != null && !endDate.isBlank())
            qw.le(PrizeRecord::getWonAt, LocalDate.parse(endDate).atTime(LocalTime.MAX));
        qw.orderByDesc(PrizeRecord::getWonAt);
        return Result.ok(recordMapper.selectPage(new Page<>(page, size), qw));
    }

    @PostMapping("/records/{id}/reissue")
    public Result<String> reissue(@PathVariable Long id) {
        PrizeRecord record = recordMapper.selectById(id);
        if (record == null) return Result.fail(400, "奖品记录不存在");
        String s = record.getStatus();
        if ("GRANTED".equals(s)) return Result.fail(400, "该记录已发放成功，无需补发");
        if ("CLAIMING".equals(s)) return Result.fail(400, "该记录正在领取中，请稍后再试");
        record.setStatus("WON");
        record.setRetryCount(0);
        record.setErrorMessage(null);
        record.setExpireTime(LocalDateTime.now().plusDays(7));
        recordMapper.updateById(record);
        claimService.claim(id);
        return Result.ok("补发已触发");
    }
}
