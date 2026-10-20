// RapidJSON: Document::Parse, ParseInsitu, a stack-buffer pool DOM, and a SAX handler that
// fills Event directly.
#include <cstring>

#include "common.hpp"
#include "rapidjson/document.h"
#include "rapidjson/reader.h"

namespace rj = rapidjson;

namespace {

constexpr const char* kLib = "rapidjson";
constexpr const char* kLicense = "MIT";

std::string_view sv(const rj::Value& v) {
  if (!v.IsString()) fail("expected string");
  return {v.GetString(), v.GetStringLength()};
}

const rj::Value& member(const rj::Value& o, const char* k) {
  auto it = o.FindMember(k);
  if (it == o.MemberEnd()) fail(std::string("missing ") + k);
  return it->value;
}

uint64_t u64(const rj::Value& v) {
  if (!v.IsUint64()) fail("expected unsigned integer");
  return v.GetUint64();
}

template <class Doc>
void check(const Doc& d) {
  if (d.HasParseError()) fail("rapidjson parse error " + std::to_string(d.GetParseError()));
}

void digest_dom(const rj::Value& root, DigestWriter& d) {
  d.str(sv(member(root, "id")));
  d.str(sv(member(root, "pubkey")));
  d.num(u64(member(root, "created_at")));
  d.num(u64(member(root, "kind")));
  for (const auto& tag : member(root, "tags").GetArray()) {
    for (const auto& el : tag.GetArray()) d.str(sv(el));
    d.tag_end();
  }
  d.tags_end();
  d.str(sv(member(root, "content")));
  d.str(sv(member(root, "sig")));
  d.event_end();
}

inline uint64_t fold_dom(const rj::Value& root) {
  return root["kind"].GetUint() + root["tags"].Size();
}

class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    rj::Document doc;
    doc.Parse(lines_[i].c_str());
    check(doc);
    digest_dom(doc, d);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      rj::Document doc;
      doc.Parse(l.c_str());
      sink += fold_dom(doc);
    }
    return sink;
  }
  // A Document owns its MemoryPoolAllocator (and the 64 KiB chunk), so each one is independent.
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<rj::Document>(lines_.size(), [&](size_t i) {
      auto doc = std::make_unique<rj::Document>();
      doc->Parse(lines_[i].c_str());
      check(*doc);
      return doc.release();
    });
  }

 private:
  const Lines& lines_;
};

class InsituRunner final : public Runner {
 public:
  explicit InsituRunner(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    buf_.resize(max + 1);
  }
  char* copy(const std::string& l) {
    std::memcpy(buf_.data(), l.data(), l.size());
    buf_[l.size()] = 0;
    return buf_.data();
  }
  void digest(size_t i, DigestWriter& d) override {
    rj::Document doc;
    doc.ParseInsitu(copy(lines_[i]));
    check(doc);
    digest_dom(doc, d);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      rj::Document doc;
      doc.ParseInsitu(copy(l));
      sink += fold_dom(doc);
    }
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<char> buf_;
};

// The documented low-allocation idiom: a MemoryPoolAllocator over a reused user buffer for
// values, and another for the parse stack.
class PoolRunner final : public Runner {
  using PoolDoc = rj::GenericDocument<rj::UTF8<>, rj::MemoryPoolAllocator<>, rj::MemoryPoolAllocator<>>;

 public:
  explicit PoolRunner(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    // A value is 16 bytes and needs at least two input bytes; the parse stack holds at most
    // every value of the event and grows by 1.5x inside its pool, so 16x is ample for both.
    values_.resize(max * 16 + 65536);
    stack_.resize(max * 16 + 65536);
  }
  template <class F>
  uint64_t with_doc(const std::string& l, F&& f) {
    rj::MemoryPoolAllocator<> va(values_.data(), values_.size());
    rj::MemoryPoolAllocator<> sa(stack_.data(), stack_.size());
    PoolDoc doc(&va, 1024, &sa);  // initial stack must fit the stack pool's first chunk
    doc.Parse(l.c_str());
    return f(doc);
  }
  void digest(size_t i, DigestWriter& d) override {
    with_doc(lines_[i], [&](PoolDoc& doc) {
      check(doc);
      digest_dom(doc, d);
      return uint64_t(0);
    });
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) sink += with_doc(l, [](PoolDoc& doc) { return fold_dom(doc); });
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<char> values_;
  std::vector<char> stack_;
};

struct EventHandler : rj::BaseReaderHandler<rj::UTF8<>, EventHandler> {
  Event* e = nullptr;
  int depth = 0;  // object/array nesting
  Field cur = Field::Unknown;
  std::vector<std::string>* tag = nullptr;

  bool Default() { return false; }  // null, bool, double: not in a Nostr event
  bool Num(uint64_t v) {
    if (depth != 1) return false;
    if (cur == Field::CreatedAt) e->created_at = v;
    else if (cur == Field::Kind) e->kind = uint32_t(v);
    else return false;
    return true;
  }
  bool Uint(unsigned v) { return Num(v); }
  bool Uint64(uint64_t v) { return Num(v); }
  bool Int(int v) { return v >= 0 && Num(uint64_t(v)); }
  bool Int64(int64_t v) { return v >= 0 && Num(uint64_t(v)); }
  bool String(const char* s, rj::SizeType n, bool) {
    if (depth == 1) {
      switch (cur) {
        case Field::Id: e->id.assign(s, n); return true;
        case Field::Pubkey: e->pubkey.assign(s, n); return true;
        case Field::Content: e->content.assign(s, n); return true;
        case Field::Sig: e->sig.assign(s, n); return true;
        default: return false;
      }
    }
    if (depth == 3 && tag) {
      tag->emplace_back(s, n);
      return true;
    }
    return false;
  }
  bool Key(const char* s, rj::SizeType n, bool) {
    cur = field_of({s, n});
    return cur != Field::Unknown;
  }
  bool StartObject() { return ++depth == 1; }
  bool EndObject(rj::SizeType) {
    --depth;
    return true;
  }
  bool StartArray() {
    ++depth;
    if (cur != Field::Tags) return false;
    if (depth == 3) tag = &e->tags.emplace_back();
    return depth <= 3;
  }
  bool EndArray(rj::SizeType) {
    if (depth == 3) tag = nullptr;
    --depth;
    return true;
  }
};

class SaxRunner final : public Runner {
 public:
  explicit SaxRunner(const Lines& l) : lines_(l) {}
  void parse(const std::string& l, Event& e) {
    EventHandler h;
    h.e = &e;
    rj::StringStream ss(l.c_str());
    if (!reader_.Parse(ss, h)) fail("rapidjson SAX error " + std::to_string(reader_.GetParseErrorCode()));
  }
  void digest(size_t i, DigestWriter& d) override {
    Event e;
    parse(lines_[i], e);
    digest_event(d, e);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      Event e;
      parse(l, e);
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<Event>(lines_.size(), [&](size_t i) {
      auto e = std::make_unique<Event>();
      parse(lines_[i], *e);
      return e.release();
    });
  }

 private:
  const Lines& lines_;
  rj::Reader reader_;  // reused: keeps its parse stack between events
};

template <class R>
std::unique_ptr<Runner> make(const Lines& l) {
  return std::make_unique<R>(l);
}

}  // namespace

void register_rapidjson(Registry& r) {
  const std::string ver = JB_REF_rapidjson;
  const std::string simd = "built with RAPIDJSON_SSE42; ";
  const std::string cstr = "reads frame.c_str() (std::string is NUL-terminated for free); ";
  r.push_back({"rapidjson-dom", kLib, ver, kLicense, "dom", "string",
               simd + cstr + "fresh Document per event: Parse(const char*), default MemoryPoolAllocator (mallocs a 64 KiB chunk per Document, which a kept Document keeps). Parse(str, len) goes through a MemoryStream without the SSE4.2 string scan and measured ~1.6x slower",
               make<DomRunner>});
  r.push_back({"rapidjson-insitu", kLib, ver, kLicense, "dom", "bytes-copy",
               simd + "ParseInsitu needs a mutable NUL-terminated buffer: each parse memcpys the frame into one reused scratch buffer (timed); strings point into it. Retained heap null: the scratch is reused",
               make<InsituRunner>});
  r.push_back({"rapidjson-dom-pool", kLib, ver, kLicense, "dom", "string",
               simd + cstr + "Parse(const char*) with MemoryPoolAllocators over reused user buffers (no malloc per parse). Retained heap null: values live in the reused pool",
               make<PoolRunner>});
  r.push_back({"rapidjson-sax-typed", kLib, ver, kLicense, "typed", "string",
               simd + cstr + "Reader over a StringStream + SAX handler filling Event; one reused Reader", make<SaxRunner>});
}
