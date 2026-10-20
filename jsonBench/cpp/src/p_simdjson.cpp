// simdjson: On-Demand into owned and borrowed events, and the DOM API.
#include "simdjson.h"

#include <cstring>

#include "common.hpp"

using namespace simdjson;

namespace {

constexpr const char* kLib = "simdjson";
constexpr const char* kLicense = "Apache-2.0 OR MIT";

// The frame is an exact-size std::string, so capacity() - size() < SIMDJSON_PADDING and
// ondemand::parser::iterate(const std::string&) would return INSUFFICIENT_PADDING. On-Demand
// has no copying entry point for it, so every parse copies the frame into one reused buffer
// with SIMDJSON_PADDING spare bytes (zeroed once; simdjson does not care what they hold).
class PaddedScratch {
 public:
  explicit PaddedScratch(const Lines& lines) {
    size_t max = 0;
    for (const auto& l : lines) max = std::max(max, l.size());
    buf_.assign(max + SIMDJSON_PADDING, 0);
  }
  padded_string_view copy(const std::string& l) {
    std::memcpy(buf_.data(), l.data(), l.size());
    return padded_string_view(buf_.data(), l.size(), buf_.size());
  }

 private:
  std::vector<char> buf_;
};

template <class E>
void read_ondemand(ondemand::document& doc, E& e) {
  using Str = std::remove_cvref_t<decltype(e.id)>;
  for (auto field : doc.get_object()) {
    ondemand::value v = field.value();
    switch (field_of(field.escaped_key())) {
      case Field::Id: e.id = Str(std::string_view(v.get_string())); break;
      case Field::Pubkey: e.pubkey = Str(std::string_view(v.get_string())); break;
      case Field::CreatedAt: e.created_at = v.get_uint64(); break;
      case Field::Kind: e.kind = uint32_t(uint64_t(v.get_uint64())); break;
      case Field::Tags:
        for (auto tag : v.get_array()) {
          auto& t = e.tags.emplace_back();
          for (auto el : tag.get_array()) t.emplace_back(std::string_view(el.get_string()));
        }
        break;
      case Field::Content: e.content = Str(std::string_view(v.get_string())); break;
      case Field::Sig: e.sig = Str(std::string_view(v.get_string())); break;
      case Field::Unknown: fail("unexpected key");
    }
  }
}

template <class E>
class OnDemandRunner final : public Runner {
 public:
  explicit OnDemandRunner(const Lines& lines) : lines_(lines), scratch_(lines) {}

  void parse(const std::string& l, E& e) {
    ondemand::document doc = parser_.iterate(scratch_.copy(l));
    read_ondemand(doc, e);
  }

  void digest(size_t i, DigestWriter& d) override {
    E e;
    parse(lines_[i], e);
    digest_event(d, e);
  }

  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      E e;
      parse(l, e);
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }

  // Borrowed views point into the reused parser's string buffer: nothing to keep.
  std::unique_ptr<Kept> keep_all() override {
    if constexpr (std::is_same_v<E, Event>) {
      return keep_each<Event>(lines_.size(), [&](size_t i) {
        auto e = std::make_unique<Event>();
        parse(lines_[i], *e);
        return e.release();
      });
    } else {
      return nullptr;
    }
  }

 private:
  const Lines& lines_;
  PaddedScratch scratch_;
  ondemand::parser parser_;
};

// dom::parser::parse(const std::string&) is simdjson's own std::string entry point: it sees
// capacity() - size() < SIMDJSON_PADDING and memcpys the frame into the parser's reused
// padded buffer (loaded_bytes), then builds the tape in the parser's reused tape/string
// buffers. The element is a view into them, valid until the next parse.
class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& lines) : lines_(lines) {}

  void digest(size_t i, DigestWriter& d) override {
    dom::element root = parser_.parse(lines_[i]);
    d.str(root["id"].get_string());
    d.str(root["pubkey"].get_string());
    d.num(root["created_at"].get_uint64());
    d.num(root["kind"].get_uint64());
    for (dom::element tag : dom::array(root["tags"])) {
      for (dom::element el : dom::array(tag)) d.str(el.get_string());
      d.tag_end();
    }
    d.tags_end();
    d.str(root["content"].get_string());
    d.str(root["sig"].get_string());
    d.event_end();
  }

  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      dom::element root = parser_.parse(l);
      sink += uint64_t(root["kind"].get_uint64()) + dom::array(root["tags"]).size();
    }
    return sink;
  }

 private:
  const Lines& lines_;
  dom::parser parser_;
};

template <class R>
std::unique_ptr<Runner> make(const Lines& l) {
  return std::make_unique<R>(l);
}

}  // namespace

void register_simdjson(Registry& r) {
  const std::string ver = JB_REF_simdjson;
  std::string impl = std::string("SIMD kernel: ") + get_active_implementation()->name() + "; ";
  const std::string pad =
      "the frame std::string has no spare capacity, so iterate(const std::string&) refuses it "
      "(INSUFFICIENT_PADDING); each parse memcpys it into one reused buffer with SIMDJSON_PADDING "
      "spare bytes (timed) and iterates a padded_string_view of that. ";
  r.push_back({"simdjson-ondemand-typed", kLib, ver, kLicense, "typed", "string→bytes-padded",
               impl + pad + "On-Demand field walk, strings copied into std::string; one reused ondemand::parser",
               make<OnDemandRunner<Event>>});
  r.push_back({"simdjson-ondemand-borrowed", kLib, ver, kLicense, "borrowed", "string→bytes-padded",
               impl + pad +
                   "string_views into the reused parser's string buffer (valid until next iterate); "
                   "retained heap null: the views live in the reused parser",
               make<OnDemandRunner<BorrowedEvent>>});
  r.push_back({"simdjson-dom", kLib, ver, kLicense, "dom", "string→bytes-padded",
               impl +
                   "dom::parser::parse(const std::string&): the frame lacks SIMDJSON_PADDING spare capacity, so "
                   "simdjson memcpys it into the parser's reused padded buffer (timed); one reused parser "
                   "(tape + string buffer reused). Retained heap null: the element is a view into the reused parser",
               make<DomRunner>});
}
