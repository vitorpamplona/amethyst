// Boost.JSON (header-only build via boost/json/src.hpp): parse() DOM, a reused parser over
// a monotonic arena, and value_to<Event> through Boost.Describe.
#include <boost/describe.hpp>
#include <boost/json.hpp>
#include <boost/json/src.hpp>
#include <optional>

#include "common.hpp"

BOOST_DESCRIBE_STRUCT(Event, (), (id, pubkey, created_at, kind, tags, content, sig))

namespace json = boost::json;

namespace {

constexpr const char* kLib = "Boost.JSON";
constexpr const char* kLicense = "BSL-1.0";

std::string_view sv(const json::value& v) {
  const json::string& s = v.as_string();
  return {s.data(), s.size()};
}

uint64_t u64(const json::value& v) { return v.to_number<uint64_t>(); }

void digest_dom(const json::value& root, DigestWriter& d) {
  const json::object& o = root.as_object();
  d.str(sv(o.at("id")));
  d.str(sv(o.at("pubkey")));
  d.num(u64(o.at("created_at")));
  d.num(u64(o.at("kind")));
  for (const auto& tag : o.at("tags").as_array()) {
    for (const auto& el : tag.as_array()) d.str(sv(el));
    d.tag_end();
  }
  d.tags_end();
  d.str(sv(o.at("content")));
  d.str(sv(o.at("sig")));
  d.event_end();
}

inline uint64_t fold_dom(const json::value& v) {
  const json::object& o = v.get_object();
  return uint64_t(o.find("kind")->value().get_int64()) + o.find("tags")->value().get_array().size();
}

class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override { digest_dom(json::parse(lines_[i]), d); }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) sink += fold_dom(json::parse(l));
    return sink;
  }
  // A value on the default resource owns all its storage.
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<json::value>(lines_.size(), [&](size_t i) { return new json::value(json::parse(lines_[i])); });
  }

 private:
  const Lines& lines_;
};

// The documented fast path: one reused json::parser (keeps its temporary buffers) and a
// monotonic_resource over a reused buffer, released after each event.
class PoolRunner final : public Runner {
 public:
  explicit PoolRunner(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    arena_.resize(max * 16 + 4096);
    mr_.emplace(arena_.data(), arena_.size());
  }
  template <class F>
  uint64_t with_value(const std::string& l, F&& f) {
    mr_->release();  // the previous event's value is gone; rewind the arena
    parser_.reset(&*mr_);
    parser_.write(l);
    json::value v = parser_.release();
    return f(v);
  }
  void digest(size_t i, DigestWriter& d) override {
    with_value(lines_[i], [&](const json::value& v) {
      digest_dom(v, d);
      return uint64_t(0);
    });
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) sink += with_value(l, [](const json::value& v) { return fold_dom(v); });
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<unsigned char> arena_;
  std::optional<json::monotonic_resource> mr_;
  json::parser parser_;
};

class TypedRunner final : public Runner {
 public:
  explicit TypedRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    Event e = json::value_to<Event>(json::parse(lines_[i]));
    digest_event(d, e);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      Event e = json::value_to<Event>(json::parse(l));
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<Event>(lines_.size(),
                            [&](size_t i) { return new Event(json::value_to<Event>(json::parse(lines_[i]))); });
  }

 private:
  const Lines& lines_;
};

template <class R>
std::unique_ptr<Runner> make(const Lines& l) {
  return std::make_unique<R>(l);
}

}  // namespace

void register_boostjson(Registry& r) {
  const std::string ver = JB_REF_boost_json;
  const std::string in = "json::parse / parser::write take the frame as a string_view (no copy); ";
  r.push_back({"boostjson-dom", kLib, ver, kLicense, "dom", "string",
               in + "json::parse with the default heap resource; each value owns its storage", make<DomRunner>});
  r.push_back({"boostjson-dom-pool", kLib, ver, kLicense, "dom", "string",
               in + "reused json::parser + monotonic_resource over a reused buffer (no malloc per parse). Retained "
                    "heap null: the value lives in the reused arena",
               make<PoolRunner>});
  r.push_back({"boostjson-typed", kLib, ver, kLicense, "typed", "string",
               in + "json::value_to<Event>(json::parse(frame)) via BOOST_DESCRIBE_STRUCT (DOM, then copy)",
               make<TypedRunner>});
}
