package gateway

import (
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

// ToolInfo 工具信息
type ToolInfo struct {
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	InputSchema map[string]interface{} `json:"inputSchema"`
}

// RemoteMCP 远端 MCP 节点
type RemoteMCP struct {
	ID       string     `json:"id"`
	Name     string     `json:"name"`
	URL      string     `json:"url"`
	Tools    []ToolInfo `json:"tools"`
	Healthy  bool       `json:"healthy"`
	LastSeen time.Time  `json:"lastSeen"`
}

// Registry 工具注册表 - 管理所有远端 MCP 及其工具
type Registry struct {
	mu       sync.RWMutex
	remotes  map[string]*RemoteMCP   // id -> RemoteMCP
	toolMap  map[string][]*RemoteMCP // toolName -> 提供该工具的远端列表
	rrIndex  map[string]int          // toolName -> 轮询索引（负载均衡）
	client   *http.Client
	stopCh   chan struct{}
}

func NewRegistry() *Registry {
	return &Registry{
		remotes: make(map[string]*RemoteMCP),
		toolMap: make(map[string][]*RemoteMCP),
		rrIndex: make(map[string]int),
		client: &http.Client{
			Timeout: 10 * time.Second,
		},
		stopCh: make(chan struct{}),
	}
}

// AddRemote 注册一个远端 MCP
func (r *Registry) AddRemote(id, name, url string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.remotes[id] = &RemoteMCP{
		ID:      id,
		Name:    name,
		URL:     url,
		Healthy: false,
	}
}

// RemoveRemote 移除一个远端 MCP
func (r *Registry) RemoveRemote(id string) {
	r.mu.Lock()
	defer r.mu.Unlock()
	delete(r.remotes, id)
	r.rebuildToolMap()
}

// GetAllTools 获取所有工具列表（聚合去重）
func (r *Registry) GetAllTools() []ToolInfo {
	r.mu.RLock()
	defer r.mu.RUnlock()

	seen := make(map[string]bool)
	var tools []ToolInfo
	for _, remote := range r.remotes {
		if !remote.Healthy {
			continue
		}
		for _, tool := range remote.Tools {
			if !seen[tool.Name] {
				seen[tool.Name] = true
				tools = append(tools, tool)
			}
		}
	}
	return tools
}

// GetAllRemotes 获取所有远端节点信息
func (r *Registry) GetAllRemotes() []*RemoteMCP {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var result []*RemoteMCP
	for _, remote := range r.remotes {
		result = append(result, remote)
	}
	return result
}

// RouteToRemote 根据工具名路由到远端 MCP（轮询负载均衡）
func (r *Registry) RouteToRemote(toolName string) (*RemoteMCP, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	remotes, ok := r.toolMap[toolName]
	if !ok || len(remotes) == 0 {
		return nil, fmt.Errorf("no remote MCP provides tool: %s", toolName)
	}

	// 过滤健康的节点
	var healthy []*RemoteMCP
	for _, rm := range remotes {
		if rm.Healthy {
			healthy = append(healthy, rm)
		}
	}
	if len(healthy) == 0 {
		return nil, fmt.Errorf("no healthy remote MCP for tool: %s", toolName)
	}

	// 轮询
	idx := r.rrIndex[toolName] % len(healthy)
	r.rrIndex[toolName] = idx + 1
	return healthy[idx], nil
}

// RefreshAll 刷新所有远端 MCP 的工具列表
func (r *Registry) RefreshAll() {
	r.mu.RLock()
	ids := make([]string, 0, len(r.remotes))
	for id := range r.remotes {
		ids = append(ids, id)
	}
	r.mu.RUnlock()

	for _, id := range ids {
		r.refreshOne(id)
	}

	r.mu.Lock()
	r.rebuildToolMap()
	r.mu.Unlock()
}

func (r *Registry) refreshOne(id string) {
	r.mu.RLock()
	remote, ok := r.remotes[id]
	if !ok {
		r.mu.RUnlock()
		return
	}
	url := remote.URL
	r.mu.RUnlock()

	// 调用远端 MCP 的 tools/list（通过 MCP JSON-RPC）
	tools, err := r.fetchTools(url)

	r.mu.Lock()
	defer r.mu.Unlock()

	if remote, ok := r.remotes[id]; ok {
		if err != nil {
			log.Printf("[Registry] Failed to refresh %s (%s): %v", id, url, err)
			remote.Healthy = false
		} else {
			remote.Tools = tools
			remote.Healthy = true
			remote.LastSeen = time.Now()
			log.Printf("[Registry] Refreshed %s: %d tools", id, len(tools))
		}
	}
}

// fetchTools 通过 MCP JSON-RPC 获取远端工具列表
func (r *Registry) fetchTools(baseURL string) ([]ToolInfo, error) {
	// 发送 JSON-RPC initialize 请求
	initReq := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      1,
		"method":  "initialize",
		"params": map[string]interface{}{
			"protocolVersion": "2025-03-26",
			"capabilities":    map[string]interface{}{},
			"clientInfo": map[string]interface{}{
				"name":    "mcp-gateway",
				"version": "1.0.0",
			},
		},
	}

	initBody, _ := json.Marshal(initReq)
	resp, err := r.client.Post(baseURL+"/mcp", "application/json", io.NopCloser(
		jsonReader(initBody),
	))
	if err != nil {
		return nil, fmt.Errorf("initialize failed: %w", err)
	}
	resp.Body.Close()

	// 发送 tools/list 请求
	toolsReq := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      2,
		"method":  "tools/list",
		"params":  map[string]interface{}{},
	}

	toolsBody, _ := json.Marshal(toolsReq)
	resp, err = r.client.Post(baseURL+"/mcp", "application/json", io.NopCloser(
		jsonReader(toolsBody),
	))
	if err != nil {
		return nil, fmt.Errorf("tools/list failed: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response failed: %w", err)
	}

	var rpcResp struct {
		Result struct {
			Tools []ToolInfo `json:"tools"`
		} `json:"result"`
	}
	if err := json.Unmarshal(body, &rpcResp); err != nil {
		return nil, fmt.Errorf("parse response failed: %w", err)
	}

	return rpcResp.Result.Tools, nil
}

func (r *Registry) rebuildToolMap() {
	r.toolMap = make(map[string][]*RemoteMCP)
	for _, remote := range r.remotes {
		for _, tool := range remote.Tools {
			r.toolMap[tool.Name] = append(r.toolMap[tool.Name], remote)
		}
	}
}

// StartPeriodicRefresh 启动定期刷新
func (r *Registry) StartPeriodicRefresh(interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		// 启动时立即刷新一次
		r.RefreshAll()
		for {
			select {
			case <-ticker.C:
				r.RefreshAll()
			case <-r.stopCh:
				return
			}
		}
	}()
}

func (r *Registry) Stop() {
	close(r.stopCh)
}
