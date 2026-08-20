package com.mkt.identity.convert;

import com.mkt.identity.entity.DictEntryEntity;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.identity.response.DictPortalEntry;
import com.mkt.identity.response.DictTypeView;

public final class DictConvert {

    private DictConvert() {}

    public static DictTypeView toView(DictTypeEntity entity) {
        return new DictTypeView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRemark(),
                IdentityTime.toInstant(entity.getCreatedAt()));
    }

    public static DictEntryOption toOption(DictEntryEntity entity) {
        int sort = entity.getSort() == null ? 0 : entity.getSort();
        return new DictEntryOption(entity.getLabel(), entity.getValue(), sort);
    }

    public static DictPortalEntry toPortal(DictEntryOption option) {
        return new DictPortalEntry(option.label(), option.value());
    }
}
