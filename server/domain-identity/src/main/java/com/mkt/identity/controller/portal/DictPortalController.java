package com.mkt.identity.controller.portal;

import com.mkt.identity.application.DictAppService;
import com.mkt.identity.convert.DictConvert;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.identity.response.DictPortalEntry;
import com.mkt.kernel.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/dict")
@Tag(name = "dict")
public class DictPortalController {

    private final DictAppService appService;

    public DictPortalController(DictAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/{typeCode}")
    @Operation(summary = "门户字典项", description = "登录态；类型停用返回空列表")
    public Result<List<DictPortalEntry>> entries(@PathVariable String typeCode) {
        List<DictEntryOption> options = appService.listEnabledEntries(typeCode);
        List<DictPortalEntry> views = new ArrayList<>(options.size());
        for (DictEntryOption option : options) {
            views.add(DictConvert.toPortal(option));
        }
        return Result.ok(views);
    }
}
