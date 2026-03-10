#!/bin/bash
# MCP 多层架构 - 全部启动脚本
# 使用方式: bash start-all.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "============================================"
echo "  MCP Multi-Layer Architecture - Startup"
echo "============================================"

# 1. 启动远端 MCP Server (Java)
echo ""
echo "[1/3] Starting Remote MCP Server (Java, port 8080)..."
cd "$SCRIPT_DIR/remote-mcp-server"
if [ ! -f target/*.jar ] 2>/dev/null; then
    echo "  Building Java project..."
    mvn clean package -DskipTests -q
fi
java -jar target/*.jar --server.port=8080 &
REMOTE_PID=$!
echo "  Remote MCP Server PID: $REMOTE_PID"

# 等待 Java 启动
echo "  Waiting for Remote MCP Server to start..."
for i in $(seq 1 30); do
    if curl -s http://localhost:8080/health > /dev/null 2>&1; then
        echo "  Remote MCP Server is ready!"
        break
    fi
    sleep 1
done

# 2. 启动 Gateway (Go)
echo ""
echo "[2/3] Starting MCP Gateway (Go, port 9090)..."
cd "$SCRIPT_DIR/mcp-gateway"
if [ ! -f mcp-gateway ] && [ ! -f mcp-gateway.exe ]; then
    echo "  Building Go Gateway..."
    go build -o mcp-gateway .
fi
./mcp-gateway --config config.json &
GATEWAY_PID=$!
echo "  MCP Gateway PID: $GATEWAY_PID"
sleep 2

# 3. 提示 Local Proxy 用法
echo ""
echo "[3/3] Local MCP Proxy (Go, stdio mode)"
echo "  Build: cd local-mcp-proxy && go build -o local-mcp-proxy ."
echo ""
echo "  Usage in claude_desktop_config.json:"
echo '  {'
echo '    "mcpServers": {'
echo '      "mcp-proxy": {'
echo "        \"command\": \"$SCRIPT_DIR/local-mcp-proxy/local-mcp-proxy\","
echo '        "args": ["--gateway", "http://localhost:9090/gateway"],'
echo '        "transport": "stdio"'
echo '      }'
echo '    }'
echo '  }'
echo ""
echo "============================================"
echo "  All services started!"
echo "  Remote MCP: http://localhost:8080/mcp"
echo "  Gateway:    http://localhost:9090/gateway/mcp"
echo "  Admin API:  http://localhost:9090/admin/remotes"
echo "============================================"
echo ""
echo "Press Ctrl+C to stop all services"

# 等待子进程
trap "kill $REMOTE_PID $GATEWAY_PID 2>/dev/null; exit 0" INT TERM
wait
