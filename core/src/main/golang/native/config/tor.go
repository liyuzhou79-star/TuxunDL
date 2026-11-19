package config

import (
	"encoding/json"
	"strings"

	"github.com/metacubex/mihomo/config"
	"github.com/metacubex/mihomo/log"
	T "github.com/metacubex/mihomo/tunnel"
)

// Tor egress (1.7.3): while the session override carries the Tor
// marker (written by the Tor page only after Tor is up and its exit
// has been verified), the parsed config is rewritten so the
// tunnel's only way out is the local Tor SOCKS listener:
//
//   - a socks5 proxy pointing at 127.0.0.1:<tor port> (UDP off —
//     Tor carries TCP only, so UDP is dropped, never sent direct),
//   - mode forced to global,
//   - the GLOBAL selector reduced to that one proxy (replaced when
//     the profile defines GLOBAL, appended when it does not), so
//     the selector's default and every remembered selection can
//     only ever resolve to Tor while armed.
//
// The profile on disk is never touched: the rewrite happens on the
// in-memory RawConfig of each load, so disarming the marker and
// reloading restores the previous behaviour exactly.

const torProxyName = "TUXUN-TOR"
const defaultTorSocksPort = 19050

type torEgressMarker struct {
	Enabled   bool `json:"enabled"`
	SocksPort int  `json:"socks-port"`
}

func readTorEgressMarker() (torEgressMarker, bool) {
	var root struct {
		Tor *torEgressMarker `json:"tuxundl-tor"`
	}
	if err := json.NewDecoder(strings.NewReader(ReadOverride(OverrideSlotSession))).Decode(&root); err != nil || root.Tor == nil {
		return torEgressMarker{}, false
	}

	return *root.Tor, true
}

// TorEgressArmed reports whether the current session override arms
// Tor egress (used by the selector guard in native/tunnel.go).
func TorEgressArmed() bool {
	marker, ok := readTorEgressMarker()
	return ok && marker.Enabled
}

// TorProxyName is the name of the injected Tor egress proxy.
func TorProxyName() string { return torProxyName }

func patchTorEgress(cfg *config.RawConfig, _ string) error {
	marker, ok := readTorEgressMarker()
	if !ok || !marker.Enabled {
		return nil
	}

	port := marker.SocksPort
	if port < 1 || port > 65535 {
		port = defaultTorSocksPort
	}

	torProxy := map[string]any{
		"name":   torProxyName,
		"type":   "socks5",
		"server": "127.0.0.1",
		"port":   port,
		"udp":    false,
	}

	proxies := make([]map[string]any, 0, len(cfg.Proxy)+1)
	proxies = append(proxies, torProxy)
	for _, proxy := range cfg.Proxy {
		if name, _ := proxy["name"].(string); name == torProxyName {
			continue // never keep a profile proxy under our name
		}
		proxies = append(proxies, proxy)
	}
	cfg.Proxy = proxies

	found := false
	for _, group := range cfg.ProxyGroup {
		if name, _ := group["name"].(string); name == "GLOBAL" {
			group["type"] = "select"
			group["proxies"] = []string{torProxyName}
			found = true
		}
	}
	if !found {
		cfg.ProxyGroup = append(cfg.ProxyGroup, map[string]any{
			"name":    "GLOBAL",
			"type":    "select",
			"proxies": []string{torProxyName},
		})
	}

	cfg.Mode = T.Global

	log.Infoln("Tor egress armed: tunnel exits via %s (socks5 127.0.0.1:%d)", torProxyName, port)

	return nil
}
