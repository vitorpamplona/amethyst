// glaze: compile-time reflected read_json into Event, a borrowed variant, and the generic DOM.
#include "glaze/glaze.hpp"

#include "common.hpp"

namespace {

constexpr const char* kLib = "glaze";
constexpr const char* kLicense = "MIT";

// glaze reads std::string_view fields as raw views of the input *without unescaping*, so a
// view is only correct for strings that can never hold an escape. id, pubkey and sig are
// lowercase hex by NIP-01; content and tag values routinely carry escapes and stay owned.
struct GlazeBorrowedEvent {
  std::string_view id;
  std::string_view pubkey;
  uint64_t created_at = 0;
  uint32_t kind = 0;
  std::vector<std::vector<std::string>> tags;
  std::string content;
  std::string_view sig;
};

// A partially borrowed event kept with the frame its views point into: the app would move the
// websocket's std::string in here; the harness copies it (exact size) to model that.
struct FrameAndView {
  std::string frame;
  GlazeBorrowedEvent e;
};

template <class E>
void read_or_throw(E& e, const std::string& l) {
  auto ec = glz::read_json(e, l);
  if (ec) fail("glaze: " + glz::format_error(ec, l));
}

template <class E>
class TypedRunner final : public Runner {
 public:
  explicit TypedRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    E e;
    read_or_throw(e, lines_[i]);
    digest_event(d, e);
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      E e;
      (void)glz::read_json(e, l);
      sink += e.kind + e.tags.size() + e.content.size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    if constexpr (std::is_same_v<E, Event>) {
      return keep_each<Event>(lines_.size(), [&](size_t i) {
        auto e = std::make_unique<Event>();
        read_or_throw(*e, lines_[i]);
        return e.release();
      });
    } else {
      return keep_each<FrameAndView>(lines_.size(), [&](size_t i) {
        auto k = std::make_unique<FrameAndView>();
        k->frame = std::string(lines_[i].data(), lines_[i].size());
        read_or_throw(k->e, k->frame);
        return k.release();
      });
    }
  }

 private:
  const Lines& lines_;
};

using Generic = glz::generic_u64;

const Generic& member(const Generic& j, const char* k) {
  const auto& o = j.get_object();
  auto it = o.find(k);
  if (it == o.end()) fail(std::string("missing ") + k);
  return it->second;
}

uint64_t u64(const Generic& v) {
  if (auto* p = v.get_if<uint64_t>()) return *p;
  fail("expected unsigned integer");
}

const std::string& str(const Generic& v) {
  if (auto* p = v.get_if<std::string>()) return *p;
  fail("expected string");
}

class DomRunner final : public Runner {
 public:
  explicit DomRunner(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    Generic j;
    read_or_throw(j, lines_[i]);
    d.str(str(member(j, "id")));
    d.str(str(member(j, "pubkey")));
    d.num(u64(member(j, "created_at")));
    d.num(u64(member(j, "kind")));
    for (const auto& tag : member(j, "tags").get_array()) {
      for (const auto& el : tag.get_array()) d.str(str(el));
      d.tag_end();
    }
    d.tags_end();
    d.str(str(member(j, "content")));
    d.str(str(member(j, "sig")));
    d.event_end();
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      Generic j;
      (void)glz::read_json(j, l);
      sink += u64(member(j, "kind")) + member(j, "tags").get_array().size();
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<Generic>(lines_.size(), [&](size_t i) {
      auto j = std::make_unique<Generic>();
      read_or_throw(*j, lines_[i]);
      return j.release();
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

void register_glaze(Registry& r) {
  const std::string ver = JB_REF_glaze;
  const std::string in = "glz::read_json(value, frame) takes the std::string directly (default opts rely on its "
                         "free NUL terminator); ";
  r.push_back({"glaze-typed", kLib, ver, kLicense, "typed", "string",
               in + "into the Event aggregate (compile-time reflection)", make<TypedRunner<Event>>});
  r.push_back({"glaze-borrowed", kLib, ver, kLicense, "borrowed", "string",
               in + "partial borrow: glaze string_views are raw (escapes not decoded), so only id/pubkey/sig "
                    "(hex) are views into the frame; content and tags are owned std::string. Retained keeps each "
                    "event with an exact-size copy of its frame (standing in for the moved websocket string)",
               make<TypedRunner<GlazeBorrowedEvent>>});
  r.push_back({"glaze-dom", kLib, ver, kLicense, "dom", "string",
               in + "into glz::generic_u64 (variant tree, map objects)", make<DomRunner>});
}
