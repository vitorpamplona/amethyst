// cJSON: cJSON_ParseWithLength into its linked-list tree.
#include "cJSON.h"

#include "common.hpp"

namespace {

struct CjsonFree {
  void operator()(cJSON* j) const { cJSON_Delete(j); }
};

std::string_view sv(const cJSON* v) {
  if (!cJSON_IsString(v)) fail("expected string");
  return v->valuestring;  // cJSON strings are NUL-terminated (an embedded \u0000 would truncate)
}

uint64_t u64(const cJSON* v) {
  if (!cJSON_IsNumber(v) || v->valuedouble < 0) fail("expected unsigned number");
  return uint64_t(v->valuedouble);  // numbers are doubles; exact for created_at < 2^53
}

const cJSON* member(const cJSON* o, const char* k) {
  const cJSON* v = cJSON_GetObjectItemCaseSensitive(o, k);
  if (!v) fail(std::string("missing ") + k);
  return v;
}

class Dom final : public Runner {
 public:
  explicit Dom(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    cJSON* root = cJSON_ParseWithLength(lines_[i].data(), lines_[i].size());
    if (!root) fail("cJSON parse error");
    std::unique_ptr<cJSON, void (*)(cJSON*)> guard(root, cJSON_Delete);
    d.str(sv(member(root, "id")));
    d.str(sv(member(root, "pubkey")));
    d.num(u64(member(root, "created_at")));
    d.num(u64(member(root, "kind")));
    const cJSON* tag;
    cJSON_ArrayForEach(tag, member(root, "tags")) {
      const cJSON* el;
      cJSON_ArrayForEach(el, tag) d.str(sv(el));
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
      cJSON* root = cJSON_ParseWithLength(l.data(), l.size());
      sink += uint64_t(cJSON_GetObjectItemCaseSensitive(root, "kind")->valuedouble) +
              uint64_t(cJSON_GetArraySize(cJSON_GetObjectItemCaseSensitive(root, "tags")));
      cJSON_Delete(root);
    }
    return sink;
  }
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<cJSON, CjsonFree>(lines_.size(), [&](size_t i) {
      cJSON* root = cJSON_ParseWithLength(lines_[i].data(), lines_[i].size());
      if (!root) fail("cJSON parse error");
      return root;
    });
  }

 private:
  const Lines& lines_;
};

}  // namespace

void register_cjson(Registry& r) {
  r.push_back({"cjson-dom", "cJSON", JB_REF_cjson, "MIT", "dom", "string",
               "cJSON_ParseWithLength(frame.data(), frame.size()); one malloc per node and per string; numbers as double. Strings are NUL-terminated with no length, so a \\u0000 escape truncates them: digest fails on the corpus events that contain one",
               [](const Lines& l) -> std::unique_ptr<Runner> { return std::make_unique<Dom>(l); }});
}
