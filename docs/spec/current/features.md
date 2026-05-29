# 系统功能清单

最后更新：2026-05-29

## 任务管理
- 任务 CRUD（聚合保存：步骤/过滤器/平台/分支配置）
- 任务状态机：DRAFT → SCHEDULED → PUBLISHED → OFFLINE
- 任务发布/下线/批量发布/批量下线
- 定时发布 + 取消定时
- 任务复制（支持自定义名称/code）
- 任务软删除 + 查看已删除任务
- 配置版本快照（发布时固化，实例按版本读取）
- 任务版本历史对比

## 步骤引擎
- 步骤类型：PASSIVE / CLICK / CALLBACK / PROGRESS / REWARD
- 步骤 CRUD + 拖拽排序 + code 唯一性校验
- 条件分支路由（QLExpress 表达式 + 优先级 + DAG 约束）
- 步骤平台特化配置（buttonText/jumpType/jumpTarget）

## 过滤与灰度
- QLExpress 表达式过滤引擎（沙箱 + 白名单 + 禁用关键字 + 超时保护）
- allowlist / denylist 名单过滤
- 灰度：百分比分流 / AB 实验分组 / 人群包绑定
- 过滤器 CRUD + 表达式校验

## 奖品
- 奖品管理 CRUD（类型/库存/限制/领取设置/时间窗口/奖品组）
- 奖品发放（PrizeHandler 策略：积分/优惠券/徽章/站内）
- 领奖专区（自动/手动领取 + 防重锁）
- 奖品过期（用户进专区 + 定时扫描 + 领奖时校验）
- 奖品记录管理 + 补发

## 活动
- 活动配置管理 + 状态机（DRAFT → PUBLISHED → ONLINE → OFFLINE）
- 展示规则（富文本 + content_hash ETag）
- 灰度控制（NONE/RATIO）+ 参与规则引擎
- 参与规则校验链：白名单/新用户/次数限制/设备指纹/IP 限频
- 活动统计聚合 + 定时上下线

## 签到
- 签到配置管理（CRUD + 发布/下线）
- 签到 + 补签
- 签到日历 + 签到状态查询
- 积分账户 + 积分流水查询
- 积分手动发放

## 系统
- 用户管理（admin/client 分页查询/重置密码/启用停用/踢下线）
- Sa-Token 多账号 JWT 鉴权（admin StpUtil + client StpUserUtil）
- 监控指标（dashboard/任务维度/活动维度/按日聚合）
- 模拟测试（身份模拟 + 手动触发 + 全流程测试）
- 审计日志查询
- 互斥组管理（独立表 + 跨周期互斥 + 移除关联）
- 验证码
- 客户端用户注册/登录
