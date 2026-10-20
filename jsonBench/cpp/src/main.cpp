// jsonBench C/C++ harness driver. Implements the CLI, verification, timing and output of
// jsonBench/HARNESS.md; the parsers live in p_<library>.cpp.
#include <openssl/evp.h>
#include <sys/utsname.h>

#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <map>
#include <nlohmann/json.hpp>
#include <optional>
#include <set>
#include <sstream>
#include <thread>

#include "common.hpp"

extern "C" {
extern uint64_t jb_alloc_count;
extern uint64_t jb_alloc_bytes;
}

namespace fs = std::filesystem;
using ojson = nlohmann::ordered_json;

namespace {

struct Options {
  std::string corpus = "jsonBench/.corpus";
  std::string out = "jsonBench/results/cpp.json";
  std::vector<std::string> workloads;
  std::vector<std::string> parsers;
  long warmup_ms = 1000;
  long min_time_ms = 2000;
  long min_rounds = 5;
  bool list = false;
};

std::vector<std::string> split_csv(const std::string& s) {
  std::vector<std::string> out;
  std::stringstream ss(s);
  std::string item;
  while (std::getline(ss, item, ','))
    if (!item.empty()) out.push_back(item);
  return out;
}

Options parse_args(int argc, char** argv) {
  Options o;
  bool quick = false;
  std::optional<long> warm, mt, mr;
  for (int i = 1; i < argc; i++) {
    std::string a = argv[i];
    auto next = [&]() -> std::string {
      if (i + 1 >= argc) fail("missing value for " + a);
      return argv[++i];
    };
    if (a == "--corpus") o.corpus = next();
    else if (a == "--out") o.out = next();
    else if (a == "--workloads") o.workloads = split_csv(next());
    else if (a == "--parsers") o.parsers = split_csv(next());
    else if (a == "--warmup-ms") warm = std::stol(next());
    else if (a == "--min-time-ms") mt = std::stol(next());
    else if (a == "--min-rounds") mr = std::stol(next());
    else if (a == "--quick") quick = true;
    else if (a == "--list") o.list = true;
    else fail("unknown argument: " + a);
  }
  if (quick) {
    o.warmup_ms = 200;
    o.min_time_ms = 300;
    o.min_rounds = 3;
  }
  if (warm) o.warmup_ms = *warm;
  if (mt) o.min_time_ms = *mt;
  if (mr) o.min_rounds = *mr;
  return o;
}

Lines load_lines(const fs::path& file) {
  std::ifstream in(file, std::ios::binary);
  if (!in) fail("cannot open " + file.string());
  Lines lines;
  std::string line;
  while (std::getline(in, line))
    if (!line.empty()) lines.push_back(line);
  return lines;
}

std::string sha256_hex(EVP_MD_CTX* ctx) {
  unsigned char md[32];
  unsigned int len = 0;
  EVP_DigestFinal_ex(ctx, md, &len);
  static const char* hex = "0123456789abcdef";
  std::string out;
  for (unsigned i = 0; i < len; i++) {
    out.push_back(hex[md[i] >> 4]);
    out.push_back(hex[md[i] & 15]);
  }
  return out;
}

std::string cpu_model() {
  std::ifstream in("/proc/cpuinfo");
  std::string line;
  while (std::getline(in, line)) {
    if (line.rfind("model name", 0) == 0) {
      auto p = line.find(':');
      if (p != std::string::npos) return line.substr(p + 2);
    }
  }
  return "unknown";
}

std::string os_name() {
  struct utsname u {};
  if (uname(&u) != 0) return "unknown";
  return std::string(u.sysname) + " " + u.release;
}

volatile uint64_t g_sink = 0;

using Clock = std::chrono::steady_clock;

long long now_ns() {
  return std::chrono::duration_cast<std::chrono::nanoseconds>(Clock::now().time_since_epoch()).count();
}

ojson run_one(const ParserInfo& p, const ojson& wl, const Lines& lines, const Options& o) {
  const std::string wname = wl["name"];
  const uint64_t events = wl["events"];
  const uint64_t bytes = wl["bytes"];
  const std::string expected = wl["digest"];

  ojson row;
  row["parser"] = p.id;
  row["library"] = p.library;
  row["version"] = p.version;
  row["license"] = p.license;
  row["mode"] = p.mode;
  row["input"] = p.input;
  row["notes"] = p.notes;
  row["workload"] = wname;
  row["events"] = events;
  row["bytes"] = bytes;

  uint64_t errors = 0;
  std::string first_error;
  bool digest_ok = false;
  std::unique_ptr<Runner> runner;
  try {
    runner = p.make(lines);
  } catch (const std::exception& e) {
    errors = lines.size();
    first_error = std::string("prepare: ") + e.what();
  }

  if (runner) {
    EVP_MD_CTX* ctx = EVP_MD_CTX_new();
    EVP_DigestInit_ex(ctx, EVP_sha256(), nullptr);
    DigestWriter d;
    for (size_t i = 0; i < lines.size(); i++) {
      d.buffer().clear();
      try {
        runner->digest(i, d);
        EVP_DigestUpdate(ctx, d.buffer().data(), d.buffer().size());
      } catch (const std::exception& e) {
        if (errors++ == 0) first_error = "event " + std::to_string(i) + ": " + e.what();
      }
    }
    std::string got = sha256_hex(ctx);
    EVP_MD_CTX_free(ctx);
    digest_ok = errors == 0 && got == expected && lines.size() == events;
    if (errors == 0 && !digest_ok) first_error = "digest mismatch: got " + got;
  }

  row["digest_ok"] = digest_ok;
  row["errors"] = errors;
  row["error"] = first_error.empty() ? ojson(nullptr) : ojson(first_error);

  auto null_timing = [&] {
    for (const char* k : {"rounds", "median_ns", "min_ns", "p90_ns", "ns_per_event", "mb_per_s",
                          "events_per_s", "alloc_bytes_per_event", "allocs_per_event"})
      row[k] = nullptr;
    row["rounds"] = 0;
    row["gc_count"] = nullptr;
    row["gc_ms"] = nullptr;
  };

  // A parser that fails on some event cannot run a clean round; one with only a digest
  // mismatch is still timed (the numbers are reported but excluded from rankings).
  if (!runner || errors > 0) {
    null_timing();
    return row;
  }

  try {
    const long long warm_end = now_ns() + o.warmup_ms * 1000000LL;
    do {
      g_sink = g_sink + runner->round();
    } while (now_ns() < warm_end);

    std::vector<long long> samples;
    const uint64_t c0 = jb_alloc_count, b0 = jb_alloc_bytes;
    const long long start = now_ns();
    while (true) {
      const long long t0 = now_ns();
      g_sink = g_sink + runner->round();
      const long long t1 = now_ns();
      samples.push_back(t1 - t0);
      if (samples.size() >= 10000) break;
      if ((long)samples.size() >= o.min_rounds && (t1 - start) >= o.min_time_ms * 1000000LL) break;
    }
    const uint64_t c1 = jb_alloc_count, b1 = jb_alloc_bytes;

    std::vector<long long> sorted = samples;
    std::sort(sorted.begin(), sorted.end());
    const size_t n = sorted.size();
    const double median = n % 2 ? double(sorted[n / 2]) : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    const size_t p90i = std::min(n - 1, size_t(std::ceil(0.9 * n)) - 1);
    const double parsed = double(n) * double(events);

    row["rounds"] = n;
    row["median_ns"] = (long long)std::llround(median);
    row["min_ns"] = sorted.front();
    row["p90_ns"] = sorted[p90i];
    row["ns_per_event"] = median / double(events);
    row["mb_per_s"] = double(bytes) / (median / 1e9) / 1e6;
    row["events_per_s"] = double(events) / (median / 1e9);
    row["alloc_bytes_per_event"] = double(b1 - b0) / parsed;
    row["allocs_per_event"] = double(c1 - c0) / parsed;
    row["gc_count"] = nullptr;
    row["gc_ms"] = nullptr;
  } catch (const std::exception& e) {
    row["error"] = std::string("timed round: ") + e.what();
    row["digest_ok"] = false;
    null_timing();
  }
  return row;
}

std::string fmt(const ojson& v, int prec) {
  if (v.is_null()) return "-";
  char b[64];
  std::snprintf(b, sizeof b, "%.*f", prec, v.get<double>());
  return b;
}

}  // namespace

std::string json_unescape(std::string_view raw) {
  std::string out;
  out.reserve(raw.size());
  auto hex4 = [&](size_t i) -> uint32_t {
    if (i + 4 > raw.size()) fail("truncated \\u escape");
    uint32_t v = 0;
    for (size_t k = 0; k < 4; k++) {
      char c = raw[i + k];
      v <<= 4;
      if (c >= '0' && c <= '9') v |= uint32_t(c - '0');
      else if (c >= 'a' && c <= 'f') v |= uint32_t(c - 'a' + 10);
      else if (c >= 'A' && c <= 'F') v |= uint32_t(c - 'A' + 10);
      else fail("bad \\u escape");
    }
    return v;
  };
  for (size_t i = 0; i < raw.size(); i++) {
    char c = raw[i];
    if (c != '\\') {
      out.push_back(c);
      continue;
    }
    if (++i >= raw.size()) fail("dangling backslash");
    switch (raw[i]) {
      case '"': out.push_back('"'); break;
      case '\\': out.push_back('\\'); break;
      case '/': out.push_back('/'); break;
      case 'b': out.push_back('\b'); break;
      case 'f': out.push_back('\f'); break;
      case 'n': out.push_back('\n'); break;
      case 'r': out.push_back('\r'); break;
      case 't': out.push_back('\t'); break;
      case 'u': {
        uint32_t cp = hex4(i + 1);
        i += 4;
        if (cp >= 0xD800 && cp <= 0xDBFF) {
          if (i + 2 < raw.size() && raw[i + 1] == '\\' && raw[i + 2] == 'u') {
            uint32_t lo = hex4(i + 3);
            if (lo < 0xDC00 || lo > 0xDFFF) fail("lone high surrogate");
            cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
            i += 6;
          } else {
            fail("lone high surrogate");
          }
        } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
          fail("lone low surrogate");
        }
        if (cp < 0x80) out.push_back(char(cp));
        else if (cp < 0x800) {
          out.push_back(char(0xC0 | (cp >> 6)));
          out.push_back(char(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
          out.push_back(char(0xE0 | (cp >> 12)));
          out.push_back(char(0x80 | ((cp >> 6) & 0x3F)));
          out.push_back(char(0x80 | (cp & 0x3F)));
        } else {
          out.push_back(char(0xF0 | (cp >> 18)));
          out.push_back(char(0x80 | ((cp >> 12) & 0x3F)));
          out.push_back(char(0x80 | ((cp >> 6) & 0x3F)));
          out.push_back(char(0x80 | (cp & 0x3F)));
        }
        break;
      }
      default: fail("bad escape");
    }
  }
  return out;
}

int main(int argc, char** argv) {
  try {
    Options o = parse_args(argc, argv);

    Registry all;
    register_simdjson(all);
    register_yyjson(all);
    register_rapidjson(all);
    register_nlohmann(all);
    register_glaze(all);
    register_cjson(all);
    register_boostjson(all);
    register_sajson(all);
    register_jsonc(all);
    register_jsmn(all);
    register_jansson(all);

    if (o.list) {
      for (const auto& p : all) std::cout << p.id << "\n";
      return 0;
    }

    std::vector<const ParserInfo*> selected;
    if (o.parsers.empty()) {
      for (const auto& p : all) selected.push_back(&p);
    } else {
      for (const auto& id : o.parsers) {
        auto it = std::find_if(all.begin(), all.end(), [&](const ParserInfo& p) { return p.id == id; });
        if (it == all.end()) fail("unknown parser: " + id + " (see --list)");
        selected.push_back(&*it);
      }
    }

    const fs::path corpus = o.corpus;
    std::ifstream mf(corpus / "manifest.json");
    if (!mf) fail("cannot read " + (corpus / "manifest.json").string() +
                  " (build it with: python3 jsonBench/corpus/prepare.py)");
    ojson manifest = ojson::parse(mf);

    std::vector<ojson> workloads;
    for (const auto& w : manifest["workloads"]) {
      if (o.workloads.empty() ||
          std::find(o.workloads.begin(), o.workloads.end(), w["name"].get<std::string>()) != o.workloads.end())
        workloads.push_back(w);
    }
    for (const auto& name : o.workloads) {
      if (std::none_of(workloads.begin(), workloads.end(), [&](const ojson& w) { return w["name"] == name; }))
        fail("unknown workload: " + name);
    }

    ojson out;
    out["harness"] = "cpp";
    out["language"] = "C/C++";
    out["runtime"] = std::string(JB_COMPILER) + ", " + JB_FLAGS + ", C++23, single-threaded";
    out["host"] = {{"cpu", cpu_model()}, {"cores", std::thread::hardware_concurrency()}, {"os", os_name()}};
    out["settings"] = {{"warmup_ms", o.warmup_ms}, {"min_time_ms", o.min_time_ms}, {"min_rounds", o.min_rounds}};
    out["corpus_fingerprint"] = manifest["fingerprint"];
    out["results"] = ojson::array();

    for (const auto& w : workloads) {
      Lines lines = load_lines(corpus / w["file"].get<std::string>());
      std::fprintf(stderr, "== %s: %zu events\n", w["name"].get<std::string>().c_str(), lines.size());
      for (const ParserInfo* p : selected) {
        ojson row = run_one(*p, w, lines, o);
        std::fprintf(stderr, "  %-28s %-8s digest=%-5s %9s MB/s %10s ns/ev %9s B/ev %7s allocs/ev%s%s\n",
                     p->id.c_str(), p->mode.c_str(), row["digest_ok"].get<bool>() ? "ok" : "FAIL",
                     fmt(row["mb_per_s"], 1).c_str(), fmt(row["ns_per_event"], 1).c_str(),
                     fmt(row["alloc_bytes_per_event"], 1).c_str(), fmt(row["allocs_per_event"], 2).c_str(),
                     row["error"].is_null() ? "" : "  error: ",
                     row["error"].is_null() ? "" : row["error"].get<std::string>().c_str());
        out["results"].push_back(std::move(row));
      }
    }

    fs::path outp = o.out;
    if (outp.has_parent_path()) fs::create_directories(outp.parent_path());
    std::ofstream of(outp);
    of << out.dump(2) << "\n";
    if (!of) fail("cannot write " + outp.string());
    std::fprintf(stderr, "wrote %s (sink %llu)\n", outp.c_str(), (unsigned long long)g_sink);
    return 0;
  } catch (const std::exception& e) {
    std::fprintf(stderr, "error: %s\n", e.what());
    return 2;
  }
}
