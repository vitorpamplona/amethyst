//! Every parser row of the harness: how a line becomes an event, and its metadata.

use crate::dom::HifiValue;
use crate::model::{D, Event, EventCow, EventRef, R, View, digest_fields, e};
use sha2::Sha256;
use std::hint::black_box;

/// One input line. Every line is stored with `SIMDJSON_PADDING` (64) zero bytes after it in the
/// same allocation (`bytes-padded`); parsers that need no padding just never read past `b`.
#[derive(Clone, Copy)]
pub struct Line {
    pub b: &'static [u8],
}

impl Line {
    /// The line as `&str`. Validated once at load, so this costs nothing; the
    /// `std-from_utf8` baseline row shows what validating it would cost.
    #[inline(always)]
    pub fn s(&self) -> &'static str {
        // SAFETY: every line passed `std::str::from_utf8` when the corpus was loaded.
        unsafe { std::str::from_utf8_unchecked(self.b) }
    }
}

pub struct Meta {
    pub id: &'static str,
    /// The crate whose version and license the row reports.
    pub library: &'static str,
    pub mode: &'static str,
    pub input: &'static str,
    pub notes: &'static str,
}

pub trait Parser {
    /// Parse one line and fold kind + tag count + content length.
    fn fold(&mut self, l: Line) -> R<u64>;
    /// Parse one line and feed the decoded event into the digest.
    fn digest(&mut self, l: Line, h: &mut Sha256) -> R<()>;
    /// One timed round over the workload; monomorphized per parser, so `fold` inlines.
    #[inline(never)]
    fn round(&mut self, lines: &[Line]) -> u64 {
        let mut sink = 0u64;
        for &l in lines {
            sink = sink.wrapping_add(black_box(self.fold(black_box(l)).unwrap_or(0)));
        }
        sink
    }
}

pub struct Entry {
    pub meta: Meta,
    pub make: fn() -> Box<dyn Parser>,
}

/// Declares a parser: its state, and the expression that turns `l: Line` into a `View`.
macro_rules! parser {
    ($name:ident { $($f:ident : $t:ty = $init:expr),* $(,)? }, |$s:ident, $l:ident| $body:expr) => {
        pub struct $name { $($f: $t),* }
        impl $name {
            fn new() -> Self { Self { $($f: $init),* } }
        }
        impl Parser for $name {
            #[inline(always)]
            #[allow(unused_variables)]
            fn fold(&mut self, $l: Line) -> R<u64> {
                let $s = &mut *self;
                let v = $body;
                Ok(View::fold(&v))
            }
            #[allow(unused_variables)]
            fn digest(&mut self, $l: Line, h: &mut Sha256) -> R<()> {
                let $s = &mut *self;
                let v = $body;
                View::digest(&v, h)
            }
        }
    };
}

/// Copies the line into the parser's scratch buffer (for parsers that destroy their input).
#[inline(always)]
fn copy_into(scratch: &mut Vec<u8>, l: Line) -> &mut [u8] {
    scratch.clear();
    scratch.extend_from_slice(l.b);
    scratch.as_mut_slice()
}

// ---------------------------------------------------------------- serde_json
parser!(SerdeJsonTyped {}, |s, l| serde_json::from_slice::<Event>(l.b)
    .map_err(e)?);
parser!(SerdeJsonTypedStr {}, |s, l| serde_json::from_str::<Event>(l.s())
    .map_err(e)?);
parser!(SerdeJsonBorrowed {}, |s, l| serde_json::from_slice::<EventCow>(l.b)
    .map_err(e)?);
parser!(SerdeJsonValue {}, |s, l| D(
    serde_json::from_slice::<serde_json::Value>(l.b).map_err(e)?
));
parser!(SerdeJsonBorrowValue {}, |s, l| D(serde_json::from_slice::<
    serde_json_borrow::Value,
>(l.b)
.map_err(e)?));

// ---------------------------------------------------------------- simd-json
parser!(SimdJsonTyped { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    simd_json::serde::from_slice_with_buffers::<Event>(input, &mut s.buf).map_err(e)?
});
parser!(SimdJsonBorrowedStruct { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    simd_json::serde::from_slice_with_buffers::<EventRef>(input, &mut s.buf).map_err(e)?
});
parser!(SimdJsonBorrowedValue { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    D(simd_json::to_borrowed_value_with_buffers(input, &mut s.buf).map_err(e)?)
});
parser!(SimdJsonOwnedValue { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    D(simd_json::to_owned_value_with_buffers(input, &mut s.buf).map_err(e)?)
});

/// simd-json's tape (the flat node array its other APIs are built on), reused across lines.
pub struct SimdJsonTape {
    scratch: Vec<u8>,
    buf: simd_json::Buffers,
    tape: Option<simd_json::Tape<'static>>,
}

impl SimdJsonTape {
    fn new() -> Self {
        Self {
            scratch: Vec::new(),
            buf: simd_json::Buffers::default(),
            tape: Some(simd_json::Tape::null()),
        }
    }

    #[inline(always)]
    fn with<T>(&mut self, l: Line, f: impl FnOnce(simd_json::tape::Value<'_, '_>) -> R<T>) -> R<T> {
        let input = copy_into(&mut self.scratch, l);
        let mut tape = self.tape.take().unwrap_or_else(simd_json::Tape::null).reset();
        let res = simd_json::fill_tape(input, &mut self.buf, &mut tape)
            .map_err(e)
            .and_then(|()| f(tape.as_value()));
        self.tape = Some(tape.reset());
        res
    }
}

impl Parser for SimdJsonTape {
    #[inline(always)]
    fn fold(&mut self, l: Line) -> R<u64> {
        use simd_json::prelude::*;
        self.with(l, |v| {
            let o = v.as_object().ok_or("not an object")?;
            let kind = o.get("kind").and_then(|k| k.as_u64()).unwrap_or(0);
            let tags = o.get("tags").and_then(|t| t.as_array()).map_or(0, |a| a.len());
            let content = o.get("content").and_then(|c| c.as_str().map(str::len)).unwrap_or(0);
            Ok(kind.wrapping_add(tags as u64).wrapping_add(content as u64))
        })
    }

    fn digest(&mut self, l: Line, h: &mut Sha256) -> R<()> {
        use simd_json::prelude::*;
        self.with(l, |v| {
            let o = v.as_object().ok_or("not an object")?;
            let s = |k: &str| {
                o.get(k)
                    .and_then(|x| x.as_str().map(str::to_owned))
                    .ok_or_else(|| format!("missing string {k}"))
            };
            let n = |k: &str| {
                o.get(k)
                    .and_then(|x| x.as_u64())
                    .ok_or_else(|| format!("missing integer {k}"))
            };
            let mut tags: Vec<Vec<String>> = Vec::new();
            for t in o.get("tags").and_then(|t| t.as_array()).ok_or("missing tags")?.iter() {
                let mut tag = Vec::new();
                for x in t.as_array().ok_or("tag is not an array")?.iter() {
                    tag.push(x.as_str().ok_or("tag element is not a string")?.to_owned());
                }
                tags.push(tag);
            }
            let tags = tags.iter().map(|t| t.iter().map(String::as_str));
            digest_fields(
                h,
                &s("id")?,
                &s("pubkey")?,
                n("created_at")?,
                n("kind")?,
                tags,
                &s("content")?,
                &s("sig")?,
            );
            Ok(())
        })
    }
}

// ---------------------------------------------------------------- simd-json-derive
parser!(SimdJsonDeriveTyped { scratch: Vec<u8> = Vec::new(), buf: sjd::Buffers = sjd::new_buffers() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    sjd::parse_owned(input, &mut s.buf)?
});
parser!(SimdJsonDeriveBorrowed { scratch: Vec<u8> = Vec::new(), buf: sjd::Buffers = sjd::new_buffers() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    sjd::parse_borrowed(input, &mut s.buf)?
});

// ---------------------------------------------------------------- sonic-rs
parser!(SonicTyped {}, |s, l| sonic_rs::from_slice::<Event>(l.b).map_err(e)?);
parser!(SonicBorrowed {}, |s, l| sonic_rs::from_slice::<EventCow>(l.b)
    .map_err(e)?);
parser!(SonicValue {}, |s, l| D(
    sonic_rs::from_slice::<sonic_rs::Value>(l.b).map_err(e)?
));
parser!(SonicLazy {}, |s, l| sonic_lazy(l.b)?);

/// sonic-rs lazy API: iterate the object's raw fields and materialize an owned `Event`.
#[inline(always)]
fn sonic_lazy(b: &[u8]) -> R<Event> {
    use sonic_rs::JsonValueTrait;
    let mut ev = Event::default();
    for item in sonic_rs::to_object_iter(b) {
        let (k, v) = item.map_err(e)?;
        let str_of = |v: &sonic_rs::LazyValue| v.as_str().map(str::to_owned).ok_or("expected string");
        match &*k {
            "id" => ev.id = str_of(&v)?,
            "pubkey" => ev.pubkey = str_of(&v)?,
            "content" => ev.content = str_of(&v)?,
            "sig" => ev.sig = str_of(&v)?,
            "created_at" => ev.created_at = v.as_u64().ok_or("created_at")?,
            "kind" => ev.kind = v.as_u64().and_then(|k| u32::try_from(k).ok()).ok_or("kind")?,
            "tags" => {
                for t in v.into_array_iter().ok_or("tags")? {
                    let mut tag = Vec::new();
                    for x in t.map_err(e)?.into_array_iter().ok_or("tag")? {
                        tag.push(str_of(&x.map_err(e)?)?);
                    }
                    ev.tags.push(tag);
                }
            }
            _ => {}
        }
    }
    Ok(ev)
}

// ---------------------------------------------------------------- jiter
parser!(JiterValue {}, |s, l| D(jiter::JsonValue::parse(l.b, false).map_err(e)?));
parser!(JiterValueScratch { scratch: jiter::JsonValueScratch<'static> = jiter::JsonValueScratch::new() }, |s, l| D(
    s.scratch.parse(l.b, false, jiter::PartialMode::Off).map_err(e)?
));
parser!(JiterTyped {}, |s, l| jiter_event(l.b).map_err(e)?);
parser!(JiterSerdeTyped {}, |s, l| jiter::serde::from_slice::<Event>(l.b)
    .map_err(e)?);
parser!(JiterSerdeBorrowed {}, |s, l| jiter::serde::from_slice::<EventCow>(l.b)
    .map_err(e)?);

#[derive(Clone, Copy)]
enum Field {
    Id,
    Pubkey,
    CreatedAt,
    Kind,
    Tags,
    Content,
    Sig,
    Other,
}

#[inline(always)]
fn field(k: &str) -> Field {
    match k {
        "id" => Field::Id,
        "pubkey" => Field::Pubkey,
        "created_at" => Field::CreatedAt,
        "kind" => Field::Kind,
        "tags" => Field::Tags,
        "content" => Field::Content,
        "sig" => Field::Sig,
        _ => Field::Other,
    }
}

/// jiter's pull API, hand-written into an owned `Event`.
#[inline(always)]
fn jiter_event(b: &[u8]) -> R<Event> {
    use jiter::{Jiter, NumberInt, Peek};
    let mut j = Jiter::new(b);
    let mut ev = Event::default();
    let mut key = j.next_object().map_err(e)?.map(field);
    while let Some(f) = key {
        match f {
            Field::Id => ev.id = j.next_str().map_err(e)?.to_owned(),
            Field::Pubkey => ev.pubkey = j.next_str().map_err(e)?.to_owned(),
            Field::Content => ev.content = j.next_str().map_err(e)?.to_owned(),
            Field::Sig => ev.sig = j.next_str().map_err(e)?.to_owned(),
            Field::CreatedAt | Field::Kind => {
                let n = match j.next_int().map_err(e)? {
                    NumberInt::Int(i) => u64::try_from(i).map_err(e)?,
                    _ => return Err("integer out of range".into()),
                };
                if let Field::Kind = f {
                    ev.kind = u32::try_from(n).map_err(e)?;
                } else {
                    ev.created_at = n;
                }
            }
            Field::Tags => {
                let mut outer = j.next_array().map_err(e)?;
                while let Some(p) = outer {
                    if p != Peek::Array {
                        return Err("tag is not an array".into());
                    }
                    let mut tag = Vec::new();
                    let mut inner = j.known_array().map_err(e)?;
                    while let Some(q) = inner {
                        if q != Peek::String {
                            return Err("tag element is not a string".into());
                        }
                        tag.push(j.known_str().map_err(e)?.to_owned());
                        inner = j.array_step().map_err(e)?;
                    }
                    ev.tags.push(tag);
                    outer = j.array_step().map_err(e)?;
                }
            }
            Field::Other => j.next_skip().map_err(e)?,
        }
        key = j.next_key().map_err(e)?.map(field);
    }
    j.finish().map_err(e)?;
    Ok(ev)
}

// ---------------------------------------------------------------- json (json-rust)
parser!(JsonDom {}, |s, l| D(json::parse(l.s()).map_err(e)?));

// ---------------------------------------------------------------- miniserde / nanoserde / tinyjson
parser!(MiniserdeTyped {}, |s, l| miniserde::json::from_str::<Event>(l.s())
    .map_err(e)?);
parser!(NanoserdeTyped {}, |s, l| {
    <Event as nanoserde::DeJson>::deserialize_json(l.s()).map_err(e)?
});
parser!(TinyjsonDom {}, |s, l| D(l
    .s()
    .parse::<tinyjson::JsonValue>()
    .map_err(e)?));

// ---------------------------------------------------------------- hifijson
parser!(HifijsonValue {}, |s, l| {
    use hifijson::token::Lex;
    let mut lexer = hifijson::SliceLexer::new(l.b);
    let v: HifiValue = lexer
        .exactly_one(Lex::ws_peek, hifijson::value::parse_unbounded)
        .map_err(e)?;
    D(v)
});
parser!(HifijsonSerdeTyped {}, |s, l| {
    let mut lexer = hifijson::SliceLexer::new(l.b);
    hifijson::serde::exactly_one::<Event, _>(&mut lexer).map_err(e)?
});

// ---------------------------------------------------------------- struson
parser!(StrusonTyped {}, |s, l| struson_event(l.b).map_err(e)?);

/// struson's streaming reader, hand-written into an owned `Event`.
#[inline(always)]
fn struson_event(b: &[u8]) -> Result<Event, Box<dyn std::error::Error>> {
    use struson::reader::{JsonReader, JsonStreamReader};
    let mut r = JsonStreamReader::new(b);
    let mut ev = Event::default();
    r.begin_object()?;
    while r.has_next()? {
        match field(r.next_name()?) {
            Field::Id => ev.id = r.next_string()?,
            Field::Pubkey => ev.pubkey = r.next_string()?,
            Field::Content => ev.content = r.next_string()?,
            Field::Sig => ev.sig = r.next_string()?,
            Field::CreatedAt => ev.created_at = r.next_number::<u64>()??,
            Field::Kind => ev.kind = r.next_number::<u32>()??,
            Field::Tags => {
                r.begin_array()?;
                while r.has_next()? {
                    r.begin_array()?;
                    let mut tag = Vec::new();
                    while r.has_next()? {
                        tag.push(r.next_string()?);
                    }
                    r.end_array()?;
                    ev.tags.push(tag);
                }
                r.end_array()?;
            }
            Field::Other => r.skip_value()?,
        }
    }
    r.end_object()?;
    r.consume_trailing_whitespace()?;
    Ok(ev)
}

// ---------------------------------------------------------------- simdjson-rust (C++ simdjson)
parser!(
    SimdjsonRustTyped {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        // SAFETY: every line is stored with SIMDJSON_PADDING zero bytes after it.
        let tape = unsafe { s.p.parse_bytes_with_padding(l.b) }.map_err(e)?;
        simdjson_rust::serde::from_tape::<Event>(tape).map_err(e)?
    }
);
parser!(
    SimdjsonRustBorrowed {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        // SAFETY: as above.
        let tape = unsafe { s.p.parse_bytes_with_padding(l.b) }.map_err(e)?;
        simdjson_rust::serde::from_tape::<EventRef>(tape).map_err(e)?
    }
);
parser!(
    SimdjsonRustDom {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        // SAFETY: as above.
        let mut tape = unsafe { s.p.parse_bytes_with_padding(l.b) }.map_err(e)?;
        D(tape.parse_value().map_err(e)?)
    }
);

// ---------------------------------------------------------------- baseline
pub struct Utf8Baseline;

impl Parser for Utf8Baseline {
    #[inline(always)]
    fn fold(&mut self, l: Line) -> R<u64> {
        Ok(std::str::from_utf8(l.b).map_err(e)?.len() as u64)
    }
    fn digest(&mut self, _: Line, _: &mut Sha256) -> R<()> {
        Err("baseline: not a parser".into())
    }
}

macro_rules! entry {
    ($ty:ident, $id:literal, $lib:literal, $mode:literal, $input:literal, $notes:expr) => {
        Entry {
            meta: Meta {
                id: $id,
                library: $lib,
                mode: $mode,
                input: $input,
                notes: $notes,
            },
            make: || Box::new($ty::new()),
        }
    };
}

impl Utf8Baseline {
    fn new() -> Self {
        Utf8Baseline
    }
}

const COPY: &str = "simd-json unescapes in place, so each line is first copied into a reused scratch buffer (timed); Buffers reused across lines";

pub fn all() -> Vec<Entry> {
    vec![
        entry!(
            SerdeJsonTyped,
            "serde_json-typed",
            "serde_json",
            "typed",
            "bytes",
            "serde_json::from_slice into owned Event"
        ),
        entry!(
            SerdeJsonTypedStr,
            "serde_json-typed-str",
            "serde_json",
            "typed",
            "string",
            "serde_json::from_str (&str input skips serde_json's UTF-8 check of string contents)"
        ),
        entry!(
            SerdeJsonBorrowed,
            "serde_json-borrowed",
            "serde_json",
            "borrowed",
            "bytes",
            "Cow<'a, str> fields with #[serde(borrow)]: borrowed when the string has no escape, owned otherwise"
        ),
        entry!(
            SerdeJsonValue,
            "serde_json-value",
            "serde_json",
            "dom",
            "bytes",
            "serde_json::Value (BTreeMap objects)"
        ),
        entry!(
            SerdeJsonBorrowValue,
            "serde_json_borrow-value",
            "serde_json_borrow",
            "dom",
            "bytes",
            "serde_json_borrow::Value via serde_json::from_slice: Cow strings, objects as Vec of pairs"
        ),
        entry!(
            SimdJsonTyped,
            "simd_json-typed",
            "simd-json",
            "typed",
            "bytes-copy",
            COPY
        ),
        entry!(
            SimdJsonBorrowedStruct,
            "simd_json-borrowed-struct",
            "simd-json",
            "borrowed",
            "bytes-copy",
            "serde into a struct of &str pointing into the in-place-unescaped copy; same copy as simd_json-typed"
        ),
        entry!(
            SimdJsonBorrowedValue,
            "simd_json-borrowed",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::BorrowedValue (Cow strings into the scratch copy)"
        ),
        entry!(
            SimdJsonOwnedValue,
            "simd_json-owned-value",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::OwnedValue"
        ),
        entry!(
            SimdJsonTape,
            "simd_json-tape",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::fill_tape into a reused Tape, read through tape::Value"
        ),
        entry!(
            SimdJsonDeriveTyped,
            "simd_json_derive-typed",
            "simd-json-derive",
            "typed",
            "bytes-copy",
            "simd-json-derive (no serde) into owned Event; runs on simd-json 0.17.3, which simd-json-derive 0.18 pins"
        ),
        entry!(
            SimdJsonDeriveBorrowed,
            "simd_json_derive-borrowed",
            "simd-json-derive",
            "borrowed",
            "bytes-copy",
            "simd-json-derive into a struct of &str; runs on simd-json 0.17.3"
        ),
        entry!(
            SonicTyped,
            "sonic_rs-typed",
            "sonic-rs",
            "typed",
            "bytes",
            "sonic_rs::from_slice into owned Event"
        ),
        entry!(
            SonicBorrowed,
            "sonic_rs-borrowed",
            "sonic-rs",
            "borrowed",
            "bytes",
            "Cow<'a, str> fields with #[serde(borrow)]"
        ),
        entry!(
            SonicValue,
            "sonic_rs-value",
            "sonic-rs",
            "dom",
            "bytes",
            "sonic_rs::Value"
        ),
        entry!(
            SonicLazy,
            "sonic_rs-lazy",
            "sonic-rs",
            "typed",
            "bytes",
            "lazy API: to_object_iter + LazyValue::as_str/into_array_iter, materialized into owned Event"
        ),
        entry!(
            JiterValue,
            "jiter-value",
            "jiter",
            "dom",
            "bytes",
            "jiter::JsonValue::parse (Cow strings, Arc'd arrays/objects)"
        ),
        entry!(
            JiterValueScratch,
            "jiter-value-scratch",
            "jiter",
            "dom",
            "bytes",
            "JsonValueScratch reused across lines (parse stacks and string tape kept)"
        ),
        entry!(
            JiterTyped,
            "jiter-typed",
            "jiter",
            "typed",
            "bytes",
            "hand-written loop over jiter's pull API (Jiter::next_object/next_str/...)"
        ),
        entry!(
            JiterSerdeTyped,
            "jiter-serde-typed",
            "jiter",
            "typed",
            "bytes",
            "jiter::serde::from_slice into owned Event"
        ),
        entry!(
            JiterSerdeBorrowed,
            "jiter-serde-borrowed",
            "jiter",
            "borrowed",
            "bytes",
            "jiter::serde::from_slice into Cow fields"
        ),
        entry!(
            JsonDom,
            "json-dom",
            "json",
            "dom",
            "string",
            "json::parse (json-rust JsonValue)"
        ),
        entry!(
            MiniserdeTyped,
            "miniserde-typed",
            "miniserde",
            "typed",
            "string",
            "miniserde::json::from_str into owned Event"
        ),
        entry!(
            NanoserdeTyped,
            "nanoserde-typed",
            "nanoserde",
            "typed",
            "string",
            "nanoserde DeJson::deserialize_json into owned Event"
        ),
        entry!(
            TinyjsonDom,
            "tinyjson-dom",
            "tinyjson",
            "dom",
            "string",
            "tinyjson::JsonValue (numbers are f64; exact for NIP-01 integers < 2^53)"
        ),
        entry!(
            HifijsonValue,
            "hifijson-value",
            "hifijson",
            "dom",
            "bytes",
            "hifijson value::parse_unbounded on a SliceLexer (numbers kept as text, Cow strings)"
        ),
        entry!(
            HifijsonSerdeTyped,
            "hifijson-serde-typed",
            "hifijson",
            "typed",
            "bytes",
            "hifijson::serde::exactly_one into owned Event"
        ),
        entry!(
            StrusonTyped,
            "struson-typed",
            "struson",
            "typed",
            "bytes",
            "hand-written loop over struson's JsonStreamReader (a Read over the slice)"
        ),
        entry!(
            SimdjsonRustTyped,
            "simdjson_rust-typed",
            "simdjson-rust",
            "typed",
            "bytes-padded",
            "C++ simdjson DOM (reused parser) + the crate's serde tape deserializer into owned Event; C++ allocations are not counted"
        ),
        entry!(
            SimdjsonRustBorrowed,
            "simdjson_rust-borrowed",
            "simdjson-rust",
            "borrowed",
            "bytes-padded",
            "as simdjson_rust-typed, into a struct of &str pointing into simdjson's string buffer"
        ),
        entry!(
            SimdjsonRustDom,
            "simdjson_rust-dom",
            "simdjson-rust",
            "dom",
            "bytes-padded",
            "C++ simdjson DOM materialized as simdjson_rust::tape::Value (Vec-backed, &str strings)"
        ),
        entry!(
            Utf8Baseline,
            "std-from_utf8",
            "std",
            "baseline",
            "bytes",
            "std::str::from_utf8: the cost of turning the byte input into the &str the string-input rows read"
        ),
    ]
}
