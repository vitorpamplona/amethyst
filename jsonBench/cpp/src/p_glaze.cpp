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
  r.push_back({"glaze-typed", kLib, ver, kLicense, "typed", "bytes",
               "glz::read_json into the Event aggregate (compile-time reflection, default opts)",
               make<TypedRunner<Event>>});
  r.push_back({"glaze-borrowed", kLib, ver, kLicense, "borrowed", "bytes",
               "partial borrow: glaze string_views are raw (escapes not decoded), so only id/pubkey/sig "
               "(hex) are views into the input; content and tags are owned std::string",
               make<TypedRunner<GlazeBorrowedEvent>>});
  r.push_back({"glaze-dom", kLib, ver, kLicense, "dom", "bytes",
               "glz::read_json into glz::generic_u64 (variant tree, map objects)", make<DomRunner>});
}
