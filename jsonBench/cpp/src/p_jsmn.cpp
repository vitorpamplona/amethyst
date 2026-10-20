// jsmn: a tokenizer, not a parser. It records token boundaries into a caller-provided array
// (no allocation) and never decodes strings or numbers. The timed loop tokenizes, finds the
// top-level "kind" and "tags" tokens and parses the kind digits; for verification the harness
// decodes the string tokens itself (json_unescape), which jsmn does not do.
#define JSMN_STATIC
#define JSMN_STRICT
#include "jsmn.h"

#include "common.hpp"

namespace {

int skip(const jsmntok_t* t, int i) {
  int j = i + 1;
  for (int k = 0; k < t[i].size; k++) j = skip(t, j);
  return j;
}

std::string_view slice(const std::string& s, const jsmntok_t& t) {
  return std::string_view(s).substr(size_t(t.start), size_t(t.end - t.start));
}

uint64_t digits(std::string_view s) {
  uint64_t v = 0;
  if (s.empty()) fail("empty number");
  for (char c : s) {
    if (c < '0' || c > '9') fail("not an unsigned integer");
    v = v * 10 + uint64_t(c - '0');
  }
  return v;
}

class Tok final : public Runner {
 public:
  explicit Tok(const Lines& l) : lines_(l) {
    size_t max = 0;
    for (const auto& s : l) max = std::max(max, s.size());
    toks_.resize(max / 2 + 16);  // a token needs at least two input bytes
  }
  int tokenize(const std::string& l) {
    jsmn_parser p;
    jsmn_init(&p);
    return jsmn_parse(&p, l.data(), l.size(), toks_.data(), unsigned(toks_.size()));
  }
  void digest(size_t i, DigestWriter& d) override {
    const std::string& l = lines_[i];
    int n = tokenize(l);
    if (n < 1 || toks_[0].type != JSMN_OBJECT) fail("jsmn error " + std::to_string(n));
    const jsmntok_t* t = toks_.data();
    int id = -1, pubkey = -1, created = -1, kind = -1, tags = -1, content = -1, sig = -1;
    for (int k = 0, j = 1; k < t[0].size; k++) {
      switch (field_of(slice(l, t[j]))) {
        case Field::Id: id = j + 1; break;
        case Field::Pubkey: pubkey = j + 1; break;
        case Field::CreatedAt: created = j + 1; break;
        case Field::Kind: kind = j + 1; break;
        case Field::Tags: tags = j + 1; break;
        case Field::Content: content = j + 1; break;
        case Field::Sig: sig = j + 1; break;
        case Field::Unknown: fail("unexpected key");
      }
      j = skip(t, j);
    }
    auto str = [&](int j) {
      if (j < 0 || t[j].type != JSMN_STRING) fail("expected string");
      d.str(json_unescape(slice(l, t[j])));
    };
    auto num = [&](int j) {
      if (j < 0 || t[j].type != JSMN_PRIMITIVE) fail("expected number");
      d.num(digits(slice(l, t[j])));
    };
    str(id);
    str(pubkey);
    num(created);
    num(kind);
    if (tags < 0 || t[tags].type != JSMN_ARRAY) fail("tags not an array");
    for (int k = 0, j = tags + 1; k < t[tags].size; k++) {
      if (t[j].type != JSMN_ARRAY) fail("tag not an array");
      for (int m = 0; m < t[j].size; m++) str(j + 1 + m);
      d.tag_end();
      j = skip(t, j);
    }
    d.tags_end();
    str(content);
    str(sig);
    d.event_end();
  }
  uint64_t round() override {
    uint64_t sink = 0;
    for (const auto& l : lines_) {
      tokenize(l);
      const jsmntok_t* t = toks_.data();
      for (int k = 0, j = 1; k < t[0].size; k++) {
        std::string_view key = slice(l, t[j]);
        if (key == "kind") {
          uint64_t v = 0;
          for (int c = t[j + 1].start; c < t[j + 1].end; c++) v = v * 10 + uint64_t(l[size_t(c)] - '0');
          sink += v;
        } else if (key == "tags") {
          sink += uint64_t(t[j + 1].size);
        }
        j = skip(t, j);
      }
    }
    return sink;
  }

 private:
  const Lines& lines_;
  std::vector<jsmntok_t> toks_;
};

}  // namespace

void register_jsmn(Registry& r) {
  r.push_back({"jsmn-tokens", "jsmn", JB_REF_jsmn, "MIT", "dom", "string",
               "jsmn_parse(frame.data(), frame.size()); tokenizer only (JSMN_STRICT, reused token array): no string "
               "or number decoding in the timed loop; the digest decodes the token slices with the harness's own "
               "unescaper. Retained heap null: tokens are offsets in a reused array",
               [](const Lines& l) -> std::unique_ptr<Runner> { return std::make_unique<Tok>(l); }});
}
