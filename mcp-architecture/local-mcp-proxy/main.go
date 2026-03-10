package main

import (
	"flag"
	"log"
	"os"
	"strings"

	"github.com/mcp-architecture/local-mcp-proxy/proxy"
)

func main() {
	gatewayURL := flag.String("gateway", "", "MCP Gateway URL (e.g., http://localhost:9090/gateway)")
	directURLs := flag.String("direct", "", "Comma-separated direct remote MCP URLs (fallback)")
	flag.Parse()

	// 也支持环境变量
	if *gatewayURL == "" {
		*gatewayURL = os.Getenv("MCP_GATEWAY_URL")
	}
	if *directURLs == "" {
		*directURLs = os.Getenv("MCP_DIRECT_URLS")
	}

	if *gatewayURL == "" && *directURLs == "" {
		log.Fatal("Must specify --gateway or --direct (or set MCP_GATEWAY_URL / MCP_DIRECT_URLS)")
	}

	var directs []string
	if *directURLs != "" {
		directs = strings.Split(*directURLs, ",")
	}

	log.Printf("[Proxy] Gateway: %s", *gatewayURL)
	log.Printf("[Proxy] Direct URLs: %v", directs)

	client := proxy.NewRemoteClient(*gatewayURL, directs)
	server := proxy.NewStdioServer(client)

	if err := server.Run(); err != nil {
		log.Fatalf("Server error: %v", err)
	}
}
