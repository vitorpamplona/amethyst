// Shared types for the C/C++ jsonBench harness. See jsonBench/HARNESS.md for the contract.
#pragma once

#include <charconv>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

// The typed result: owned, fully decoded strings.
struct Event {
  std::string id;
  std::string pubkey;
  uint64_t created_at = 0;
  uint32_t kind = 0;
  std::vector<std::vector<std::string>> tags;
  std::string content;
  std::string sig;
};

// The borrowed result: strings point into the parser's (or the input's) buffers and stay
// valid only until the next parse.
struct BorrowedEvent {
  std::string_view id;
  std::string_view pubkey;
  uint64_t created_at = 0;
  uint32_t kind = 0;
  std::vector<std::vector<std::string_view>> tags;
  std::string_view content;
  std::string_view sig;
};

// The seven NIP-01 keys, for parsers that walk an object's fields in input order.
enum class Field : uint8_t { Id, Pubkey, CreatedAt, Kind, Tags, Content, Sig, Unknown };

inline Field field_of(std::string_view k) noexcept {
  switch (k.size()) {
    case 2: return k == "id" ? Field::Id : Field::Unknown;
    case 3: return k == "sig" ? Field::Sig : Field::Unknown;
    case 4: return k == "kind" ? Field::Kind : (k == "tags" ? Field::Tags : Field::Unknown);
    case 6: return k == "pubkey" ? Field::Pubkey : Field::Unknown;
    case 7: return k == "content" ? Field::Content : Field::Unknown;
    case 10: return k == "created_at" ? Field::CreatedAt : Field::Unknown;
    default: return Field::Unknown;
  }
}

[[noreturn]] inline void fail(const std::string& msg) { throw std::runtime_error(msg); }

// Builds the cross-language digest stream (HARNESS.md "Verification") for one event; the
// harness hashes it. Callers must emit the fields in the contract's order.
class DigestWriter {
 public:
  void str(std::string_view s) {
    buf_.append(s);
    buf_.push_back('\x1F');
  }
  void num(uint64_t v) {
    char tmp[24];
    auto r = std::to_chars(tmp, tmp + sizeof tmp, v);
    buf_.append(tmp, r.ptr);
    buf_.push_back('\x1F');
  }
  void tag_end() { buf_.push_back('\x1E'); }
  void tags_end() { buf_.push_back('\x1D'); }
  void event_end() { buf_.push_back('\x1C'); }
  std::string& buffer() { return buf_; }

 private:
  std::string buf_;
};

template <class E>
void digest_event(DigestWriter& d, const E& e) {
  d.str(e.id);
  d.str(e.pubkey);
  d.num(e.created_at);
  d.num(e.kind);
  for (const auto& tag : e.tags) {
    for (const auto& v : tag) d.str(v);
    d.tag_end();
  }
  d.tags_end();
  d.str(e.content);
  d.str(e.sig);
  d.event_end();
}

// One (parser, workload) instance. The constructor receives the raw lines and prepares the
// parser's preferred input form (untimed). digest() parses event i once and feeds the
// digest, throwing on any failure. round() parses every event once and returns the sink.
class Runner {
 public:
  virtual ~Runner() = default;
  virtual void digest(size_t i, DigestWriter& d) = 0;
  virtual uint64_t round() = 0;
};

using Lines = std::vector<std::string>;

struct ParserInfo {
  std::string id;
  std::string library;
  std::string version;
  std::string license;
  std::string mode;   // typed | borrowed | dom | baseline
  std::string input;  // string | bytes | bytes-padded | bytes-copy
  std::string notes;
  std::unique_ptr<Runner> (*make)(const Lines&);
};

using Registry = std::vector<ParserInfo>;

// Decodes the body of a JSON string literal (between the quotes) into UTF-8, including
// \uXXXX surrogate pairs. Used only by the harness for tokenizer-only libraries (jsmn)
// that hand back raw slices; never inside a timed loop of a parser that decodes itself.
std::string json_unescape(std::string_view raw);

// Each library's translation unit registers its parsers.
void register_simdjson(Registry&);
void register_yyjson(Registry&);
void register_rapidjson(Registry&);
void register_nlohmann(Registry&);
void register_glaze(Registry&);
void register_cjson(Registry&);
void register_boostjson(Registry&);
void register_sajson(Registry&);
void register_jsonc(Registry&);
void register_jsmn(Registry&);
void register_jansson(Registry&);

// Library refs from deps.txt, injected by CMake as JB_REF_<name>.
#define JB_STR2(x) #x
#define JB_STR(x) JB_STR2(x)
