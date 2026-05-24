package com.marketing.task.prize.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.prize.domain.entity.Prize;
import com.marketing.task.prize.domain.entity.PrizeRecord;
import com.marketing.task.prize.mapper.PrizeMapper;
import com.marketing.task.prize.mapper.PrizeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/prize")
@RequiredArgsConstructor
public class AdminPrizeController {
    private final PrizeMapper prizeMapper;
    private final PrizeRecordMapper recordMapper;

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
}
