// yyjson: immutable DOM (copying and in-situ), a pool-allocator DOM, and typed/borrowed
// extraction from the DOM.
#include "yyjson.h"

#include <cstring>

#include "common.hpp"

namespace {

constexpr const char* kLib = "yyjson";
constexpr const char* kLicense = "MIT";

std::string_view sv(yyjson_val* v) {
  if (!yyjson_is_str(v)) fail("expected string");
  return {yyjson_get_str(v), yyjson_get_len(v)};
}

uint64_t u64(yyjson_val* v) {
  if (!yyjson_is_uint(v)) fail("expected unsigned integer");
  return yyjson_get_uint(v);
}

yyjson_doc* read_or_throw(const char* data, size_t len, yyjson_read_flag flg, const yyjson_alc* alc) {
  yyjson_read_err err;
  yyjson_doc* doc = yyjson_read_opts(const_cast<char*>(data), len, flg, alc, &err);
  if (!doc) fail(std::string("yyjson: ") + err.msg);
  return doc;
}

void digest_dom(yyjson_val* root, DigestWriter& d) {
  d.str(sv(yyjson_obj_get(root, "id")));
  d.str(sv(yyjson_obj_get(root, "pubkey")));
  d.num(u64(yyjson_obj_get(root, "created_at")));
  d.num(u64(yyjson_obj_get(root, "kind")));
  yyjson_val* tags = yyjson_obj_get(root, "tags");
  if (!yyjson_is_arr(tags)) fail("tags not an array");
  size_t i, n;
  yyjson_val* tag;
  yyjson_arr_foreach(tags, i, n, tag) {
    size_t j, m;
    yyjson_val* el;
    yyjson_arr_foreach(tag, j, m, el) d.str(sv(el));
    d.tag_end();
  }
  d.tags_end();
  d.str(sv(yyjson_obj_get(root, "content")));
  d.str(sv(yyjson_obj_get(root, "sig")));
  d.event_end();
}

struct DocFree {
  void operator()(yyjson_doc* d) const { yyjson_doc_free(d); }
};

// A borrowed event kept alive together with the document its views point into.
struct DocAndView {
  yyjson_doc* doc = nullptr;
  BorrowedEvent e;
  ~DocAndView() { yyjson_doc_free(doc); }
};

inline uint64_t fold_dom(yyjson_val* root) {
  return yyjson_get_uint(yyjson_obj_get(root, "kind")) + yyjson_arr_size(yyjson_obj_get(root, "tags"));
}

template <class E>
void extract(yyjson_val* root, E& e) {
  using Str = std::remove_cvref_t<decltype(e.id)>;
  yyjson_obj_iter it = yyjson_obj_iter_with(root);
  yyjson_val* key;
  while ((key = yyjson_obj_iter_next(&it))) {
    yyjson_val* v = yyjson_obj_iter_get_val(key);
    switch (field_of({yyjson_get_str(key), yyjson_get_len(key)})) {
      case Field::Id: e.id = Str(sv(v)); break;
      case Field::Pubkey: e.pubkey = Str(sv(v)); break;
      case Field::CreatedAt: e.created_at = u64(v); break;
      case Field::Kind: e.kind = uint32_t(u64(v)); break;
      case Field::Tags: {
        e.tags.reserve(yyjson_arr_size(v));
        size_t i, n;
        yyjson_val* tag;
        yyjson_arr_foreach(v, i, n, tag) {
          auto& t = e.tags.emplace_back();
          t.reserve(yyjson_arr_size(tag));
          size_t j, m;
          yyjson_val* el;
          yyjson_arr_foreach(tag, j, m, el) t.emplace_back(sv(el));
        }
        break;
      }
      case Field::Content: e.content = Str(sv(v)); break;
      case Field::Sig: e.sig = Str(sv(v)); break;
      case Field::Unknown: fail("unexpected key");
    }
  }
}

// yyjson_read: the library copies the input into its own buffer, then builds the DOM.
class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    yyjson_doc* doc = read_or_throw(lines_[i].data(), lines_[i].size(), 0, nullptr);
    try {
      digest_dom(yyjson_doc_get_root(doc), d);
    } catch (...) {
      yyjson_doc_free(doc);
      throw;
    }
    yyjson_doc_free(doc);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      yyjson_doc* doc = yyjson_read(l.data(), l.size(), 0);
      sink += fold_dom(yyjson_doc_get_root(doc));
      yyjson_doc_free(doc);
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<yyjson_doc, DocFree>(lines_.size(), [&](size_t i) {
      return read_or_throw(lines_[i].data(), lines_[i].size(), 0, nullptr);
    });
  }

 private:
  const Lines& lines_;
};

// YYJSON_READ_INSITU: parse in a mutable, padded scratch copy; the copy is timed.
class InsituRunner final : public Runner {
 public:
  explicit InsituRunner(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    buf_.assign(max + YYJSON_PADDING_SIZE, 0);
  }
  yyjson_doc* parse(const std::string& l) {
    std::memcpy(buf_.data(), l.data(), l.size());
    std::memset(buf_.data() + l.size(), 0, YYJSON_PADDING_SIZE);
    return read_or_throw(buf_.data(), l.size(), YYJSON_READ_INSITU, nullptr);
  }
  void digest(size_t i, DigestWriter& d) override {
    yyjson_doc* doc = parse(lines_[i]);
    try {
      digest_dom(yyjson_doc_get_root(doc), d);
    } catch (...) {
      yyjson_doc_free(doc);
      throw;
    }
    yyjson_doc_free(doc);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      yyjson_doc* doc = parse(l);
      sink += fold_dom(yyjson_doc_get_root(doc));
      yyjson_doc_free(doc);
    }
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<char> buf_;
};

// A fixed pool allocator (yyjson_alc_pool_init) over one reused buffer: zero mallocs.
class PoolRunner final : public Runner {
 public:
  explicit PoolRunner(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    // A document needs about len + padding for its string copy plus 16 bytes per value;
    // a value is at least two input bytes, so 16x the line length is a safe upper bound.
    pool_.resize(max * 16 + 4096);
    yyjson_alc_pool_init(&alc_, pool_.data(), pool_.size());
  }
  void digest(size_t i, DigestWriter& d) override {
    yyjson_doc* doc = read_or_throw(lines_[i].data(), lines_[i].size(), 0, &alc_);
    try {
      digest_dom(yyjson_doc_get_root(doc), d);
    } catch (...) {
      yyjson_doc_free(doc);
      throw;
    }
    yyjson_doc_free(doc);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      yyjson_doc* doc = yyjson_read_opts(const_cast<char*>(l.data()), l.size(), 0, &alc_, nullptr);
      sink += fold_dom(yyjson_doc_get_root(doc));
      yyjson_doc_free(doc);
    }
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<char> pool_;
  yyjson_alc alc_{};
};

// yyjson_read, then extract into Event (owned) or BorrowedEvent (views into the doc).
template <class E>
class TypedRunner final : public Runner {
 public:
  explicit TypedRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    yyjson_doc* doc = read_or_throw(lines_[i].data(), lines_[i].size(), 0, nullptr);
    try {
      E e;
      extract(yyjson_doc_get_root(doc), e);
      digest_event(d, e);
    } catch (...) {
      yyjson_doc_free(doc);
      throw;
    }
    yyjson_doc_free(doc);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      yyjson_doc* doc = yyjson_read(l.data(), l.size(), 0);
      E e;
      extract(yyjson_doc_get_root(doc), e);
      sink += e.kind + e.tags.size() + e.content.size();
      yyjson_doc_free(doc);  // a BorrowedEvent dies with the doc
    }
    return sink;
  }
  // typed keeps the Event (the doc is freed); borrowed keeps the doc with its views.
  std::unique_ptr<Kept> keep_all() override {
    if constexpr (std::is_same_v<E, Event>) {
      return keep_each<Event>(lines_.size(), [&](size_t i) {
        yyjson_doc* doc = read_or_throw(lines_[i].data(), lines_[i].size(), 0, nullptr);
        auto e = std::make_unique<Event>();
        extract(yyjson_doc_get_root(doc), *e);
        yyjson_doc_free(doc);
        return e.release();
      });
    } else {
      return keep_each<DocAndView>(lines_.size(), [&](size_t i) {
        auto k = std::make_unique<DocAndView>();
        k->doc = read_or_throw(lines_[i].data(), lines_[i].size(), 0, nullptr);
        extract(yyjson_doc_get_root(k->doc), k->e);
        return k.release();
      });
    }
  }

 private:
  const Lines& lines_;
};

template <class R>
std::unique_ptr<Runner> make(const Lines& l) {
  return std::make_unique<R>(l);
}

}  // namespace

void register_yyjson(Registry& r) {
  const std::string ver = JB_REF_yyjson;
  const std::string in = "yyjson_read(frame.data(), frame.size()) reads the std::string as is (the library "
                         "copies it into the doc's own padded buffer, timed). ";
  r.push_back({"yyjson-dom", kLib, ver, kLicense, "dom", "string",
               in + "Immutable DOM; each yyjson_doc is independent, so retained keeps the docs", make<DomRunner>});
  r.push_back({"yyjson-insitu", kLib, ver, kLicense, "dom", "bytes-copy",
               "YYJSON_READ_INSITU needs a mutable buffer with YYJSON_PADDING_SIZE zero bytes: each parse memcpys "
               "the frame into one reused scratch buffer and zeroes the padding (timed). Retained heap null: the "
               "doc's strings point into the reused scratch",
               make<InsituRunner>});
  r.push_back({"yyjson-dom-pool", kLib, ver, kLicense, "dom", "string",
               in + "yyjson_alc_pool_init over one reused buffer (no malloc per parse). Retained heap null: the "
                    "doc lives in the reused pool",
               make<PoolRunner>});
  r.push_back({"yyjson-typed", kLib, ver, kLicense, "typed", "string",
               in + "Then copy fields into Event (std::string) and free the doc", make<TypedRunner<Event>>});
  r.push_back({"yyjson-borrowed", kLib, ver, kLicense, "borrowed", "string",
               in + "Then string_views into the doc (valid until yyjson_doc_free); retained keeps each doc "
                    "together with its BorrowedEvent",
               make<TypedRunner<BorrowedEvent>>});
}
