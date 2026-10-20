// Command jsonbench is the Go harness of jsonBench (see ../HARNESS.md).
//
// The parent process re-executes itself once per parser (--child) so that one
// parser's heap, GC pacing and sonic JIT cache do not tax the next one.
package main

import (
	"bufio"
	"bytes"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"runtime/metrics"
	"sort"
	"strings"
	"time"
)

type settings struct {
	WarmupMs  int `json:"warmup_ms"`
	MinTimeMs int `json:"min_time_ms"`
	MinRounds int `json:"min_rounds"`
}

type workload struct {
	Name   string `json:"name"`
	File   string `json:"file"`
	Events int    `json:"events"`
	Bytes  int64  `json:"bytes"`
	Digest string `json:"digest"`
}

type manifest struct {
	Fingerprint string     `json:"fingerprint"`
	Workloads   []workload `json:"workloads"`
}

type row struct {
	Parser             string   `json:"parser"`
	Library            string   `json:"library"`
	Version            string   `json:"version"`
	License            string   `json:"license"`
	Mode               string   `json:"mode"`
	Input              string   `json:"input"`
	Notes              string   `json:"notes"`
	Workload           string   `json:"workload"`
	Events             int      `json:"events"`
	Bytes              int64    `json:"bytes"`
	DigestOK           *bool    `json:"digest_ok"`
	Errors             int      `json:"errors"`
	Error              *string  `json:"error"`
	Rounds             int      `json:"rounds"`
	MedianNs           int64    `json:"median_ns"`
	MinNs              int64    `json:"min_ns"`
	P90Ns              int64    `json:"p90_ns"`
	NsPerEvent         float64  `json:"ns_per_event"`
	MBPerS             float64  `json:"mb_per_s"`
	EventsPerS         float64  `json:"events_per_s"`
	AllocBytesPerEvent *float64 `json:"alloc_bytes_per_event"`
	AllocsPerEvent     *float64 `json:"allocs_per_event"`
	GCCount            *int64   `json:"gc_count"`
	GCMs               *float64 `json:"gc_ms"`
	// Heap still in use per event after a full GC with every result of one more parse
	// pass kept alive (median of 3 passes; see measureRetained). Null when the
	// parser's result is invalidated by the next parse, or the pass failed.
	RetainedBytesPerEvent *float64 `json:"retained_bytes_per_event"`
	// Go-specific extra: estimated GC CPU time (runtime/metrics
	// /cpu/classes/gc/total:cpu-seconds) over the measured rounds. gc_ms is only the
	// stop-the-world pause total, which hides the concurrent mark work.
	GCCPUMs *float64 `json:"gc_cpu_ms"`
	Sink    int64    `json:"sink"`
}

type output struct {
	Harness           string         `json:"harness"`
	Language          string         `json:"language"`
	Runtime           string         `json:"runtime"`
	Host              map[string]any `json:"host"`
	Settings          settings       `json:"settings"`
	CorpusFingerprint string         `json:"corpus_fingerprint"`
	Results           []row          `json:"results"`
}

type opts struct {
	corpus, out        string
	workloads, parsers []string
	set                settings
	list, child, quick bool
	explicitWarmup     bool
	explicitMinTime    bool
	explicitMinRounds  bool
}

func splitList(s string) []string {
	var r []string
	for _, p := range strings.Split(s, ",") {
		if p = strings.TrimSpace(p); p != "" {
			r = append(r, p)
		}
	}
	return r
}

func parseFlags() opts {
	var o opts
	var wl, ps string
	fs := flag.NewFlagSet("jsonbench", flag.ExitOnError)
	fs.StringVar(&o.corpus, "corpus", "jsonBench/.corpus", "corpus directory")
	fs.StringVar(&o.out, "out", "jsonBench/results/go.json", "output JSON file")
	fs.StringVar(&wl, "workloads", "", "comma-separated workloads (default: all)")
	fs.StringVar(&ps, "parsers", "", "comma-separated parser ids (default: all)")
	fs.IntVar(&o.set.WarmupMs, "warmup-ms", 1000, "warmup per (workload, parser)")
	fs.IntVar(&o.set.MinTimeMs, "min-time-ms", 2000, "minimum measured time")
	fs.IntVar(&o.set.MinRounds, "min-rounds", 5, "minimum measured rounds")
	fs.BoolVar(&o.quick, "quick", false, "smoke test: warmup 200, min-time 300, min-rounds 3")
	fs.BoolVar(&o.list, "list", false, "print parser ids and exit")
	fs.BoolVar(&o.child, "child", false, "internal: run the given parser in this process and print rows as JSON")
	_ = fs.Parse(os.Args[1:])
	fs.Visit(func(f *flag.Flag) {
		switch f.Name {
		case "warmup-ms":
			o.explicitWarmup = true
		case "min-time-ms":
			o.explicitMinTime = true
		case "min-rounds":
			o.explicitMinRounds = true
		}
	})
	if o.quick {
		if !o.explicitWarmup {
			o.set.WarmupMs = 200
		}
		if !o.explicitMinTime {
			o.set.MinTimeMs = 300
		}
		if !o.explicitMinRounds {
			o.set.MinRounds = 3
		}
	}
	o.workloads = splitList(wl)
	o.parsers = splitList(ps)
	return o
}

func main() {
	o := parseFlags()
	if o.list {
		for _, p := range parsers {
			fmt.Println(p.ID)
		}
		return
	}
	man, err := loadManifest(o.corpus)
	if err != nil {
		fatal(err)
	}
	wls, err := selectWorkloads(man, o.workloads)
	if err != nil {
		fatal(err)
	}
	sel, err := selectParsers(o.parsers)
	if err != nil {
		fatal(err)
	}
	if o.child {
		if len(sel) != 1 {
			fatal(errors.New("--child needs exactly one parser"))
		}
		rows := runChild(sel[0], o, wls)
		enc := json.NewEncoder(os.Stdout)
		if err := enc.Encode(rows); err != nil {
			fatal(err)
		}
		return
	}
	runParent(o, man, wls, sel)
}

func fatal(err error) {
	fmt.Fprintln(os.Stderr, "jsonbench:", err)
	os.Exit(2)
}

func loadManifest(dir string) (*manifest, error) {
	b, err := os.ReadFile(filepath.Join(dir, "manifest.json"))
	if err != nil {
		return nil, fmt.Errorf("%w (build the corpus with: python3 jsonBench/corpus/prepare.py)", err)
	}
	var m manifest
	if err := json.Unmarshal(b, &m); err != nil {
		return nil, err
	}
	return &m, nil
}

func selectWorkloads(m *manifest, names []string) ([]workload, error) {
	if len(names) == 0 {
		return m.Workloads, nil
	}
	var r []workload
	for _, n := range names {
		found := false
		for _, w := range m.Workloads {
			if w.Name == n {
				r = append(r, w)
				found = true
			}
		}
		if !found {
			return nil, fmt.Errorf("unknown workload %q", n)
		}
	}
	return r, nil
}

func selectParsers(ids []string) ([]parserDef, error) {
	if len(ids) == 0 {
		return parsers, nil
	}
	var r []parserDef
	for _, id := range ids {
		found := false
		for _, p := range parsers {
			if p.ID == id {
				r = append(r, p)
				found = true
			}
		}
		if !found {
			return nil, fmt.Errorf("unknown parser %q (see --list)", id)
		}
	}
	return r, nil
}

// ---- parent ----

func runParent(o opts, man *manifest, wls []workload, sel []parserDef) {
	self, err := os.Executable()
	if err != nil {
		fatal(err)
	}
	wlNames := make([]string, len(wls))
	for i, w := range wls {
		wlNames[i] = w.Name
	}
	var rows []row
	for _, p := range sel {
		fmt.Fprintf(os.Stderr, "== %s\n", p.ID)
		args := []string{
			"--child", "--parsers", p.ID, "--corpus", o.corpus,
			"--workloads", strings.Join(wlNames, ","),
			"--warmup-ms", fmt.Sprint(o.set.WarmupMs),
			"--min-time-ms", fmt.Sprint(o.set.MinTimeMs),
			"--min-rounds", fmt.Sprint(o.set.MinRounds),
		}
		cmd := exec.Command(self, args...)
		cmd.Stderr = os.Stderr
		var stdout bytes.Buffer
		cmd.Stdout = &stdout
		runErr := cmd.Run()
		var got []row
		if runErr == nil {
			runErr = json.Unmarshal(stdout.Bytes(), &got)
		}
		if runErr != nil {
			// The child died (panic, crash): report every workload as failed.
			msg := fmt.Sprintf("child process failed: %v", runErr)
			for _, w := range wls {
				r := baseRow(p, w)
				f := false
				r.DigestOK = &f
				r.Error = &msg
				got = append(got, r)
			}
		}
		rows = append(rows, got...)
	}

	out := output{
		Harness:           "go",
		Language:          "Go",
		Runtime:           runtimeDescription(),
		Host:              hostInfo(),
		Settings:          o.set,
		CorpusFingerprint: man.Fingerprint,
		Results:           rows,
	}
	if dir := filepath.Dir(o.out); dir != "" {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			fatal(err)
		}
	}
	b, err := json.MarshalIndent(out, "", "  ")
	if err != nil {
		fatal(err)
	}
	if err := os.WriteFile(o.out, append(b, '\n'), 0o644); err != nil {
		fatal(err)
	}
	printSummary(rows)
	fmt.Fprintf(os.Stderr, "wrote %s\n", o.out)
}

func printSummary(rows []row) {
	sorted := append([]row(nil), rows...)
	sort.SliceStable(sorted, func(i, j int) bool {
		if sorted[i].Workload != sorted[j].Workload {
			return sorted[i].Workload < sorted[j].Workload
		}
		return sorted[i].MBPerS > sorted[j].MBPerS
	})
	fmt.Fprintf(os.Stderr, "%-10s %-22s %-8s %6s %9s %9s %10s %8s %5s %8s %10s\n",
		"workload", "parser", "mode", "digest", "MB/s", "ns/event", "B/event", "allocs/e", "GCs", "gcCPUms", "retainedB/e")
	for _, r := range sorted {
		d := "n/a"
		if r.DigestOK != nil {
			d = fmt.Sprint(*r.DigestOK)
		}
		fmt.Fprintf(os.Stderr, "%-10s %-22s %-8s %6s %9.1f %9.1f %10s %8s %5s %8s %10s\n",
			r.Workload, r.Parser, r.Mode, d, r.MBPerS, r.NsPerEvent,
			fmtF(r.AllocBytesPerEvent), fmtF(r.AllocsPerEvent), fmtI(r.GCCount), fmtF(r.GCCPUMs),
			fmtF(r.RetainedBytesPerEvent))
	}
}

func fmtF(p *float64) string {
	if p == nil {
		return "-"
	}
	return fmt.Sprintf("%.1f", *p)
}

func fmtI(p *int64) string {
	if p == nil {
		return "-"
	}
	return fmt.Sprint(*p)
}

func gogcSetting() string {
	if v, ok := os.LookupEnv("GOGC"); ok {
		return v
	}
	return "100 (default)"
}

func gomemlimitSetting() string {
	if v, ok := os.LookupEnv("GOMEMLIMIT"); ok {
		return v
	}
	return "off (default)"
}

func runtimeDescription() string {
	goamd64 := ""
	flags := ""
	if bi, ok := debug.ReadBuildInfo(); ok {
		for _, s := range bi.Settings {
			switch s.Key {
			case "GOAMD64", "GOARM64":
				goamd64 = s.Key + "=" + s.Value
			case "-gcflags", "-ldflags", "-trimpath", "CGO_ENABLED":
				flags += ", " + s.Key + "=" + s.Value
			}
		}
	}
	if goamd64 == "" && runtime.GOARCH == "amd64" {
		goamd64 = "GOAMD64=v1"
	}
	return fmt.Sprintf("%s %s/%s, %s, GOGC=%s, GOMEMLIMIT=%s, GOMAXPROCS=%d%s, one child process per parser",
		runtime.Version(), runtime.GOOS, runtime.GOARCH, goamd64, gogcSetting(), gomemlimitSetting(),
		runtime.GOMAXPROCS(0), flags)
}

func hostInfo() map[string]any {
	cpu := ""
	if f, err := os.Open("/proc/cpuinfo"); err == nil {
		sc := bufio.NewScanner(f)
		for sc.Scan() {
			if k, v, ok := strings.Cut(sc.Text(), ":"); ok && strings.TrimSpace(k) == "model name" {
				cpu = strings.TrimSpace(v)
				break
			}
		}
		f.Close()
	}
	osName := runtime.GOOS
	if b, err := os.ReadFile("/proc/sys/kernel/osrelease"); err == nil {
		osName = "Linux " + strings.TrimSpace(string(b))
	}
	return map[string]any{"cpu": cpu, "cores": runtime.NumCPU(), "os": osName}
}

// ---- child ----

func moduleVersion(mod string) string {
	switch mod {
	case "std", "":
		return runtime.Version()
	}
	if bi, ok := debug.ReadBuildInfo(); ok {
		for _, d := range bi.Deps {
			if d.Path == mod {
				if d.Replace != nil {
					return d.Replace.Version
				}
				return d.Version
			}
		}
	}
	return "unknown"
}

func baseRow(p parserDef, w workload) row {
	return row{
		Parser: p.ID, Library: p.Library, Version: moduleVersion(p.Module), License: licenses[p.Module],
		Mode: p.Mode, Input: p.Input, Notes: p.Notes,
		Workload: w.Name, Events: w.Events, Bytes: w.Bytes,
	}
}

func loadLines(dir string, w workload) ([][]byte, error) {
	data, err := os.ReadFile(filepath.Join(dir, w.File))
	if err != nil {
		return nil, err
	}
	lines := make([][]byte, 0, w.Events)
	for len(data) > 0 {
		i := bytes.IndexByte(data, '\n')
		var l []byte
		if i < 0 {
			l, data = data, nil
		} else {
			l, data = data[:i], data[i+1:]
		}
		if len(l) == 0 {
			continue
		}
		// Cap the capacity so no parser can append into the next line.
		lines = append(lines, l[:len(l):len(l)])
	}
	return lines, nil
}

func strp(s string) *string { return &s }

func runChild(p parserDef, o opts, wls []workload) []row {
	var rows []row
	for _, w := range wls {
		rows = append(rows, runOne(p, o, w))
	}
	return rows
}

func runOne(p parserDef, o opts, w workload) (r row) {
	r = baseRow(p, w)
	defer func() {
		if e := recover(); e != nil {
			f := false
			r.DigestOK = &f
			r.Error = strp(fmt.Sprintf("panic: %v", e))
		}
	}()
	lines, err := loadLines(o.corpus, w)
	if err != nil {
		r.Error = strp(err.Error())
		f := false
		r.DigestOK = &f
		return r
	}
	if len(lines) != w.Events {
		r.Error = strp(fmt.Sprintf("loaded %d lines, manifest says %d", len(lines), w.Events))
	}
	im, err := p.setup()
	run, toEvent := im.run, im.toEvent
	if err != nil {
		f := false
		r.DigestOK = &f
		r.Error = strp(err.Error())
		return r
	}

	// 2. verify (untimed)
	if toEvent != nil {
		d := newDigester()
		var firstErr error
		for i, l := range lines {
			ev, err := func() (ev *Event, err error) {
				defer func() {
					if e := recover(); e != nil {
						err = fmt.Errorf("panic: %v", e)
					}
				}()
				return toEvent(l)
			}()
			if err != nil {
				r.Errors++
				if firstErr == nil {
					firstErr = fmt.Errorf("event %d: %w", i, err)
				}
				continue
			}
			d.add(ev)
		}
		got := d.hex()
		ok := r.Errors == 0 && got == w.Digest
		r.DigestOK = &ok
		if firstErr != nil {
			r.Error = strp(firstErr.Error())
		} else if !ok {
			r.Error = strp(fmt.Sprintf("digest mismatch: got %s want %s", got, w.Digest))
		}
	}

	roundFn := func() (int64, int) {
		var sink int64
		errs := 0
		for _, l := range lines {
			v, err := run(l)
			if err != nil {
				errs++
			}
			sink += v
		}
		return sink, errs
	}

	// 3. warm up
	var sink int64
	warm := time.Duration(o.set.WarmupMs) * time.Millisecond
	ws := time.Now()
	for {
		s, _ := roundFn()
		sink += s
		if time.Since(ws) >= warm {
			break
		}
	}

	// 4. measure
	const maxRounds = 10000
	durs := make([]int64, 0, maxRounds)
	minTime := time.Duration(o.set.MinTimeMs) * time.Millisecond
	gcSample := []metrics.Sample{{Name: "/cpu/classes/gc/total:cpu-seconds"}}
	var before, after runtime.MemStats
	runtime.GC()
	metrics.Read(gcSample)
	gcCPUBefore := gcSample[0].Value.Float64()
	runtime.ReadMemStats(&before)
	ms := time.Now()
	timedErrs := 0
	for len(durs) < maxRounds {
		t0 := time.Now()
		s, e := roundFn()
		durs = append(durs, int64(time.Since(t0)))
		sink += s
		timedErrs += e
		if len(durs) >= o.set.MinRounds && time.Since(ms) >= minTime {
			break
		}
	}
	runtime.ReadMemStats(&after)
	metrics.Read(gcSample)
	gcCPUAfter := gcSample[0].Value.Float64()

	if toEvent == nil && timedErrs > 0 {
		r.Errors = timedErrs / len(durs)
	}
	r.Rounds = len(durs)
	sort.Slice(durs, func(i, j int) bool { return durs[i] < durs[j] })
	r.MedianNs = percentile(durs, 0.5)
	r.MinNs = durs[0]
	r.P90Ns = percentile(durs, 0.9)
	med := float64(r.MedianNs)
	r.NsPerEvent = med / float64(w.Events)
	r.MBPerS = float64(w.Bytes) / (med / 1e9) / 1e6
	r.EventsPerS = float64(w.Events) / (med / 1e9)
	parsed := float64(len(durs)) * float64(len(lines))
	ab := float64(after.TotalAlloc-before.TotalAlloc) / parsed
	ac := float64(after.Mallocs-before.Mallocs) / parsed
	gcn := int64(after.NumGC - before.NumGC)
	gcms := float64(after.PauseTotalNs-before.PauseTotalNs) / 1e6
	gccpu := (gcCPUAfter - gcCPUBefore) * 1e3
	r.AllocBytesPerEvent, r.AllocsPerEvent, r.GCCount, r.GCMs, r.GCCPUMs = &ab, &ac, &gcn, &gcms, &gccpu
	r.Sink = sink

	// 5. retained heap (untimed, after the measured rounds)
	if im.keep != nil && r.Errors == 0 {
		r.RetainedBytesPerEvent = measureRetained(im.keep, lines)
	}
	fmt.Fprintf(os.Stderr, "   %-10s rounds=%-5d median=%.2fms %.1f MB/s %.0f B/ev %.1f allocs/ev gc=%d retained=%s B/ev digest=%v sink=%d\n",
		w.Name, r.Rounds, med/1e6, r.MBPerS, ab, ac, gcn, fmtF(r.RetainedBytesPerEvent), fmtDigest(r.DigestOK), sink)
	return r
}

// retainedPasses is how many keep passes measureRetained runs; it reports the median.
const retainedPasses = 3

// measureRetained runs the parser's keep pass retainedPasses times and returns the
// median HeapAlloc growth per event, or nil if a pass failed. Each pass parses a
// fresh copy of every line and keeps all results alive across a runtime.GC() (see
// keepOf); the previous pass's results are garbage by the next pass's first GC.
func measureRetained(keep keepFn, lines [][]byte) *float64 {
	if len(lines) == 0 {
		return nil
	}
	deltas := make([]int64, 0, retainedPasses)
	for i := 0; i < retainedPasses; i++ {
		d, err := func() (d int64, err error) {
			defer func() {
				if e := recover(); e != nil {
					err = fmt.Errorf("panic: %v", e)
				}
			}()
			return keep(lines)
		}()
		if err != nil {
			fmt.Fprintf(os.Stderr, "   retained pass failed: %v\n", err)
			return nil
		}
		deltas = append(deltas, d)
	}
	sort.Slice(deltas, func(i, j int) bool { return deltas[i] < deltas[j] })
	v := float64(deltas[len(deltas)/2]) / float64(len(lines))
	return &v
}

func fmtDigest(b *bool) string {
	if b == nil {
		return "n/a"
	}
	return fmt.Sprint(*b)
}

// percentile of an ascending slice, nearest-rank.
func percentile(s []int64, q float64) int64 {
	if len(s) == 0 {
		return 0
	}
	if q == 0.5 && len(s)%2 == 0 {
		return (s[len(s)/2-1] + s[len(s)/2]) / 2
	}
	i := int(q*float64(len(s))+0.999999) - 1
	if i < 0 {
		i = 0
	}
	if i >= len(s) {
		i = len(s) - 1
	}
	return s[i]
}
