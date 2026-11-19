package tunnel

import (
	"context"
	"sync"
	"time"

	"github.com/metacubex/mihomo/adapter/outboundgroup"
	C "github.com/metacubex/mihomo/constant"
	"github.com/metacubex/mihomo/constant/provider"
	"github.com/metacubex/mihomo/log"
	"github.com/metacubex/mihomo/tunnel"
)

// HealthCheck tests every proxy of a group. With the default test
// URL (or an empty one) it runs the providers' own health checks,
// exactly as before. With a custom test URL the providers would
// keep using the subscription's URLs, so every proxy of the group
// is probed directly with the chosen URL instead — the same
// per-proxy URLTest the single-node test uses. Either way the
// results land in the proxies' delay histories.
func HealthCheck(name string, testURL string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request health check for `%s`: not found", name)

		return
	}

	g, ok := p.Adapter().(outboundgroup.ProxyGroup)
	if !ok {
		log.Warnln("Request health check for `%s`: invalid type %s", name, p.Type().String())

		return
	}

	wg := &sync.WaitGroup{}

	if testURL == "" || testURL == C.DefaultTestURL {
		for _, pr := range g.Providers() {
			wg.Add(1)

			go func(provider provider.ProxyProvider) {
				provider.HealthCheck()

				wg.Done()
			}(pr)
		}
	} else {
		for _, pr := range g.Providers() {
			for _, px := range pr.Proxies() {
				wg.Add(1)

				go func(proxy C.Proxy) {
					defer wg.Done()

					ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
					defer cancel()

					if _, err := proxy.URLTest(ctx, testURL, nil); err != nil {
						log.Warnln("Delay test for `%s` via %s: %s", proxy.Name(), testURL, err.Error())
					}
				}(px)
			}
		}
	}

	wg.Wait()
}

// HealthCheckProxy runs a delay test for exactly one proxy — the
// native equivalent of GET /proxies/{name}/delay, as opposed to
// HealthCheck above, which re-tests every node of a group through
// its providers. The result lands in the proxy's delay history and
// is read back through QueryProxyGroup like any other test.
func HealthCheckProxy(name string, testURL string) {
	p := tunnel.Proxies()[name]

	if p == nil {
		log.Warnln("Request delay test for `%s`: not found", name)

		return
	}

	if _, isGroup := p.Adapter().(outboundgroup.ProxyGroup); isGroup {
		// Groups keep the provider health check.
		HealthCheck(name, testURL)

		return
	}

	if testURL == "" {
		testURL = C.DefaultTestURL
	}

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if _, err := p.URLTest(ctx, testURL, nil); err != nil {
		log.Warnln("Delay test for `%s`: %s", name, err.Error())
	}
}

func HealthCheckAll() {
	for _, g := range QueryProxyGroupNames(false) {
		go func(group string) {
			HealthCheck(group, "")
		}(g)
	}
}
