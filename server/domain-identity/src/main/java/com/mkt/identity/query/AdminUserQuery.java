package com.mkt.identity.query;

import com.mkt.kernel.PageQuery;

public record AdminUserQuery(String username, String nickname, String status, Long roleId, PageQuery page) {}
