# 奖品模块 API

最后更新：2026-05-29

## Admin 奖品管理

### 奖品列表

```
GET /api/admin/prize
```

返回：`Result<IPage<PrizeVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 创建奖品

```
POST /api/admin/prize
```

返回：`Result<PrizeVO>`

### 更新奖品

```
PUT /api/admin/prize/{id}
```

返回：`Result<PrizeVO>`

### 启用/禁用

```
POST /api/admin/prize/{id}/toggle
```

返回：`Result<Void>`

### 奖品详情

```
GET /api/admin/prize/{id}
```

返回：`Result<PrizeVO>`

### 奖品记录（按奖品）

```
GET /api/admin/prize/{id}/records
```

返回：`Result<IPage<PrizeRecordVO>>`

### 全局奖品记录

```
GET /api/admin/prize/records
```

返回：`Result<IPage<PrizeRecordVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| userId | Long | 否 | 用户 ID |
| prizeId | Long | 否 | 奖品 ID |
| status | String | 否 | 记录状态 |

### 补发

```
POST /api/admin/prize/records/{id}/reissue
```

返回：`Result<Void>`

## C 端奖品

### 我的奖品记录

```
GET /api/client/prize/records
```

返回：`Result<IPage<PrizeRecordVO>>`

### 领取奖品

```
POST /api/client/prize/{recordId}/claim
```

返回：`Result<Void>`

### 记录详情

```
GET /api/client/prize/{recordId}
```

返回：`Result<PrizeRecordVO>`
