# 签到模块 API

最后更新：2026-05-29

## Admin 签到配置

### 配置列表

```
GET /api/admin/signin/configs
```

返回：`Result<IPage<SignInConfigVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 配置详情

```
GET /api/admin/signin/configs/{id}
```

返回：`Result<SignInConfigVO>`

### 创建配置

```
POST /api/admin/signin/configs
```

返回：`Result<SignInConfigVO>`

### 更新配置

```
PUT /api/admin/signin/configs/{id}
```

返回：`Result<SignInConfigVO>`

### 删除配置

```
DELETE /api/admin/signin/configs/{id}
```

返回：`Result<Void>`

### 发布

```
POST /api/admin/signin/configs/{id}/publish
```

返回：`Result<Void>`

### 下线

```
POST /api/admin/signin/configs/{id}/offline
```

返回：`Result<Void>`

### 统计

```
GET /api/admin/signin/configs/{id}/stats
```

返回：`Result<SignInStatsVO>`

### 签到记录

```
GET /api/admin/signin/configs/{id}/records
```

返回：`Result<IPage<SignInRecordVO>>`

## Admin 积分

### 积分流水查询

```
GET /api/admin/signin/points/transactions
```

返回：`Result<IPage<PointsTransactionVO>>`

### 积分手动发放

```
POST /api/admin/signin/points/grant
```

返回：`Result<Void>`

## C 端签到

### 可用签到配置

```
GET /api/client/signin/configs
```

返回：`Result<List<SignInConfigVO>>`

### 签到

```
POST /api/client/signin/{configId}/sign
```

返回：`Result<SignInResultVO>`

### 补签

```
POST /api/client/signin/{configId}/catch-up
```

返回：`Result<SignInResultVO>`

### 签到日历

```
GET /api/client/signin/{configId}/calendar
```

返回：`Result<List<CalendarDayVO>>`

### 签到状态

```
GET /api/client/signin/{configId}/status
```

返回：`Result<SignInStatusVO>`

## C 端积分

### 积分余额

```
GET /api/client/signin/points/balance
```

返回：`Result<PointsBalanceVO>`

### 积分流水

```
GET /api/client/signin/points/transactions
```

返回：`Result<IPage<PointsTransactionVO>>`
