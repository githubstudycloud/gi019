package proxy

import (
	"bufio"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"os"
)

// JSONRPCRequest JSON-RPC 请求
type JSONRPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      interface{}     `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params"`
}

// StdioServer 通过 stdio 接收 Claude Code 的 MCP 请求
type StdioServer struct {
	client *RemoteClient
	reader *bufio.Reader
	writer io.Writer
}

func NewStdioServer(client *RemoteClient) *StdioServer {
	return &StdioServer{
		client: client,
		reader: bufio.NewReader(os.Stdin),
		writer: os.Stdout,
	}
}

// Run 主循环：读取 stdin，处理请求，写入 stdout
func (s *StdioServer) Run() error {
	log.Println("[Proxy] Starting stdio server...")

	// 初始化连接
	if err := s.client.Initialize(); err != nil {
		log.Printf("[Proxy] Warning: Initialize failed: %v (will retry on request)", err)
	}

	// 拉取工具列表
	if err := s.client.RefreshTools(); err != nil {
		log.Printf("[Proxy] Warning: RefreshTools failed: %v", err)
	}

	// 启动定期刷新（60秒）
	s.client.StartPeriodicRefresh(60_000_000_000) // 60s in nanoseconds
	defer s.client.Stop()

	scanner := bufio.NewScanner(os.Stdin)
	scanner.Buffer(make([]byte, 1024*1024), 1024*1024) // 1MB buffer

	for scanner.Scan() {
		line := scanner.Bytes()
		if len(line) == 0 {
			continue
		}

		var req JSONRPCRequest
		if err := json.Unmarshal(line, &req); err != nil {
			log.Printf("[Proxy] Failed to parse request: %v", err)
			continue
		}

		// 通知类型（无 id）不需要响应
		if req.ID == nil {
			s.handleNotification(&req)
			continue
		}

		response := s.handleRequest(&req)
		s.writeResponse(response)
	}

	if err := scanner.Err(); err != nil {
		return fmt.Errorf("stdin read error: %w", err)
	}

	return nil
}

func (s *StdioServer) handleNotification(req *JSONRPCRequest) {
	log.Printf("[Proxy] Notification: %s", req.Method)
}

func (s *StdioServer) handleRequest(req *JSONRPCRequest) interface{} {
	switch req.Method {
	case "initialize":
		return s.handleInitialize(req)
	case "tools/list":
		return s.handleToolsList(req)
	case "tools/call":
		return s.handleToolsCall(req)
	default:
		return makeError(req.ID, -32601, "Method not found: "+req.Method)
	}
}

func (s *StdioServer) handleInitialize(req *JSONRPCRequest) interface{} {
	return makeResult(req.ID, map[string]interface{}{
		"protocolVersion": "2025-03-26",
		"capabilities": map[string]interface{}{
			"tools": map[string]interface{}{},
		},
		"serverInfo": map[string]interface{}{
			"name":    "local-mcp-proxy",
			"version": "1.0.0",
		},
	})
}

func (s *StdioServer) handleToolsList(req *JSONRPCRequest) interface{} {
	tools := s.client.GetTools()
	if tools == nil {
		tools = []ToolInfo{}
	}
	return makeResult(req.ID, map[string]interface{}{
		"tools": tools,
	})
}

func (s *StdioServer) handleToolsCall(req *JSONRPCRequest) interface{} {
	var params struct {
		Name      string                 `json:"name"`
		Arguments map[string]interface{} `json:"arguments"`
	}
	if err := json.Unmarshal(req.Params, &params); err != nil {
		return makeError(req.ID, -32602, "Invalid params")
	}

	resp, err := s.client.CallTool(req.ID, params.Name, params.Arguments)
	if err != nil {
		return makeError(req.ID, -32000, fmt.Sprintf("Tool call failed: %v", err))
	}

	// 直接转发远端的完整 JSON-RPC 响应
	var rpcResp map[string]interface{}
	if err := json.Unmarshal(resp, &rpcResp); err != nil {
		return makeError(req.ID, -32000, "Failed to parse remote response")
	}

	// 替换 id 为本地请求的 id
	rpcResp["id"] = req.ID
	return rpcResp
}

func (s *StdioServer) writeResponse(response interface{}) {
	data, err := json.Marshal(response)
	if err != nil {
		log.Printf("[Proxy] Failed to marshal response: %v", err)
		return
	}
	fmt.Fprintf(s.writer, "%s\n", data)
}

func makeResult(id interface{}, result interface{}) map[string]interface{} {
	return map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"result":  result,
	}
}

func makeError(id interface{}, code int, message string) map[string]interface{} {
	return map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"error": map[string]interface{}{
			"code":    code,
			"message": message,
		},
	}
}
