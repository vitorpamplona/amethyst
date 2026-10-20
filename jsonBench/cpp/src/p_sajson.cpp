// sajson: single-pass parser into a compact AST (object keys sorted for binary search).
#include "sajson.h"

#include "common.hpp"

namespace {

std::string_view sv(const sajson::value& v) {
  if (v.get_type() != sajson::TYPE_STRING) fail("expected string");
  return {v.as_cstring(), v.get_string_length()};
}

uint64_t u64(const sajson::value& v) {
  auto t = v.get_type();
  if (t == sajson::TYPE_INTEGER) return uint64_t(v.get_integer_value());
  if (t == sajson::TYPE_DOUBLE) return uint64_t(v.get_double_value());  // > 2^31 lands here
  fail("expected number");
}

sajson::value member(const sajson::value& o, const char* k) {
  sajson::value v = o.get_value_of_key(sajson::string(k, std::char_traits<char>::length(k)));
  if (v.get_type() == sajson::TYPE_NULL) fail(std::string("missing ") + k);
  return v;
}

sajson::document parse(const std::string& l) {
  return sajson::parse(sajson::dynamic_allocation(), sajson::string(l.data(), l.size()));
}

class Dom final : public Runner {
 public:
  explicit Dom(const Lines& l) : lines_(l) {}
  void digest(size_t i, DigestWriter& d) override {
    sajson::document doc = parse(lines_[i]);
    if (!doc.is_valid()) fail(std::string("sajson: ") + doc.get_error_message_as_cstring());
    sajson::value root = doc.get_root();
    d.str(sv(member(root, "id")));
    d.str(sv(member(root, "pubkey")));
    d.num(u64(member(root, "created_at")));
    d.num(u64(member(root, "kind")));
    sajson::value tags = member(root, "tags");
    for (size_t t = 0; t < tags.get_length(); t++) {
      sajson::value tag = tags.get_array_element(t);
      for (size_t k = 0; k < tag.get_length(); k++) d.str(sv(tag.get_array_element(k)));
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
      sajson::document doc = parse(l);
      sajson::value root = doc.get_root();
      sink += uint64_t(root.get_value_of_key(sajson::literal("kind")).get_integer_value()) +
              root.get_value_of_key(sajson::literal("tags")).get_length();
    }
    return sink;
  }
  // A document owns its input copy and AST (dynamic_allocation), so each one is independent.
  std::unique_ptr<Kept> keep_all() override {
    return keep_each<sajson::document>(lines_.size(), [&](size_t i) {
      auto doc = std::make_unique<sajson::document>(parse(lines_[i]));
      if (!doc->is_valid()) fail(std::string("sajson: ") + doc->get_error_message_as_cstring());
      return doc.release();
    });
  }

 private:
  const Lines& lines_;
};

}  // namespace

void register_sajson(Registry& r) {
  r.push_back({"sajson-dom", "sajson", JB_REF_sajson, "MIT", "dom", "string",
               "sajson::parse(dynamic_allocation, sajson::string(frame.data(), frame.size())): the library copies the "
               "input into the document's own mutable buffer and decodes in place; "
               "integers are int32 (created_at becomes a double)",
               [](const Lines& l) -> std::unique_ptr<Runner> { return std::make_unique<Dom>(l); }});
}
