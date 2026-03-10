package gateway

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"strings"
	"time"
)

// GatewayHandler 网关 HTTP 处理器
type GatewayHandler struct {
	registry    *Registry
	rateLimiter *SimpleRateLimiter
	breakers    map[string]*CircuitBreaker // remoteID -> breaker
	client      *http.Client
}

func NewGatewayHandler(registry *Registry) *GatewayHandler {
	return &GatewayHandler{
		registry:    registry,
		rateLimiter: NewSimpleRateLimiter(100, time.Second), // 100 QPS per user
		breakers:    make(map[string]*CircuitBreaker),
		client: &http.Client{
			Timeout: 30 * time.Second,
		},
	}
}

func (h *GatewayHandler) getBreaker(remoteID string) *CircuitBreaker {
	if cb, ok := h.breakers[remoteID]; ok {
		return cb
	}
	cb := NewCircuitBreaker(5, 30*time.Second)
	h.breakers[remoteID] = cb
	return cb
}

// ServeHTTP 处理 MCP JSON-RPC 请求
func (h *GatewayHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodGet {
		// SSE 端点 - 简化处理
		h.handleSSE(w, r)
		return
	}

	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// 获取用户标识（简化：用 Header 或 IP）
	userID := r.Header.Get("X-User-ID")
	if userID == "" {
		userID = r.RemoteAddr
	}

	// 限流检查
	if !h.rateLimiter.Allow(userID) {
		writeJSONRPCError(w, nil, -32000, "Rate limit exceeded")
		return
	}

	body, err := io.ReadAll(r.Body)
	if err != nil {
		writeJSONRPCError(w, nil, -32700, "Parse error")
		return
	}
	defer r.Body.Close()

	var req JSONRPCRequest
	if err := json.Unmarshal(body, &req); err != nil {
		writeJSONRPCError(w, nil, -32700, "Parse error")
		return
	}

	switch req.Method {
	case "initialize":
		h.handleInitialize(w, &req)
	case "tools/list":
		h.handleToolsList(w, &req)
	case "tools/call":
		h.handleToolsCall(w, &req, body)
	case "notifications/initialized":
		w.WriteHeader(http.StatusAccepted)
	default:
		writeJSONRPCError(w, req.ID, -32601, "Method not found: "+req.Method)
	}
}

func (h *GatewayHandler) handleInitialize(w http.ResponseWriter, req *JSONRPCRequest) {
	result := map[string]interface{}{
		"protocolVersion": "2025-03-26",
		"capabilities": map[string]interface{}{
			"tools": map[string]interface{}{},
		},
		"serverInfo": map[string]interface{}{
			"name":    "mcp-gateway",
			"version": "1.0.0",
		},
	}
	writeJSONRPCResult(w, req.ID, result)
}

func (h *GatewayHandler) handleToolsList(w http.ResponseWriter, req *JSONRPCRequest) {
	tools := h.registry.GetAllTools()
	result := map[string]interface{}{
		"tools": tools,
	}
	writeJSONRPCResult(w, req.ID, result)
}

func (h *GatewayHandler) handleToolsCall(w http.ResponseWriter, req *JSONRPCRequest, rawBody []byte) {
	// 解析工具名
	var params struct {
		Name      string                 `json:"name"`
		Arguments map[string]interface{} `json:"arguments"`
	}
	if err := json.Unmarshal(req.Params, &params); err != nil {
		writeJSONRPCError(w, req.ID, -32602, "Invalid params")
		return
	}

	// 路由到远端
	remote, err := h.registry.RouteToRemote(params.Name)
	if err != nil {
		writeJSONRPCError(w, req.ID, -32000, err.Error())
		return
	}

	// 熔断检查
	cb := h.getBreaker(remote.ID)
	if !cb.Allow() {
		writeJSONRPCError(w, req.ID, -32000, fmt.Sprintf("Circuit breaker open for remote: %s", remote.ID))
		return
	}

	// 转发到远端 MCP
	resp, err := h.client.Post(remote.URL+"/mcp", "application/json",
		io.NopCloser(bytes.NewReader(rawBody)))
	if err != nil {
		cb.RecordFailure()
		writeJSONRPCError(w, req.ID, -32000, fmt.Sprintf("Remote MCP error: %v", err))
		return
	}
	defer resp.Body.Close()

	cb.RecordSuccess()

	// 转发响应
	respBody, _ := io.ReadAll(resp.Body)
	w.Header().Set("Content-Type", "application/json")
	w.Write(respBody)
}

func (h *GatewayHandler) handleSSE(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "SSE not supported", http.StatusInternalServerError)
		return
	}

	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	// 发送 endpoint 事件
	fmt.Fprintf(w, "event: endpoint\ndata: /gateway/mcp\n\n")
	flusher.Flush()

	// 保持连接（简化：5分钟超时）
	timer := time.NewTimer(5 * time.Minute)
	defer timer.Stop()

	select {
	case <-timer.C:
		log.Println("[Gateway] SSE connection timeout")
	case <-r.Context().Done():
		log.Println("[Gateway] SSE client disconnected")
	}
}

// --- 管理 API ---

// HandleListRemotes 列出所有远端节点
func (h *GatewayHandler) HandleListRemotes(w http.ResponseWriter, r *http.Request) {
	remotes := h.registry.GetAllRemotes()
	json.NewEncoder(w).Encode(map[string]interface{}{
		"remotes": remotes,
	})
}

// HandleAddRemote 动态添加远端节点
func (h *GatewayHandler) HandleAddRemote(w http.ResponseWriter, r *http.Request) {
	var req struct {
		ID   string `json:"id"`
		Name string `json:"name"`
		URL  string `json:"url"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, "Invalid request body", http.StatusBadRequest)
		return
	}

	h.registry.AddRemote(req.ID, req.Name, req.URL)
	// 立即刷新该节点
	go h.registry.RefreshAll()

	w.WriteHeader(http.StatusCreated)
	json.NewEncoder(w).Encode(map[string]string{"status": "added", "id": req.ID})
}

// HandleRemoveRemote 移除远端节点
func (h *GatewayHandler) HandleRemoveRemote(w http.ResponseWriter, r *http.Request) {
	id := strings.TrimPrefix(r.URL.Path, "/admin/remotes/")
	if id == "" {
		http.Error(w, "Missing remote ID", http.StatusBadRequest)
		return
	}
	h.registry.RemoveRemote(id)
	json.NewEncoder(w).Encode(map[string]string{"status": "removed", "id": id})
}

// --- JSON-RPC helpers ---

type JSONRPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      interface{}     `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params"`
}

func writeJSONRPCResult(w http.ResponseWriter, id interface{}, result interface{}) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"result":  result,
	})
}

func writeJSONRPCError(w http.ResponseWriter, id interface{}, code int, message string) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"error": map[string]interface{}{
			"code":    code,
			"message": message,
		},
	})
}
