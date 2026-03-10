@echo off
REM MCP Multi-Layer Architecture - Windows Startup Script
REM Usage: start-all.bat

echo ============================================
echo   MCP Multi-Layer Architecture - Startup
echo ============================================

set SCRIPT_DIR=%~dp0

REM 1. Build and start Remote MCP Server (Java)
echo.
echo [1/3] Building Remote MCP Server (Java)...
cd /d "%SCRIPT_DIR%remote-mcp-server"
call mvn clean package -DskipTests -q
echo Starting Remote MCP Server on port 8080...
start "Remote MCP Server" java -jar target\remote-mcp-server-1.0.0.jar --server.port=8080
timeout /t 10 /nobreak > nul

REM 2. Build and start Gateway (Go)
echo.
echo [2/3] Building MCP Gateway (Go)...
cd /d "%SCRIPT_DIR%mcp-gateway"
go build -o mcp-gateway.exe .
echo Starting MCP Gateway on port 9090...
start "MCP Gateway" mcp-gateway.exe --config config.json
timeout /t 3 /nobreak > nul

REM 3. Build Local Proxy
echo.
echo [3/3] Building Local MCP Proxy (Go)...
cd /d "%SCRIPT_DIR%local-mcp-proxy"
go build -o local-mcp-proxy.exe .

echo.
echo ============================================
echo   All services started!
echo   Remote MCP: http://localhost:8080/mcp
echo   Gateway:    http://localhost:9090/gateway/mcp
echo   Admin API:  http://localhost:9090/admin/remotes
echo ============================================
echo.
echo Configure Claude Code with:
echo   "command": "%SCRIPT_DIR%local-mcp-proxy\local-mcp-proxy.exe"
echo   "args": ["--gateway", "http://localhost:9090/gateway"]
echo.
pause
