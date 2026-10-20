// Jansson: json_loadb into its refcounted tree.
#include <jansson.h>

#include "common.hpp"

namespace {

struct JanssonFree {
  void operator()(json_t* j) const { json_decref(j); }
};

std::string_view sv(const json_t* v) {
  if (!json_is_string(v)) fail("expected string");
  return {json_string_value(v), json_string_length(v)};
}

uint64_t u64(const json_t* v) {
  if (!json_is_integer(v) || json_integer_value(v) < 0) fail("expected unsigned integer");
  return uint64_t(json_integer_value(v));
}

const json_t* member(const json_t* o, const char* k) {
  const json_t* v = json_object_get(o, k);
  if (!v) fail(std::string("missing ") + k);
  return v;
}

class Dom final : public Runner {
 public:
  explicit Dom(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    json_error_t err;
    json_t* root = json_loadb(lines_[i].data(), lines_[i].size(), JSON_ALLOW_NUL, &err);
    if (!root) fail(std::string("jansson: ") + err.text);
    std::unique_ptr<json_t, void (*)(json_t*)> guard(root, [](json_t* j) { json_decref(j); });
    d.str(sv(member(root, "id")));
    d.str(sv(member(root, "pubkey")));
    d.num(u64(member(root, "created_at")));
    d.num(u64(member(root, "kind")));
    size_t t, k;
    json_t *tag, *el;
    json_array_foreach(member(root, "tags"), t, tag) {
      json_array_foreach(tag, k, el) d.str(sv(el));
      d.tag_end();
    }
    d.tags_end();
    d.str(sv(member(root, "content")));
    d.str(sv(member(root, "sig")));
    d.event_end();
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      json_t* root = json_loadb(l.data(), l.size(), JSON_ALLOW_NUL, nullptr);
      sink += uint64_t(json_integer_value(json_object_get(root, "kind"))) +
              json_array_size(json_object_get(root, "tags"));
      json_decref(root);
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<json_t, JanssonFree>(lines_.size(), [&](size_t i) {
      json_error_t err;
      json_t* root = json_loadb(lines_[i].data(), lines_[i].size(), JSON_ALLOW_NUL, &err);
      if (!root) fail(std::string("jansson: ") + err.text);
      return root;
    });
  }

 private:
  const Lines& lines_;
};

}  // namespace

void register_jansson(Registry& r) {
  r.push_back({"jansson-dom", "Jansson", JB_REF_jansson, "MIT", "dom", "string",
               "json_loadb(frame.data(), frame.size(), JSON_ALLOW_NUL); refcounted json_t tree, hash-table objects",
               [](const Lines& l) -> std::unique_ptr<Runner> { return std::make_unique<Dom>(l); }});
}
