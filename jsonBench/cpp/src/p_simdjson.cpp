// simdjson: On-Demand into owned and borrowed events, and the DOM API.
#include "simdjson.h"

#include "common.hpp"

using namespace simdjson;

namespace {

constexpr const char* kLib = "simdjson";
constexpr const char* kLicense = "Apache-2.0 OR MIT";

std::vector<padded_string> pad_all(const Lines& lines) {
  std::vector<padded_string> out;
  out.reserve(lines.size());
  for (const auto& l : lines) out.emplace_back(l);
  return out;
}

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
  explicit OnDemandRunner(const Lines& lines) : docs_(pad_all(lines)) {}

  void digest(size_t i, DigestWriter& d) override {
    E e;
    ondemand::document doc = parser_.iterate(docs_[i]);
    read_ondemand(doc, e);
    digest_event(d, e);
  }

  uint64_t round() override {
    uint64_t sink = 0;
    for (auto& s : docs_) {
      E e;
      ondemand::document doc = parser_.iterate(s);
      read_ondemand(doc, e);
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }

 private:
  std::vector<padded_string> docs_;
  ondemand::parser parser_;
};

class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& lines) : docs_(pad_all(lines)) {}

  void digest(size_t i, DigestWriter& d) override {
    dom::element root = parser_.parse(docs_[i]);
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
    for (auto& s : docs_) {
      dom::element root = parser_.parse(s);
      sink += uint64_t(root["kind"].get_uint64()) + dom::array(root["tags"]).size();
    }
    return sink;
  }

 private:
  std::vector<padded_string> docs_;
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
  r.push_back({"simdjson-ondemand-typed", kLib, ver, kLicense, "typed", "bytes-padded",
               impl + "On-Demand field walk, strings copied into std::string; one reused ondemand::parser",
               make<OnDemandRunner<Event>>});
  r.push_back({"simdjson-ondemand-borrowed", kLib, ver, kLicense, "borrowed", "bytes-padded",
               impl + "On-Demand; string_views into the parser's string buffer (valid until next iterate)",
               make<OnDemandRunner<BorrowedEvent>>});
  r.push_back({"simdjson-dom", kLib, ver, kLicense, "dom", "bytes-padded",
               impl + "dom::parser::parse, one reused parser (tape + string buffer reused)", make<DomRunner>});
}
