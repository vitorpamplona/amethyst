package main

import (
	"encoding/hex"
	stdjson "encoding/json"
	"errors"
	"fmt"
	"runtime"
	"strconv"

	fnostr "fiatjaf.com/nostr"
	"github.com/buger/jsonparser"
	"github.com/bytedance/sonic"
	"github.com/bytedance/sonic/ast"
	"github.com/go-faster/jx"
	jsonv2 "github.com/go-json-experiment/json"
	gojson "github.com/goccy/go-json"
	jsoniter "github.com/json-iterator/go"
	"github.com/mailru/easyjson"
	simdjson "github.com/minio/simdjson-go"
	gonostr "github.com/nbd-wtf/go-nostr"
	"github.com/ohler55/ojg/oj"
	segjson "github.com/segmentio/encoding/json"
	"github.com/tidwall/gjson"
	"github.com/valyala/fastjson"

	"github.com/vitorpamplona/amethyst/jsonbench/easyev"
)

// runFn is the timed operation: parse one line and return the sink contribution.
// eventFn parses one line through the same code path and converts the result to an
// Event for the digest (the conversion is untimed: only used during verification).
// keepFn is the retained-heap pass (see measureRetained in main.go): it parses every
// line once more, keeps every result alive and returns the HeapAlloc growth.
type (
	runFn   func(line []byte) (int64, error)
	eventFn func(line []byte) (*Event, error)
	keepFn  func(lines [][]byte) (int64, error)
)

// impl is what a parser's setup hands the harness. keep is nil when one parse's
// result is invalidated by the next (a reused parser tree), so retained heap is null.
type impl struct {
	run     runFn
	toEvent eventFn
	keep    keepFn
}

// keepOf builds the retained-heap pass for a parse function whose result T is the
// parser's own result (the library's Event for typed rows, the tree for dom rows).
//
// Every line is first copied into a fresh []byte, the way gorilla/websocket's
// ReadMessage and coder/websocket's Read hand each text frame to the app in its own
// buffer, and the copy is made inside the measured window. A result that does not
// point into the frame lets the copy die and only the result is counted; a result
// whose strings alias the frame (or a library-internal copy of it) keeps that whole
// buffer alive, and it is counted too. Parsing the preloaded corpus lines instead
// would make aliasing results look free, because those lines are already allocated.
// The results slice is allocated before the baseline reading, so it is not counted.
func keepOf[T any](parse func([]byte) (T, error)) keepFn {
	return func(lines [][]byte) (int64, error) {
		res := make([]T, len(lines))
		var before, after runtime.MemStats
		runtime.GC()
		runtime.ReadMemStats(&before)
		for i, l := range lines {
			frame := make([]byte, len(l))
			copy(frame, l)
			v, err := parse(frame)
			if err != nil {
				return 0, fmt.Errorf("event %d: %w", i, err)
			}
			res[i] = v
		}
		runtime.GC()
		runtime.ReadMemStats(&after)
		runtime.KeepAlive(res)
		return int64(after.HeapAlloc) - int64(before.HeapAlloc), nil
	}
}

type parserDef struct {
	ID      string
	Library string
	Module  string // Go module path, "std" for the standard library, "" for a baseline
	Mode    string
	Input   string
	Notes   string
	// setup builds fresh per-run state (reused parser objects) and returns the closures.
	setup func() (impl, error)
}

// typedOf wires a parse function that returns a *T into the three closures.
func typedOf[T any](parse func([]byte) (*T, error), fold func(*T) int64, conv func(*T) *Event) impl {
	run := func(l []byte) (int64, error) {
		v, err := parse(l)
		if err != nil {
			return 0, err
		}
		return fold(v), nil
	}
	ev := func(l []byte) (*Event, error) {
		v, err := parse(l)
		if err != nil {
			return nil, err
		}
		return conv(v), nil
	}
	return impl{run: run, toEvent: ev, keep: keepOf(parse)}
}

func ident(e *Event) *Event { return e }
func foldEv(e *Event) int64 { return e.fold() }

// typedEvent is the common case: the library decodes straight into a fresh *Event.
func typedEvent(dec func([]byte, *Event) error) (impl, error) {
	return typedOf(func(l []byte) (*Event, error) {
		ev := new(Event)
		if err := dec(l, ev); err != nil {
			return nil, err
		}
		return ev, nil
	}, foldEv, ident), nil
}

var errShape = errors.New("unexpected JSON shape")

var parsers = []parserDef{
	{
		ID: "stdlib-typed", Library: "encoding/json", Module: "std", Mode: "typed", Input: "bytes",
		Notes: "json.Unmarshal into Event (reflection)",
		setup: func() (impl, error) {
			return typedEvent(func(l []byte, ev *Event) error { return stdjson.Unmarshal(l, ev) })
		},
	},
	{
		ID: "stdlib-map", Library: "encoding/json", Module: "std", Mode: "dom", Input: "bytes",
		Notes: "json.Unmarshal into map[string]any (numbers as float64)",
		setup: func() (impl, error) {
			parse := func(l []byte) (map[string]any, error) {
				var m map[string]any
				err := stdjson.Unmarshal(l, &m)
				return m, err
			}
			run := func(l []byte) (int64, error) {
				m, err := parse(l)
				if err != nil {
					return 0, err
				}
				k, _ := m["kind"].(float64)
				t, _ := m["tags"].([]any)
				return int64(k) + int64(len(t)), nil
			}
			ev := func(l []byte) (*Event, error) {
				m, err := parse(l)
				if err != nil {
					return nil, err
				}
				return eventFromAnyMap(m)
			}
			return impl{run: run, toEvent: ev, keep: keepOf(parse)}, nil
		},
	},
	{
		ID: "jsonv2-typed", Library: "go-json-experiment/json", Module: "github.com/go-json-experiment/json", Mode: "typed", Input: "bytes",
		Notes: "json/v2 proposal (experimental module; Go 1.25 ships it as GOEXPERIMENT=jsonv2). Last commit whose go.mod allows Go 1.24; newer ones need Go 1.25/1.26",
		setup: func() (impl, error) {
			return typedEvent(func(l []byte, ev *Event) error { return jsonv2.Unmarshal(l, ev) })
		},
	},
	{
		ID: "goccy-typed", Library: "goccy/go-json", Module: "github.com/goccy/go-json", Mode: "typed", Input: "bytes-copy",
		Notes: "gojson.Unmarshal into Event: copies the frame into a pooled, NUL-terminated context buffer inside the call; decoded strings are copied out of it",
		setup: func() (impl, error) {
			return typedEvent(func(l []byte, ev *Event) error { return gojson.Unmarshal(l, ev) })
		},
	},
	{
		ID: "jsoniter-typed", Library: "json-iterator/go", Module: "github.com/json-iterator/go", Mode: "typed", Input: "bytes",
		Notes: "jsoniter.ConfigFastest.Unmarshal into Event",
		setup: func() (impl, error) {
			api := jsoniter.ConfigFastest
			return typedEvent(func(l []byte, ev *Event) error { return api.Unmarshal(l, ev) })
		},
	},
	{
		ID: "jsoniter-typed-compat", Library: "json-iterator/go", Module: "github.com/json-iterator/go", Mode: "typed", Input: "bytes",
		Notes: "jsoniter.ConfigCompatibleWithStandardLibrary.Unmarshal into Event",
		setup: func() (impl, error) {
			api := jsoniter.ConfigCompatibleWithStandardLibrary
			return typedEvent(func(l []byte, ev *Event) error { return api.Unmarshal(l, ev) })
		},
	},
	{
		ID: "sonic-typed", Library: "bytedance/sonic", Module: "github.com/bytedance/sonic", Mode: "typed", Input: "bytes→string",
		Notes: "sonic.ConfigDefault.Unmarshal into Event (amd64 JIT). Unmarshal([]byte) is UnmarshalFromString(string(frame)): one frame-sized string copy per parse, inside the loop. Escape-free strings are substrings of that copy (CopyString=false), so a kept Event keeps the whole frame copy alive; retained heap counts it",
		setup: func() (impl, error) {
			api := sonic.ConfigDefault
			return typedEvent(func(l []byte, ev *Event) error { return api.Unmarshal(l, ev) })
		},
	},
	{
		ID: "sonic-typed-std", Library: "bytedance/sonic", Module: "github.com/bytedance/sonic", Mode: "typed", Input: "bytes→string",
		Notes: "sonic.ConfigStd.Unmarshal into Event: CopyString + ValidateString (encoding/json-compatible behaviour). Same string(frame) copy as sonic-typed, but every field is copied out of it, so the frame copy dies after the parse",
		setup: func() (impl, error) {
			api := sonic.ConfigStd
			return typedEvent(func(l []byte, ev *Event) error { return api.Unmarshal(l, ev) })
		},
	},
	{
		ID: "sonic-ast", Library: "bytedance/sonic", Module: "github.com/bytedance/sonic", Mode: "dom", Input: "bytes→string",
		Notes: "ast.NewRaw(string(frame)) + LoadAll() (full tree, not lazy); the []byte->string copy is timed, and the tree points into that string, so a kept tree keeps the frame copy alive",
		setup: func() (impl, error) {
			parse := func(l []byte) (ast.Node, error) {
				n := ast.NewRaw(string(l))
				err := n.LoadAll()
				return n, err
			}
			run := func(l []byte) (int64, error) {
				n, err := parse(l)
				if err != nil {
					return 0, err
				}
				k, err := n.Get("kind").Int64()
				if err != nil {
					return 0, err
				}
				c, err := n.Get("tags").Len()
				if err != nil {
					return 0, err
				}
				return k + int64(c), nil
			}
			ev := func(l []byte) (*Event, error) {
				n, err := parse(l)
				if err != nil {
					return nil, err
				}
				i, err := n.Interface()
				if err != nil {
					return nil, err
				}
				m, ok := i.(map[string]any)
				if !ok {
					return nil, errShape
				}
				return eventFromAnyMap(m)
			}
			return impl{run: run, toEvent: ev, keep: keepOf(parse)}, nil
		},
	},
	{
		ID: "segmentio-typed", Library: "segmentio/encoding/json", Module: "github.com/segmentio/encoding", Mode: "typed", Input: "bytes",
		Notes: "json.Unmarshal into Event (drop-in encoding/json replacement). Every decoded slice starts at capacity 10, so each tag holds room for 10 strings; that slack shows in retained heap",
		setup: func() (impl, error) {
			return typedEvent(func(l []byte, ev *Event) error { return segjson.Unmarshal(l, ev) })
		},
	},
	{
		ID: "segmentio-borrowed", Library: "segmentio/encoding/json", Module: "github.com/segmentio/encoding", Mode: "borrowed", Input: "bytes",
		Notes: "json.Parse(frame, &ev, json.ZeroCopy): escape-free strings alias the frame's []byte, so a kept Event keeps the whole frame buffer alive (and is corrupted if the socket reuses that buffer). Retained heap parses a fresh copy of each frame, so the pinned frames are counted",
		setup: func() (impl, error) {
			return typedEvent(func(l []byte, ev *Event) error {
				_, err := segjson.Parse(l, ev, segjson.ZeroCopy)
				return err
			})
		},
	},
	{
		ID: "easyjson-typed", Library: "mailru/easyjson", Module: "github.com/mailru/easyjson", Mode: "typed", Input: "bytes",
		Notes: "generated decoder (easyev/event_easyjson.go, -no_std_marshalers) via easyjson.Unmarshal",
		setup: func() (impl, error) {
			return typedOf(func(l []byte) (*easyev.EasyEvent, error) {
				ev := new(easyev.EasyEvent)
				if err := easyjson.Unmarshal(l, ev); err != nil {
					return nil, err
				}
				return ev, nil
			}, func(e *easyev.EasyEvent) int64 {
				return int64(e.Kind) + int64(len(e.Tags)) + int64(len(e.Content))
			}, func(e *easyev.EasyEvent) *Event {
				return &Event{ID: e.ID, PubKey: e.PubKey, CreatedAt: e.CreatedAt, Kind: e.Kind, Tags: e.Tags, Content: e.Content, Sig: e.Sig}
			}), nil
		},
	},
	{
		ID: "fastjson-dom", Library: "valyala/fastjson", Module: "github.com/valyala/fastjson", Mode: "dom", Input: "bytes-copy",
		Notes: "one reused fastjson.Parser: ParseBytes copies the frame into the Parser's own reused buffer and the tree points into it; strings are unescaped lazily, only when read. The tree is only valid until the next Parse, so retained heap is null",
		setup: func() (impl, error) {
			var p fastjson.Parser
			run := func(l []byte) (int64, error) {
				v, err := p.ParseBytes(l)
				if err != nil {
					return 0, err
				}
				return v.GetInt64("kind") + int64(len(v.GetArray("tags"))), nil
			}
			ev := func(l []byte) (*Event, error) {
				v, err := p.ParseBytes(l)
				if err != nil {
					return nil, err
				}
				return eventFromFastjson(v)
			}
			return impl{run: run, toEvent: ev}, nil // tree reused by the next Parse: no retained number
		},
	},
	{
		ID: "fastjson-typed", Library: "valyala/fastjson", Module: "github.com/valyala/fastjson", Mode: "typed", Input: "bytes-copy",
		Notes: "reused fastjson.Parser (ParseBytes copies the frame into its reused buffer), then every field copied out into Event",
		setup: func() (impl, error) {
			var p fastjson.Parser
			return typedEvent(func(l []byte, ev *Event) error {
				v, err := p.ParseBytes(l)
				if err != nil {
					return err
				}
				return fillFromFastjson(v, ev)
			})
		},
	},
	{
		ID: "jsonparser-typed", Library: "buger/jsonparser", Module: "github.com/buger/jsonparser", Mode: "typed", Input: "bytes",
		Notes: "EachKey over the 7 top-level paths + nested ArrayEach for tags; strings via jsonparser.ParseString (copy + unescape). Callbacks are built once and reused",
		setup: setupJsonparser,
	},
	{
		ID: "simdjson-go-dom", Library: "minio/simdjson-go", Module: "github.com/minio/simdjson-go", Mode: "dom", Input: "bytes",
		Notes: "simdjson.Parse(frame, reused *ParsedJson) builds the tape, which points into the frame (pj.Message); kind via FindKey, tag count via Array iteration. The ParsedJson is reused by the next Parse, so retained heap is null. Needs AVX2+CLMUL",
		setup: setupSimdjson,
	},
	{
		ID: "gjson-typed", Library: "tidwall/gjson", Module: "github.com/tidwall/gjson", Mode: "typed", Input: "bytes",
		Notes: "gjson.GetBytes once per field (7 scans of the frame; GetBytes copies each result out of the frame), tags via Result.ForEach. The tag strings are substrings of one copy of the whole tags JSON text, which a kept Event keeps alive",
		setup: setupGjson,
	},
	{
		ID: "jx-typed", Library: "go-faster/jx", Module: "github.com/go-faster/jx", Mode: "typed", Input: "bytes",
		Notes: "streaming jx.Decoder (reused via ResetBytes): ObjBytes/Arr callbacks built once, Str() copies",
		setup: setupJx,
	},
	{
		ID: "ojg-dom", Library: "ohler55/ojg", Module: "github.com/ohler55/ojg", Mode: "dom", Input: "bytes",
		Notes: "reused oj.Parser (Reuse off) -> map[string]any with int64 numbers. Library bug: oj decodes each \\uXXXX escape on its own, so a surrogate pair (escaped emoji) becomes two U+FFFD; digest fails on every workload with escaped emoji",
		setup: func() (impl, error) {
			p := &oj.Parser{}
			parse := func(l []byte) (map[string]any, error) {
				v, err := p.Parse(l)
				if err != nil {
					return nil, err
				}
				m, ok := v.(map[string]any)
				if !ok {
					return nil, errShape
				}
				return m, nil
			}
			run := func(l []byte) (int64, error) {
				m, err := parse(l)
				if err != nil {
					return 0, err
				}
				k, _ := m["kind"].(int64)
				t, _ := m["tags"].([]any)
				return k + int64(len(t)), nil
			}
			ev := func(l []byte) (*Event, error) {
				m, err := parse(l)
				if err != nil {
					return nil, err
				}
				return eventFromAnyMap(m)
			}
			return impl{run: run, toEvent: ev, keep: keepOf(parse)}, nil
		},
	},
	{
		ID: "go-nostr-typed", Library: "nbd-wtf/go-nostr", Module: "github.com/nbd-wtf/go-nostr", Mode: "typed", Input: "bytes",
		Notes: "nostr.Event.UnmarshalJSON: easyjson-generated decoder checked into go-nostr (jlexer, string fields copied, tags pre-sized 7x5)",
		setup: func() (impl, error) {
			return typedOf(func(l []byte) (*gonostr.Event, error) {
				ev := new(gonostr.Event)
				if err := ev.UnmarshalJSON(l); err != nil {
					return nil, err
				}
				return ev, nil
			}, func(e *gonostr.Event) int64 {
				return int64(e.Kind) + int64(len(e.Tags)) + int64(len(e.Content))
			}, func(e *gonostr.Event) *Event {
				tags := make([][]string, len(e.Tags))
				for i, t := range e.Tags {
					tags[i] = t
				}
				return &Event{ID: e.ID, PubKey: e.PubKey, CreatedAt: int64(e.CreatedAt), Kind: e.Kind, Tags: tags, Content: e.Content, Sig: e.Sig}
			}), nil
		},
	},
	{
		ID: "fiatjaf-nostr-typed", Library: "fiatjaf.com/nostr", Module: "fiatjaf.com/nostr", Mode: "typed", Input: "bytes",
		Notes: "nostr.Event.UnmarshalJSON: hand-tuned easyjson lexer that hex-decodes id/pubkey/sig straight into [32]byte/[64]byte arrays (no strings for them), Kind is uint16. An all-zero array means absent in this library (its encoder omits zero id/pubkey/sig; its decoder leaves them zero unless the hex is exactly 64/128 chars), so the digest glue maps zero back to an empty string (the corpus has unsigned rumors whose sig is empty). Pinned to the last commit whose go.mod allows Go 1.24 (decoder identical to HEAD except unknown-field skipping)",
		setup: func() (impl, error) {
			return typedOf(func(l []byte) (*fnostr.Event, error) {
				ev := new(fnostr.Event)
				if err := ev.UnmarshalJSON(l); err != nil {
					return nil, err
				}
				return ev, nil
			}, func(e *fnostr.Event) int64 {
				return int64(e.Kind) + int64(len(e.Tags)) + int64(len(e.Content))
			}, func(e *fnostr.Event) *Event {
				tags := make([][]string, len(e.Tags))
				for i, t := range e.Tags {
					tags[i] = t
				}
				return &Event{
					ID: hexOrEmpty(e.ID[:]), PubKey: hexOrEmpty(e.PubKey[:]),
					CreatedAt: int64(e.CreatedAt), Kind: int(e.Kind), Tags: tags, Content: e.Content,
					Sig: hexOrEmpty(e.Sig[:]),
				}
			}), nil
		},
	},
	{
		ID: "go-bytes-to-string", Library: "go runtime", Module: "", Mode: "baseline", Input: "bytes→string",
		Notes: "string(frame): allocate + copy, the cost of turning a []byte frame into a Go string. Retained heap is what keeping the raw frame as a string costs",
		setup: func() (impl, error) {
			run := func(l []byte) (int64, error) {
				s := string(l)
				return int64(len(s)), nil
			}
			return impl{run: run, keep: keepOf(func(l []byte) (string, error) { return string(l), nil })}, nil
		},
	},
}

// ---- generic tree -> Event (verification only) ----

func anyInt(v any) (int64, error) {
	switch n := v.(type) {
	case float64:
		return int64(n), nil
	case int64:
		return n, nil
	case int:
		return int64(n), nil
	case uint64:
		return int64(n), nil
	case stdjson.Number:
		return n.Int64()
	case string: // sonic ast may hand back numbers as json.Number/string
		return strconv.ParseInt(n, 10, 64)
	}
	return 0, fmt.Errorf("not an integer: %T", v)
}

func eventFromAnyMap(m map[string]any) (*Event, error) {
	e := &Event{}
	var ok bool
	if e.ID, ok = m["id"].(string); !ok {
		return nil, errShape
	}
	if e.PubKey, ok = m["pubkey"].(string); !ok {
		return nil, errShape
	}
	if e.Content, ok = m["content"].(string); !ok {
		return nil, errShape
	}
	if e.Sig, ok = m["sig"].(string); !ok {
		return nil, errShape
	}
	ca, err := anyInt(m["created_at"])
	if err != nil {
		return nil, err
	}
	e.CreatedAt = ca
	k, err := anyInt(m["kind"])
	if err != nil {
		return nil, err
	}
	e.Kind = int(k)
	tags, ok := m["tags"].([]any)
	if !ok {
		return nil, errShape
	}
	e.Tags = make([][]string, len(tags))
	for i, t := range tags {
		arr, ok := t.([]any)
		if !ok {
			return nil, errShape
		}
		e.Tags[i] = make([]string, len(arr))
		for j, s := range arr {
			if e.Tags[i][j], ok = s.(string); !ok {
				return nil, errShape
			}
		}
	}
	return e, nil
}

// hexOrEmpty hex-encodes a fixed-size id/key/sig array, mapping the all-zero value
// (fiatjaf.com/nostr's "absent") to "".
func hexOrEmpty(b []byte) string {
	for _, c := range b {
		if c != 0 {
			return hex.EncodeToString(b)
		}
	}
	return ""
}

// ---- fastjson ----

func eventFromFastjson(v *fastjson.Value) (*Event, error) {
	e := new(Event)
	return e, fillFromFastjson(v, e)
}

func fjString(v *fastjson.Value, key string) (string, error) {
	f := v.Get(key)
	if f == nil {
		return "", fmt.Errorf("missing %q", key)
	}
	b, err := f.StringBytes()
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func fillFromFastjson(v *fastjson.Value, ev *Event) error {
	var err error
	if ev.ID, err = fjString(v, "id"); err != nil {
		return err
	}
	if ev.PubKey, err = fjString(v, "pubkey"); err != nil {
		return err
	}
	if ev.Content, err = fjString(v, "content"); err != nil {
		return err
	}
	if ev.Sig, err = fjString(v, "sig"); err != nil {
		return err
	}
	ca := v.Get("created_at")
	k := v.Get("kind")
	if ca == nil || k == nil {
		return errShape
	}
	if ev.CreatedAt, err = ca.Int64(); err != nil {
		return err
	}
	if ev.Kind, err = k.Int(); err != nil {
		return err
	}
	tv := v.Get("tags")
	if tv == nil {
		return errShape
	}
	tags, err := tv.Array()
	if err != nil {
		return err
	}
	ev.Tags = make([][]string, len(tags))
	for i, t := range tags {
		arr, err := t.Array()
		if err != nil {
			return err
		}
		tag := make([]string, len(arr))
		for j, s := range arr {
			b, err := s.StringBytes()
			if err != nil {
				return err
			}
			tag[j] = string(b)
		}
		ev.Tags[i] = tag
	}
	return nil
}

// ---- buger/jsonparser ----

var jpPaths = [][]string{{"id"}, {"pubkey"}, {"created_at"}, {"kind"}, {"tags"}, {"content"}, {"sig"}}

func setupJsonparser() (impl, error) {
	var (
		cur    *Event
		curTag []string
		perr   error
	)
	setErr := func(err error) {
		if err != nil && perr == nil {
			perr = err
		}
	}
	str := func(v []byte) string {
		s, err := jsonparser.ParseString(v)
		setErr(err)
		return s
	}
	elem := func(v []byte, dt jsonparser.ValueType, _ int, err error) {
		setErr(err)
		if dt != jsonparser.String {
			setErr(errShape)
			return
		}
		curTag = append(curTag, str(v))
	}
	tag := func(v []byte, dt jsonparser.ValueType, _ int, err error) {
		setErr(err)
		if dt != jsonparser.Array {
			setErr(errShape)
			return
		}
		curTag = make([]string, 0, 4)
		_, err = jsonparser.ArrayEach(v, elem)
		setErr(err)
		cur.Tags = append(cur.Tags, curTag)
	}
	key := func(idx int, v []byte, dt jsonparser.ValueType, err error) {
		setErr(err)
		switch idx {
		case 0:
			cur.ID = str(v)
		case 1:
			cur.PubKey = str(v)
		case 2:
			n, err := jsonparser.ParseInt(v)
			setErr(err)
			cur.CreatedAt = n
		case 3:
			n, err := jsonparser.ParseInt(v)
			setErr(err)
			cur.Kind = int(n)
		case 4:
			cur.Tags = make([][]string, 0, 4)
			_, err := jsonparser.ArrayEach(v, tag)
			setErr(err)
		case 5:
			cur.Content = str(v)
		case 6:
			cur.Sig = str(v)
		}
	}
	return typedEvent(func(l []byte, ev *Event) error {
		cur, perr = ev, nil
		jsonparser.EachKey(l, key, jpPaths...)
		cur, curTag = nil, nil
		return perr
	})
}

// ---- minio/simdjson-go ----

func setupSimdjson() (impl, error) {
	if !simdjson.SupportedCPU() {
		return impl{}, errors.New("simdjson-go: CPU lacks AVX2/CLMUL (SupportedCPU() == false)")
	}
	var (
		pj   *simdjson.ParsedJson
		iter simdjson.Iter
		root simdjson.Iter
		obj  simdjson.Object
		el   simdjson.Element
		arr  simdjson.Array
	)
	parse := func(l []byte) (*simdjson.Object, error) {
		var err error
		pj, err = simdjson.Parse(l, pj)
		if err != nil {
			return nil, err
		}
		iter = pj.Iter()
		iter.Advance()
		if _, _, err = iter.Root(&root); err != nil {
			return nil, err
		}
		return root.Object(&obj)
	}
	run := func(l []byte) (int64, error) {
		o, err := parse(l)
		if err != nil {
			return 0, err
		}
		if o.FindKey("kind", &el) == nil {
			return 0, errShape
		}
		k, err := el.Iter.Int()
		if err != nil {
			return 0, err
		}
		if o.FindKey("tags", &el) == nil {
			return 0, errShape
		}
		a, err := el.Iter.Array(&arr)
		if err != nil {
			return 0, err
		}
		n := int64(0)
		it := a.Iter()
		for it.Advance() != simdjson.TypeNone {
			n++
		}
		return k + n, nil
	}
	ev := func(l []byte) (*Event, error) {
		o, err := parse(l)
		if err != nil {
			return nil, err
		}
		m, err := o.Map(nil)
		if err != nil {
			return nil, err
		}
		return eventFromAnyMap(m)
	}
	return impl{run: run, toEvent: ev}, nil // ParsedJson reused by the next Parse: no retained number
}

// ---- tidwall/gjson ----

func setupGjson() (impl, error) {
	var (
		cur  *Event
		tag  []string
		perr error
	)
	elem := func(_, v gjson.Result) bool {
		if v.Type != gjson.String {
			perr = errShape
			return false
		}
		tag = append(tag, v.Str)
		return true
	}
	tagFn := func(_, v gjson.Result) bool {
		if !v.IsArray() {
			perr = errShape
			return false
		}
		tag = make([]string, 0, 4)
		v.ForEach(elem)
		cur.Tags = append(cur.Tags, tag)
		return true
	}
	strField := func(l []byte, k string) string {
		r := gjson.GetBytes(l, k)
		if r.Type != gjson.String {
			perr = fmt.Errorf("field %q: not a string", k)
		}
		return r.Str
	}
	return typedEvent(func(l []byte, ev *Event) error {
		cur, perr = ev, nil
		ev.ID = strField(l, "id")
		ev.PubKey = strField(l, "pubkey")
		ev.CreatedAt = gjson.GetBytes(l, "created_at").Int()
		ev.Kind = int(gjson.GetBytes(l, "kind").Int())
		t := gjson.GetBytes(l, "tags")
		if !t.IsArray() {
			return errShape
		}
		ev.Tags = make([][]string, 0, 4)
		t.ForEach(tagFn)
		ev.Content = strField(l, "content")
		ev.Sig = strField(l, "sig")
		cur, tag = nil, nil
		return perr
	})
}

// ---- go-faster/jx ----

func setupJx() (impl, error) {
	d := jx.DecodeBytes(nil)
	var (
		cur *Event
		tag []string
	)
	elem := func(d *jx.Decoder) error {
		s, err := d.Str()
		if err != nil {
			return err
		}
		tag = append(tag, s)
		return nil
	}
	tagFn := func(d *jx.Decoder) error {
		tag = make([]string, 0, 4)
		if err := d.Arr(elem); err != nil {
			return err
		}
		cur.Tags = append(cur.Tags, tag)
		return nil
	}
	field := func(d *jx.Decoder, key []byte) error {
		var err error
		switch string(key) {
		case "id":
			cur.ID, err = d.Str()
		case "pubkey":
			cur.PubKey, err = d.Str()
		case "created_at":
			cur.CreatedAt, err = d.Int64()
		case "kind":
			cur.Kind, err = d.Int()
		case "tags":
			cur.Tags = make([][]string, 0, 4)
			err = d.Arr(tagFn)
		case "content":
			cur.Content, err = d.Str()
		case "sig":
			cur.Sig, err = d.Str()
		default:
			err = d.Skip()
		}
		return err
	}
	return typedEvent(func(l []byte, ev *Event) error {
		cur = ev
		d.ResetBytes(l)
		err := d.ObjBytes(field)
		cur, tag = nil, nil
		return err
	})
}
