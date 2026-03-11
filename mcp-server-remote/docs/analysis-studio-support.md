# MCP Server 兼容性分析：支持 Claude Desktop 与 Claude.ai Studio

**分析日期**：2026-03-11
**服务器版本**：Spring Boot 3.4.3 / Java 17
**协议版本**：`2025-03-26`
**当前端点**：`POST http://localhost:8080/mcp`（Streamable HTTP）

---

## 1. 当前兼容性分析

### 1.1 协议符合性

本项目手工实现了 MCP Streamable HTTP Transport，无需依赖任何官方 SDK。核心逻辑集中于：

- `McpController.java`：HTTP 端点处理，暴露 `POST /mcp`（Streamable HTTP）和 `GET /sse` + `POST /mcp/messages`（旧式 SSE）
- `McpDispatcher.java`：JSON-RPC 2.0 方法路由，协议版本为 `2025-03-26`
- `McpRegistry.java`：工具/资源/提示的注册与分发

当前 `POST /mcp` 端点的行为：

```
Request:  POST /mcp
          Content-Type: application/json
          Body: { "jsonrpc": "2.0", "id": 1, "method": "...", "params": {...} }

Response: 200 OK
          Content-Type: application/json
          Body: { "jsonrpc": "2.0", "id": 1, "result": {...} }
```

Streamable HTTP 是 MCP 官方规范 2025-03-26 版本定义的标准传输协议之一，与 `stdio` 并列为一等公民。当前实现完整支持以下 JSON-RPC 方法：

| 方法 | 说明 | 实现状态 |
|------|------|---------|
| `initialize` | 握手，返回协议版本与 capabilities | 已实现 |
| `ping` | 心跳 | 已实现 |
| `tools/list` | 枚举工具 | 已实现 |
| `tools/call` | 调用工具 | 已实现 |
| `resources/list` | 枚举资源 | 已实现 |
| `resources/read` | 读取资源 | 已实现 |
| `prompts/list` | 枚举提示 | 已实现 |
| `prompts/get` | 获取提示 | 已实现 |

### 1.2 两种客户端的根本差异

| 特性 | Claude Desktop（本地） | Claude.ai Studio（云端） |
|------|----------------------|------------------------|
| 访问方式 | 直接访问 localhost | 从 Anthropic 云端发出 HTTP 请求 |
| 网络要求 | 无（本机网络即可） | 服务器必须有公网 IP / 域名 |
| HTTPS 要求 | 无强制要求 | 强制 HTTPS |
| CORS 要求 | 无 | 需要允许 `https://claude.ai` 跨域 |
| 认证要求 | 无强制要求 | 官方建议 OAuth 2.0，实际可配置 |
| 当前是否支持 | **已支持，无需改动** | **需要额外工作** |

---

## 2. 支持 Claude Desktop（本地）

### 2.1 结论：已完全支持

Claude Desktop 通过读取其配置文件中的 `mcpServers` 配置直连本地服务器。本项目的 `.claude/settings.json` 已包含正确配置：

```json
{
  "mcpServers": {
    "remote-testcase": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

Claude Desktop 会向 `http://localhost:8080/mcp` 发送标准 JSON-RPC 2.0 请求，服务器的 `McpController.handleStreamableHttp()` 方法完整处理该请求。整个交互流程：

```
Claude Desktop
    │
    ├─ POST /mcp {"jsonrpc":"2.0","id":1,"method":"initialize","params":{...}}
    │      ← 200 {"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-03-26",...}}
    │
    ├─ POST /mcp {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
    │      ← 200 {"jsonrpc":"2.0","id":2,"result":{"tools":[...]}}
    │
    └─ POST /mcp {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"...","arguments":{...}}}
           ← 200 {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"..."}]}}
```

### 2.2 Claude Desktop 配置文件位置

除 `.claude/settings.json`（Claude Code 专用）外，Claude Desktop 有独立的配置文件：

- **macOS**：`~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**：`%APPDATA%\Claude\claude_desktop_config.json`

在该文件中添加：

```json
{
  "mcpServers": {
    "remote-testcase": {
      "url": "http://localhost:8080/mcp"
    }
  }
}
```

**无需任何代码修改**。服务器在 `application.yml` 中配置的 `server.port: 8080` 与以上地址匹配，直接可用。

---

## 3. 支持 Claude.ai Studio（云端）

Studio 从 Anthropic 云端主动调用本地服务器，因此需要解决网络可达性、HTTPS 和跨域三个问题。

### 3.1 CORS 配置

**问题**：浏览器端 Studio 发起跨域请求时，Spring Boot 默认拒绝来自 `https://claude.ai` 的请求。

**解决方案**：在项目中新增 Spring CORS 配置类。在 `src/main/java/com/mcp/server/config/` 目录下创建 `CorsConfig.java`：

```java
package com.mcp.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/mcp")
                        .allowedOrigins(
                            "https://claude.ai",
                            "https://*.anthropic.com"
                        )
                        .allowedMethods("POST", "GET", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);

                // SSE 端点也需要 CORS（如使用旧式 SSE transport）
                registry.addMapping("/sse")
                        .allowedOrigins(
                            "https://claude.ai",
                            "https://*.anthropic.com"
                        )
                        .allowedMethods("GET", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(false)
                        .maxAge(3600);
            }
        };
    }
}
```

也可以通过 `application.yml` 配置（Spring Boot 3.x 支持）：

```yaml
spring:
  mvc:
    cors:
      allowed-origins: "https://claude.ai,https://*.anthropic.com"
      allowed-methods: "POST,GET,OPTIONS"
      allowed-headers: "*"
      mapping: "/mcp"
```

> 注意：`allowedOrigins` 中不能同时使用通配符 `*` 和 `allowCredentials(true)`，两者不兼容。Studio 调用不涉及 Cookie，因此 `allowCredentials` 保持 `false` 即可。

### 3.2 HTTPS 配置

Studio 强制要求目标 MCP Server 使用 HTTPS，HTTP 请求会被拒绝。有两种方案：

#### 方案一：Nginx 反向代理（推荐生产）

适用于有公网服务器和域名的场景。Nginx 处理 TLS 终止，将流量转发给 Spring Boot：

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    # SSL 证书（推荐使用 Let's Encrypt 免费证书）
    ssl_certificate     /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    location /mcp {
        proxy_pass         http://127.0.0.1:8080/mcp;
        proxy_http_version 1.1;
        proxy_set_header   Host $host;
        proxy_set_header   X-Real-IP $remote_addr;
        proxy_set_header   X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;

        # SSE 支持：禁止 Nginx 缓冲响应
        proxy_buffering    off;
        proxy_cache        off;
        proxy_read_timeout 300s;
    }

    # 可选：暴露健康检查
    location /health {
        proxy_pass http://127.0.0.1:8080/health;
    }
}

# HTTP 强制跳转 HTTPS
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}
```

Let's Encrypt 免费证书申请：

```bash
# 安装 certbot
apt install certbot python3-certbot-nginx

# 申请证书（自动修改 Nginx 配置）
certbot --nginx -d your-domain.com
```

#### 方案二：Spring Boot 直接配置 SSL

适用于测试/开发场景，不依赖外部反向代理：

```bash
# 生成自签名证书（测试用）
keytool -genkeypair \
  -alias mcp-server \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -storetype PKCS12 \
  -keystore src/main/resources/keystore.p12 \
  -storepass changeit
```

在 `application.yml` 中添加：

```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
    key-alias: mcp-server
```

> 注意：自签名证书会被浏览器和 Studio 拒绝（证书不可信）。生产环境必须使用 CA 签发的正式证书。

### 3.3 公网访问

Studio 需要从 Anthropic 的云端服务器访问 MCP Server，因此服务器必须有公网可达的地址。

#### 方案一：部署到云服务器（推荐生产）

将 Spring Boot JAR 部署至有公网 IP 的云主机：

```bash
# 上传 JAR
scp target/mcp-server-remote-1.0.0.jar user@your-server:/opt/mcp/

# 以生产配置启动（MySQL 数据库）
java -jar /opt/mcp/mcp-server-remote-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/testdb \
  --spring.datasource.username=mcp_user \
  --spring.datasource.password=<password>

# 或者用 systemd 管理进程（推荐）
```

Studio 的 MCP Server 配置：

```json
{
  "url": "https://your-domain.com/mcp"
}
```

#### 方案二：使用 frp 内网穿透（固定隧道）

如果服务器必须运行在本地，可用 frp 建立从公网到本机的持久隧道：

**frp 服务端（公网服务器）`frps.toml`**：
```toml
[common]
bind_port = 7000
```

**frp 客户端（本机）`frpc.toml`**：
```toml
[common]
server_addr = your-public-server-ip
server_port = 7000

[mcp-server]
type = tcp
local_ip  = 127.0.0.1
local_port  = 8080
remote_port = 18080
```

访问地址：`https://your-public-server-ip:18080/mcp`（需在公网服务器上配置 Nginx + SSL）

### 3.4 OAuth 2.0 认证（可选）

MCP 官方规范 2025-03-26 建议生产环境的 Remote MCP Server 实现 OAuth 2.0，但**并非强制要求**。当前实现无认证，适合内部工具场景。

如需添加认证，有两个层次的方案：

#### 简单 API Key 方案（非 OAuth，适合内部使用）

在 `McpController` 中添加拦截逻辑，检查请求头中的 Bearer token：

```java
// 在 handleStreamableHttp 方法开头添加
@Value("${mcp.auth.token:}")
private String expectedToken;

private boolean isAuthenticated(HttpServletRequest httpRequest) {
    if (expectedToken == null || expectedToken.isBlank()) return true; // 未配置则跳过
    String auth = httpRequest.getHeader("Authorization");
    return ("Bearer " + expectedToken).equals(auth);
}
```

`application.yml` 配置：

```yaml
mcp:
  auth:
    token: "your-secret-token-here"
```

Studio 配置时在 headers 中传入 token：

```json
{
  "url": "https://your-domain.com/mcp",
  "headers": {
    "Authorization": "Bearer your-secret-token-here"
  }
}
```

#### 完整 OAuth 2.0 方案（适合正式开放）

需引入 `spring-security-oauth2-authorization-server` 依赖，实现完整的授权码流程。工作量较大，仅在需要多租户、第三方接入时才有必要。

---

## 4. 实现步骤

### 4.1 支持 Claude Desktop（无需任何改动）

服务已完全可用。仅需确认：

1. 服务已启动：`java -jar target/mcp-server-remote-1.0.0.jar --spring.profiles.active=h2`
2. Claude Desktop 配置文件（`%APPDATA%\Claude\claude_desktop_config.json`）中已配置 `http://localhost:8080/mcp`
3. 重启 Claude Desktop 加载配置

### 4.2 支持 Claude.ai Studio（最小改动路径）

按以下顺序操作，每步可独立验证：

**Step 1：添加 CORS 配置**

创建 `src/main/java/com/mcp/server/config/CorsConfig.java`，内容见 3.1 节。

验证：重启服务，用 curl 发送 OPTIONS 预检请求：

```bash
curl -i -X OPTIONS http://localhost:8080/mcp \
  -H "Origin: https://claude.ai" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type"
```

期望响应头中包含：

```
Access-Control-Allow-Origin: https://claude.ai
Access-Control-Allow-Methods: POST,GET,OPTIONS
```

**Step 2：配置 HTTPS（Nginx + Let's Encrypt）**

```bash
# 1. 在云服务器上安装 Nginx 和 certbot
apt update && apt install nginx certbot python3-certbot-nginx -y

# 2. 配置 Nginx（参考 3.2 节配置文件）
nano /etc/nginx/sites-available/mcp-server

# 3. 启用配置
ln -s /etc/nginx/sites-available/mcp-server /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx

# 4. 申请 SSL 证书
certbot --nginx -d your-domain.com

# 5. 验证 HTTPS
curl https://your-domain.com/health
```

**Step 3：部署服务到公网服务器**

```bash
# 打包
mvn clean package -DskipTests

# 上传
scp target/mcp-server-remote-1.0.0.jar user@your-server:/opt/mcp/

# 启动（H2 测试模式）
java -jar /opt/mcp/mcp-server-remote-1.0.0.jar \
  --spring.profiles.active=h2 &

# 或者 MySQL 生产模式
java -jar /opt/mcp/mcp-server-remote-1.0.0.jar \
  --spring.datasource.url=jdbc:mysql://127.0.0.1:3306/testdb \
  --spring.datasource.username=mcp_user \
  --spring.datasource.password=YOUR_PASSWORD &
```

**Step 4：在 Claude.ai Studio 中配置 MCP Server**

登录 Claude.ai → Settings → Integrations → Add MCP Server：

```
URL: https://your-domain.com/mcp
```

验证：Studio 会自动发送 `initialize` 握手请求，成功后可在对话中调用 `remote-testcase` 工具。

### 4.3 代码变更汇总

| 变更项 | 文件 | 必要性 |
|--------|------|--------|
| 添加 CORS 配置 | `config/CorsConfig.java`（新建） | Studio 必须 |
| Nginx SSL 配置 | 服务器配置文件 | Studio 必须 |
| 部署到公网 | 运维操作 | Studio 必须 |
| API Key 认证 | `McpController.java`（修改） | Studio 可选 |
| OAuth 2.0 | 多个文件（新建） | Studio 可选 |

---

## 5. 快速测试方案：ngrok 临时穿透

ngrok 可在几分钟内将本地服务暴露到公网 HTTPS，非常适合快速验证 Studio 连接，**无需购买服务器或配置 DNS**。

### 5.1 安装 ngrok

```bash
# macOS
brew install ngrok

# Windows（PowerShell，管理员）
winget install ngrok

# Linux
curl -s https://ngrok-agent.s3.amazonaws.com/ngrok.asc | sudo tee /etc/apt/trusted.gpg.d/ngrok.asc
echo "deb https://ngrok-agent.s3.amazonaws.com buster main" | sudo tee /etc/apt/sources.list.d/ngrok.list
sudo apt update && sudo apt install ngrok
```

### 5.2 配置并启动 ngrok

```bash
# 注册 ngrok 账号后，配置 authtoken（一次性操作）
ngrok config add-authtoken <YOUR_NGROK_TOKEN>

# 将本地 8080 端口暴露为公网 HTTPS
ngrok http 8080
```

ngrok 启动后输出类似：

```
Forwarding  https://abc123.ngrok-free.app -> http://localhost:8080
```

### 5.3 验证连通性

```bash
# 验证健康检查
curl https://abc123.ngrok-free.app/health

# 验证 MCP initialize
curl -X POST https://abc123.ngrok-free.app/mcp \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}'
```

期望响应：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "protocolVersion": "2025-03-26",
    "capabilities": {
      "tools": {"listChanged": false},
      "resources": {"subscribe": false, "listChanged": false},
      "prompts": {"listChanged": false}
    },
    "serverInfo": {
      "name": "mcp-remote-server",
      "version": "1.0.0"
    }
  }
}
```

### 5.4 在 Studio 中配置 ngrok 地址

在 Claude.ai Studio 中添加 MCP Server：

```
URL: https://abc123.ngrok-free.app/mcp
```

> 注意：ngrok 免费版每次重启会分配新域名。如需固定域名，升级到 ngrok 付费版，或改用 frp 方案（见 3.3 节）。

### 5.5 ngrok 的注意事项

| 项目 | 说明 |
|------|------|
| 免费版域名 | 每次启动随机分配，重启需更新 Studio 配置 |
| 并发连接数 | 免费版有限制（约 40 连接/分钟） |
| 数据安全 | 所有流量经过 ngrok 服务器，生产数据不建议使用 |
| 本地服务 | ngrok 运行期间本地 Spring Boot 必须保持运行 |
| CORS | 使用 ngrok 时，CORS 配置中需将 ngrok 域名加入 `allowedOrigins`（或临时改为 `*`） |

---

## 6. 总结

| 场景 | 当前状态 | 需要的工作 | 工作量 |
|------|---------|-----------|--------|
| Claude Code（本机） | 已支持 | 无 | 0 |
| Claude Desktop（本机） | 已支持 | 配置 Desktop 配置文件 | 5 分钟 |
| Claude.ai Studio（快速测试） | 需配置 | ngrok + CORS 配置 | 30 分钟 |
| Claude.ai Studio（生产） | 需配置 | 云服务器 + Nginx + SSL + CORS | 2-4 小时 |

**关键结论**：本项目的 Streamable HTTP 实现在协议层面完全符合 MCP 规范 2025-03-26，对两种客户端均有良好的协议兼容性。制约 Studio 支持的不是代码质量，而是**网络基础设施**（公网 IP、HTTPS 证书）和**跨域配置**（一个新建 Java 类即可解决）。
