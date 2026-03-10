package proxy

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"sync"
	"time"
)

// ToolInfo 工具信息（与网关/远端定义一致）
type ToolInfo struct {
	Name        string                 `json:"name"`
	Description string                 `json:"description"`
	InputSchema map[string]interface{} `json:"inputSchema"`
}

// RemoteClient 管理与 Gateway 或远端 MCP 的连接
type RemoteClient struct {
	mu          sync.RWMutex
	gatewayURL  string            // Gateway 地址
	directURLs  []string          // 直连远端地址（降级用）
	tools       []ToolInfo        // 缓存的工具列表
	initialized bool
	client      *http.Client
	stopCh      chan struct{}
}

func NewRemoteClient(gatewayURL string, directURLs []string) *RemoteClient {
	return &RemoteClient{
		gatewayURL: gatewayURL,
		directURLs: directURLs,
		client: &http.Client{
			Timeout: 30 * time.Second,
			Transport: &http.Transport{
				MaxIdleConns:        50,
				MaxIdleConnsPerHost: 10,
				IdleConnTimeout:     90 * time.Second,
			},
		},
		stopCh: make(chan struct{}),
	}
}

// Initialize 初始化连接，向远端发送 initialize
func (c *RemoteClient) Initialize() error {
	url := c.getActiveURL()
	req := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      1,
		"method":  "initialize",
		"params": map[string]interface{}{
			"protocolVersion": "2025-03-26",
			"capabilities":    map[string]interface{}{},
			"clientInfo": map[string]interface{}{
				"name":    "local-mcp-proxy",
				"version": "1.0.0",
			},
		},
	}

	_, err := c.sendRequest(url, req)
	if err != nil {
		return fmt.Errorf("initialize failed: %w", err)
	}

	c.mu.Lock()
	c.initialized = true
	c.mu.Unlock()

	return nil
}

// RefreshTools 刷新工具列表
func (c *RemoteClient) RefreshTools() error {
	url := c.getActiveURL()
	req := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      2,
		"method":  "tools/list",
		"params":  map[string]interface{}{},
	}

	resp, err := c.sendRequest(url, req)
	if err != nil {
		return fmt.Errorf("tools/list failed: %w", err)
	}

	var rpcResp struct {
		Result struct {
			Tools []ToolInfo `json:"tools"`
		} `json:"result"`
	}
	if err := json.Unmarshal(resp, &rpcResp); err != nil {
		return fmt.Errorf("parse tools response failed: %w", err)
	}

	c.mu.Lock()
	c.tools = rpcResp.Result.Tools
	c.mu.Unlock()

	log.Printf("[Proxy] Refreshed tools: %d available", len(rpcResp.Result.Tools))
	return nil
}

// GetTools 获取缓存的工具列表
func (c *RemoteClient) GetTools() []ToolInfo {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.tools
}

// CallTool 调用远端工具
func (c *RemoteClient) CallTool(id interface{}, name string, arguments map[string]interface{}) (json.RawMessage, error) {
	url := c.getActiveURL()
	req := map[string]interface{}{
		"jsonrpc": "2.0",
		"id":      id,
		"method":  "tools/call",
		"params": map[string]interface{}{
			"name":      name,
			"arguments": arguments,
		},
	}

	resp, err := c.sendRequest(url, req)
	if err != nil {
		return nil, fmt.Errorf("tools/call failed: %w", err)
	}

	return resp, nil
}

// StartPeriodicRefresh 定期刷新工具列表
func (c *RemoteClient) StartPeriodicRefresh(interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		for {
			select {
			case <-ticker.C:
				if err := c.RefreshTools(); err != nil {
					log.Printf("[Proxy] Refresh failed: %v", err)
				}
			case <-c.stopCh:
				return
			}
		}
	}()
}

func (c *RemoteClient) Stop() {
	close(c.stopCh)
}

func (c *RemoteClient) getActiveURL() string {
	// 优先使用 Gateway
	if c.gatewayURL != "" {
		return c.gatewayURL
	}
	// 降级直连
	if len(c.directURLs) > 0 {
		return c.directURLs[0]
	}
	return ""
}

func (c *RemoteClient) sendRequest(baseURL string, req interface{}) (json.RawMessage, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, err
	}

	mcpEndpoint := baseURL
	// 如果 URL 不以 /mcp 结尾，自动补上
	if len(mcpEndpoint) > 0 && mcpEndpoint[len(mcpEndpoint)-1] != '/' {
		if !contains(mcpEndpoint, "/mcp") {
			mcpEndpoint += "/mcp"
		}
	}

	resp, err := c.client.Post(mcpEndpoint, "application/json",
		io.NopCloser(bytes.NewReader(body)))
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	return respBody, nil
}

func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s[len(s)-len(substr):] == substr ||
		func() bool {
			for i := 0; i <= len(s)-len(substr); i++ {
				if s[i:i+len(substr)] == substr {
					return true
				}
			}
			return false
		}())
}
