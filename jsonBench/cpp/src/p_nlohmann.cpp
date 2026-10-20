// nlohmann/json: json::parse DOM, json::parse(...).get<Event>(), and a SAX handler.
#include <nlohmann/json.hpp>

#include "common.hpp"

using nlohmann::json;

NLOHMANN_DEFINE_TYPE_NON_INTRUSIVE(Event, id, pubkey, created_at, kind, tags, content, sig)

namespace {

constexpr const char* kLib = "nlohmann/json";
constexpr const char* kLicense = "MIT";

void digest_dom(const json& j, DigestWriter& d) {
  d.str(j.at("id").get_ref<const std::string&>());
  d.str(j.at("pubkey").get_ref<const std::string&>());
  d.num(j.at("created_at").get<uint64_t>());
  d.num(j.at("kind").get<uint64_t>());
  for (const auto& tag : j.at("tags")) {
    for (const auto& el : tag) d.str(el.get_ref<const std::string&>());
    d.tag_end();
  }
  d.tags_end();
  d.str(j.at("content").get_ref<const std::string&>());
  d.str(j.at("sig").get_ref<const std::string&>());
  d.event_end();
}

class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override { digest_dom(json::parse(lines_[i]), d); }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      const json j = json::parse(l);
      sink += j.at("kind").get<uint64_t>() + j.at("tags").size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<json>(lines_.size(), [&](size_t i) { return new json(json::parse(lines_[i])); });
  }

 private:
  const Lines& lines_;
};

class GetRunner final : public Runner {
 public:
  explicit GetRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    Event e = json::parse(lines_[i]).get<Event>();
    digest_event(d, e);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      Event e = json::parse(l).get<Event>();
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<Event>(lines_.size(), [&](size_t i) { return new Event(json::parse(lines_[i]).get<Event>()); });
  }

 private:
  const Lines& lines_;
};

struct EventSax final : nlohmann::json_sax<json> {
  Event* e = nullptr;
  int depth = 0;
  Field cur = Field::Unknown;
  std::vector<std::string>* tag = nullptr;
  std::string error;

  bool num(uint64_t v) {
    if (depth != 1) return false;
    if (cur == Field::CreatedAt) e->created_at = v;
    else if (cur == Field::Kind) e->kind = uint32_t(v);
    else return false;
    return true;
  }
  bool null() override { return false; }
  bool boolean(bool) override { return false; }
  bool number_integer(number_integer_t v) override { return v >= 0 && num(uint64_t(v)); }
  bool number_unsigned(number_unsigned_t v) override { return num(v); }
  bool number_float(number_float_t, const string_t&) override { return false; }
  bool binary(binary_t&) override { return false; }
  bool string(string_t& s) override {
    if (depth == 1) {
      switch (cur) {
        case Field::Id: e->id = std::move(s); return true;
        case Field::Pubkey: e->pubkey = std::move(s); return true;
        case Field::Content: e->content = std::move(s); return true;
        case Field::Sig: e->sig = std::move(s); return true;
        default: return false;
      }
    }
    if (depth == 3 && tag) {
      tag->push_back(std::move(s));
      return true;
    }
    return false;
  }
  bool start_object(std::size_t) override { return ++depth == 1; }
  bool key(string_t& k) override {
    cur = field_of(k);
    return cur != Field::Unknown;
  }
  bool end_object() override {
    --depth;
    return true;
  }
  bool start_array(std::size_t) override {
    ++depth;
    if (cur != Field::Tags) return false;
    if (depth == 3) tag = &e->tags.emplace_back();
    return depth <= 3;
  }
  bool end_array() override {
    if (depth == 3) tag = nullptr;
    --depth;
    return true;
  }
  bool parse_error(std::size_t, const std::string&, const nlohmann::detail::exception& ex) override {
    error = ex.what();
    return false;
  }
};

class SaxRunner final : public Runner {
 public:
  explicit SaxRunner(const Lines& l) : lines_(l) {}
  static void parse(const std::string& l, Event& e) {
    EventSax h;
    h.e = &e;
    if (!json::sax_parse(l, &h)) fail("nlohmann sax: " + (h.error.empty() ? "rejected" : h.error));
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
};

template <class R>
std::unique_ptr<Runner> make(const Lines& l) {
  return std::make_unique<R>(l);
}

}  // namespace

void register_nlohmann(Registry& r) {
  const std::string ver = JB_REF_nlohmann;
  const std::string in = "json::parse / sax_parse take the frame std::string directly (iterator input adapter, no copy); ";
  r.push_back({"nlohmann-dom", kLib, ver, kLicense, "dom", "string", in + "std::map objects", make<DomRunner>});
  r.push_back({"nlohmann-typed", kLib, ver, kLicense, "typed", "string",
               in + "json::parse(frame).get<Event>() via NLOHMANN_DEFINE_TYPE_NON_INTRUSIVE (DOM, then copy)",
               make<GetRunner>});
  r.push_back({"nlohmann-sax-typed", kLib, ver, kLicense, "typed", "string",
               in + "json_sax handler moving the lexer's strings into Event (their capacity grew by push_back)",
               make<SaxRunner>});
}
