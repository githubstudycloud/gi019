package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/mcp-architecture/mcp-gateway/gateway"
)

type Config struct {
	Port    int             `json:"port"`
	Remotes []RemoteConfig `json:"remotes"`
}

type RemoteConfig struct {
	ID   string `json:"id"`
	Name string `json:"name"`
	URL  string `json:"url"`
}

func main() {
	port := flag.Int("port", 9090, "Gateway listen port")
	configFile := flag.String("config", "", "Config file path (JSON)")
	remotesFlag := flag.String("remotes", "", "Comma-separated remote MCPs: id=name=url,...")
	flag.Parse()

	registry := gateway.NewRegistry()

	// 从配置文件加载
	if *configFile != "" {
		data, err := os.ReadFile(*configFile)
		if err != nil {
			log.Fatalf("Failed to read config: %v", err)
		}
		var cfg Config
		if err := json.Unmarshal(data, &cfg); err != nil {
			log.Fatalf("Failed to parse config: %v", err)
		}
		if cfg.Port > 0 {
			*port = cfg.Port
		}
		for _, r := range cfg.Remotes {
			registry.AddRemote(r.ID, r.Name, r.URL)
			log.Printf("[Gateway] Registered remote: %s -> %s", r.ID, r.URL)
		}
	}

	// 从命令行参数加载
	if *remotesFlag != "" {
		for _, entry := range strings.Split(*remotesFlag, ",") {
			parts := strings.SplitN(entry, "=", 3)
			if len(parts) == 3 {
				registry.AddRemote(parts[0], parts[1], parts[2])
				log.Printf("[Gateway] Registered remote: %s -> %s", parts[0], parts[2])
			}
		}
	}

	// 启动定期刷新（30秒）
	registry.StartPeriodicRefresh(30 * time.Second)
	defer registry.Stop()

	handler := gateway.NewGatewayHandler(registry)

	mux := http.NewServeMux()

	// MCP 端点（Streamable HTTP）
	mux.Handle("/gateway/mcp", handler)

	// SSE 端点
	mux.HandleFunc("/gateway/sse", func(w http.ResponseWriter, r *http.Request) {
		handler.ServeHTTP(w, r)
	})

	// 管理 API
	mux.HandleFunc("/admin/remotes", func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			handler.HandleListRemotes(w, r)
		case http.MethodPost:
			handler.HandleAddRemote(w, r)
		default:
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		}
	})
	mux.HandleFunc("/admin/remotes/", func(w http.ResponseWriter, r *http.Request) {
		if r.Method == http.MethodDelete {
			handler.HandleRemoveRemote(w, r)
		} else {
			http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		}
	})

	// 健康检查
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		json.NewEncoder(w).Encode(map[string]string{
			"status":  "UP",
			"service": "mcp-gateway",
		})
	})

	addr := fmt.Sprintf(":%d", *port)
	log.Printf("[Gateway] Starting MCP Gateway on %s", addr)
	if err := http.ListenAndServe(addr, mux); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}
