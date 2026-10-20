package main

// licenses maps each benchmarked module to the SPDX id of its own LICENSE file, read
// from the module cache (go list -m -f '{{.Dir}}' <module>). Versions are not listed
// here: they come from the binary's build info at run time. Re-check this table when
// bumping a module in go.mod.
var licenses = map[string]string{
	"std":                                "BSD-3-Clause",
	"":                                   "BSD-3-Clause", // baseline: the Go runtime itself
	"github.com/go-json-experiment/json": "BSD-3-Clause",
	"github.com/goccy/go-json":           "MIT",
	"github.com/json-iterator/go":        "MIT",
	"github.com/bytedance/sonic":         "Apache-2.0",
	"github.com/segmentio/encoding":      "MIT",
	"github.com/mailru/easyjson":         "MIT",
	"github.com/valyala/fastjson":        "MIT",
	"github.com/buger/jsonparser":        "MIT",
	"github.com/minio/simdjson-go":       "Apache-2.0",
	"github.com/tidwall/gjson":           "MIT",
	"github.com/go-faster/jx":            "MIT",
	"github.com/ohler55/ojg":             "MIT",
	"github.com/nbd-wtf/go-nostr":        "MIT",
	"fiatjaf.com/nostr":                  "Unlicense",
}
