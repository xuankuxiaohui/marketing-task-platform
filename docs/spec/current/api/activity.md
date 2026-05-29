# 活动模块 API

最后更新：2026-05-29

## Admin 活动管理

### 活动列表

```
GET /api/admin/activities
```

返回：`Result<IPage<ActivityVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |
| status | String | 否 | 活动状态 |

### 活动详情

```
GET /api/admin/activities/{id}
```

返回：`Result<ActivityVO>`

### 创建活动

```
POST /api/admin/activities
```

返回：`Result<ActivityVO>`

Body：`ActivityCreateRequest`

### 更新活动

```
PUT /api/admin/activities/{id}
```

返回：`Result<ActivityVO>`

Body：`ActivityUpdateRequest`

### 删除活动

```
DELETE /api/admin/activities/{id}
```

返回：`Result<Void>`

### 获取展示规则

```
GET /api/admin/activities/{id}/display-rule
```

返回：`Result<DisplayRuleVO>`

### 更新展示规则

```
PUT /api/admin/activities/{id}/display-rule
```

返回：`Result<DisplayRuleVO>`

Body：`DisplayRuleUpdateRequest`

### 发布

```
POST /api/admin/activities/{id}/publish
```

返回：`Result<Void>`

### 下线

```
POST /api/admin/activities/{id}/offline
```

返回：`Result<Void>`

### 退回草稿

```
POST /api/admin/activities/{id}/back-to-draft
```

返回：`Result<Void>`

### 子模块查询

```
GET /api/admin/activities/{id}/sub-modules
```

返回：`Result<List<SubModuleVO>>`

## C 端活动

### 活动列表

```
GET /api/client/activities
```

返回：`Result<IPage<ActivityVO>>`

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | Integer | 否 | 页码，默认 1 |
| size | Integer | 否 | 每页条数，默认 10 |

### 活动详情

```
GET /api/client/activities/{id}
```

返回：`Result<ActivityVO>`

### 展示规则

```
GET /api/client/activities/{id}/display-rule
```

返回：`Result<DisplayRuleVO>`

### 参与活动

```
POST /api/client/activities/{id}/participate
```

返回：`Result<ParticipateResultVO>`
