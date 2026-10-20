// json-c: json_tokener_parse_ex with one reused tokener into json_object trees.
#include <json.h>

#include "common.hpp"

namespace {

struct JsoncFree {
  void operator()(json_object* j) const { json_object_put(j); }
};

std::string_view sv(json_object* v) {
  if (!json_object_is_type(v, json_type_string)) fail("expected string");
  return {json_object_get_string(v), size_t(json_object_get_string_len(v))};
}

uint64_t u64(json_object* v) {
  if (!json_object_is_type(v, json_type_int)) fail("expected integer");
  int64_t x = json_object_get_int64(v);
  if (x < 0) fail("negative");
  return uint64_t(x);
}

json_object* member(json_object* o, const char* k) {
  json_object* v = nullptr;
  if (!json_object_object_get_ex(o, k, &v)) fail(std::string("missing ") + k);
  return v;
}

class Dom final : public Runner {
 public:
  explicit Dom(const Lines& l) : lines_(l), tok_(json_tokener_new()) {}
  ~Dom() override { json_tokener_free(tok_); }
  json_object* parse(const std::string& l) {
    json_tokener_reset(tok_);
    return json_tokener_parse_ex(tok_, l.data(), int(l.size()));
  }
  void digest(size_t i, DigestWriter& d) override {
    json_object* root = parse(lines_[i]);
    if (!root) fail(std::string("json-c: ") + json_tokener_error_desc(json_tokener_get_error(tok_)));
    std::unique_ptr<json_object, int (*)(json_object*)> guard(root, json_object_put);
    d.str(sv(member(root, "id")));
    d.str(sv(member(root, "pubkey")));
    d.num(u64(member(root, "created_at")));
    d.num(u64(member(root, "kind")));
    json_object* tags = member(root, "tags");
    for (size_t t = 0; t < json_object_array_length(tags); t++) {
      json_object* tag = json_object_array_get_idx(tags, t);
      for (size_t k = 0; k < json_object_array_length(tag); k++) d.str(sv(json_object_array_get_idx(tag, k)));
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
      json_object* root = parse(l);
      json_object *kind = nullptr, *tags = nullptr;
      json_object_object_get_ex(root, "kind", &kind);
      json_object_object_get_ex(root, "tags", &tags);
      sink += uint64_t(json_object_get_int64(kind)) + json_object_array_length(tags);
      json_object_put(root);
    }
    return sink;
  }
  // The tokener is reused, but each parse returns an independent object tree.
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<json_object, JsoncFree>(lines_.size(), [&](size_t i) {
      json_object* root = parse(lines_[i]);
      if (!root) fail(std::string("json-c: ") + json_tokener_error_desc(json_tokener_get_error(tok_)));
      return root;
    });
  }

 private:
  const Lines& lines_;
  json_tokener* tok_;
};

}  // namespace

void register_jsonc(Registry& r) {
  r.push_back({"jsonc-dom", "json-c", JB_REF_jsonc, "MIT", "dom", "string",
               "json_tokener_parse_ex(tok, frame.data(), frame.size()) with one reused tokener; refcounted json_object "
               "tree, hash-table objects (each tree independent of the tokener, so retained keeps the trees)",
               [](const Lines& l) -> std::unique_ptr<Runner> { return std::make_unique<Dom>(l); }});
}
