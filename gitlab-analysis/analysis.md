# GitLab Docker Compose 配置分析报告

## 环境概要

- **镜像**: gitlab/gitlab-ce:18.2.2-ce.0
- **部署目标**: NAS 环境
- **资源限制**: 8G 内存 / 2 CPU
- **网络**: 外部网络 nginx_net001

---

## 一、发现的问题

### 1. 严重问题

#### 1.1 备份卷路径冲突
```yaml
- ./data:/var/opt/gitlab:Z
- ./backups:/var/opt/gitlab/backups:Z
```
`./data` 已经挂载到 `/var/opt/gitlab`，而 `./backups` 挂载到其子目录 `/var/opt/gitlab/backups`。
这会导致 `./data/backups` 被 `./backups` 覆盖遮蔽。虽然功能上可以工作（备份写入 `./backups`），但容易造成混淆。建议使用独立路径如 `/gitlab-backups`。

#### 1.2 `:Z` 卷标签在非 SELinux 系统上无意义
NAS 系统（群晖/威联通等）通常不使用 SELinux。`:Z` 标签是 SELinux 特有的，在非 SELinux 系统上虽无害但增加混淆。

#### 1.3 `initial_shared_runners_registration_token` 已弃用
GitLab 16+ 已弃用 registration token 方式注册 Runner，18.x 应使用新的 Runner 认证方式。此配置可能被忽略或产生警告。

#### 1.4 自定义错误页面拼写错误
```ruby
'title' => 'The page you are looking for doesn not exist'
```
`doesn not` 应为 `does not`。

### 2. 中等问题

#### 2.1 KAS 配置可能多余
对于 NAS 个人/小团队使用场景，Kubernetes Agent Server (KAS) 通常不需要。启用它会占用额外内存和 CPU。

#### 2.2 GitLab Pages 配置复杂性
Pages 需要额外端口(8980)和配置，如果不实际使用 Pages 功能，建议禁用以节省资源。

#### 2.3 Prometheus 监控开销
在 8G 内存限制的 NAS 上，Prometheus 会占用 200-500MB 内存。对于小团队来说，这个开销可能不值得。

#### 2.4 Remote Development 配置无效
```ruby
gitlab_rails['remote_development_feature_flag_enabled'] = true
```
这不是一个有效的 GitLab 配置项。Remote Development 是通过 Feature Flag API 或管理后台控制的，不是通过 omnibus 配置。

#### 2.5 VSCode Web IDE 配置项可能无效
```ruby
gitlab_rails['vscode_web_ide_enabled'] = true
gitlab_rails['vscode_web_ide_external_url'] = '...'
```
GitLab 18.x 的 Web IDE 基于 VS Code 已经是默认启用的，这些配置项不是标准的 omnibus 配置键。

### 3. 轻微问题 / 优化建议

#### 3.1 内存分配不合理
当前分配：
- PostgreSQL shared_buffers: 512MB
- Redis: 512MB
- Puma worker_killer_max_memory: 2048MB × 2 workers = 最大 4GB
- Sidekiq、Gitaly、其他组件

总计可能超过 8G 限制，导致 OOM。

#### 3.2 `shm_size: '1g'` 偏大
PostgreSQL 在此负载下不需要 1GB 共享内存。256MB-512MB 足够。

#### 3.3 Nginx gzip_types 缺少常用类型
缺少 `image/svg+xml` 和 `application/x-font-ttf` 等。

#### 3.4 healthcheck start_period 可以更长
GitLab 在 NAS 上首次启动可能需要 5-10 分钟，400s 可能不够，建议 600s。

#### 3.5 `redis['save']` 配置
```ruby
redis['save'] = ["900 1"]
```
对于 NAS 减少磁盘写入是好的，但可以考虑完全禁用 RDB 持久化（GitLab 的 Redis 数据是可重建的）。

---

## 二、各组件配置评估

| 组件 | 当前配置 | 评估 | 建议 |
|------|---------|------|------|
| **PostgreSQL** | 512MB shared_buffers | 偏高 | 256MB 即可 |
| **Redis** | 512MB maxmemory | 偏高 | 256MB 足够 |
| **Puma** | 2 workers, 2-4 threads | 合理 | 保持不变 |
| **Sidekiq** | max_concurrency=10 | 合理 | 可降至 5 |
| **Nginx** | 2 workers, 1024 connections | 合理 | 保持不变 |
| **Prometheus** | 启用 | 不推荐 | 禁用节省资源 |
| **KAS** | 启用 | 不推荐 | 禁用节省资源 |
| **Pages** | 启用 | 看需求 | 不用则禁用 |
| **Gitaly** | 有并发限制 | 合理 | 保持不变 |

---

## 三、安全评估

| 项目 | 状态 | 说明 |
|------|------|------|
| privileged: false | 好 | 正确禁用特权模式 |
| 注册关闭 | 好 | signup_enabled = false |
| LDAP/OAuth 禁用 | 好 | 简化安全面 |
| HTTP 明文 | 注意 | 内网可接受，但建议内网也上 HTTPS |
| trusted_proxies | 好 | 正确限制了可信代理 |
| Gravatar 禁用 | 好 | 避免头像信息泄露 |
| Runner Token 硬编码 | 差 | 应使用环境变量或 secrets |

---

## 四、总结

配置整体质量较高，体现了对 NAS 场景的理解。主要问题集中在：
1. **资源分配过度** — 各组件内存总和可能超过 8G 限制
2. **不必要的服务** — KAS、Pages、Prometheus 在小团队 NAS 上是浪费
3. **部分配置项无效** — remote_development、vscode_web_ide 等非标准配置
4. **备份卷路径覆盖** — 虽能工作但不清晰
