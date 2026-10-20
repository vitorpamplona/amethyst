//! Every parser row of the harness: how a line becomes an event, and its metadata.

use crate::dom::HifiValue;
use crate::model::{D, Event, EventCow, EventRef, R, View, digest_fields, e};
use sha2::Sha256;
use std::hint::black_box;

/// One input line: what a WebSocket text frame is in Rust, a `&str` (tungstenite
/// `Message::Text(Utf8Bytes)`), already UTF-8-validated by the socket. The harness builds it
/// (as a leaked `String`, so results can borrow it for `'static`) before timing; every parser
/// starts from it, and any copy it needs on top is part of the timed parse.
#[derive(Clone, Copy)]
pub struct Line {
    pub s: &'static str,
}

impl Line {
    /// The frame's bytes, free (`str::as_bytes`), for parsers that take a byte slice.
    #[inline(always)]
    pub fn b(&self) -> &'static [u8] {
        self.s.as_bytes()
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
    /// Untimed: parse every line once more, keep every result alive, and return the live heap
    /// bytes they hold per event. `None` when the results borrow a reused parser buffer or tape,
    /// so they cannot all be kept at once.
    fn retained(&mut self, lines: &[Line]) -> Option<f64> {
        let _ = lines;
        None
    }
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

/// Keeps the result of parsing every line and returns the live heap bytes per event: the
/// allocator's (allocated - freed) before vs after, with the `Vec` of results allocated first
/// so its own buffer is not counted. Temporaries a parse frees again cancel out. The result's
/// own inline size (`size_of::<T>()`, e.g. the `Event` struct's String headers) is added,
/// because the JVM and C++ harnesses count the event object itself too.
fn retain_with<T>(lines: &[Line], mut parse: impl FnMut(Line) -> R<T>) -> Option<f64> {
    let mut kept: Vec<T> = Vec::with_capacity(lines.len());
    let before = crate::alloc::live();
    for &l in lines {
        kept.push(parse(l).ok()?);
    }
    let after = crate::alloc::live();
    black_box(&kept);
    drop(kept);
    let n = lines.len().max(1);
    let inline = (std::mem::size_of::<T>() * lines.len()) as u64;
    Some((after.saturating_sub(before) + inline) as f64 / n as f64)
}

/// Declares a parser: its state, and the expression that turns `l: Line` into a `View`.
/// A leading `keep` also measures retained heap: only for results that own their data or
/// borrow just the input line (which outlives them), never a reused parser buffer.
macro_rules! parser {
    (keep $name:ident { $($f:ident : $t:ty = $init:expr),* $(,)? }, |$s:ident, $l:ident| $body:expr) => {
        parser!(@impl $name { $($f : $t = $init),* }, |$s, $l| $body,
            #[allow(unused_variables)]
            fn retained(&mut self, lines: &[Line]) -> Option<f64> {
                retain_with(lines, |$l: Line| -> R<_> {
                    let $s = &mut *self;
                    Ok($body)
                })
            }
        );
    };
    ($name:ident { $($f:ident : $t:ty = $init:expr),* $(,)? }, |$s:ident, $l:ident| $body:expr) => {
        parser!(@impl $name { $($f : $t = $init),* }, |$s, $l| $body,);
    };
    (@impl $name:ident { $($f:ident : $t:ty = $init:expr),* }, |$s:ident, $l:ident| $body:expr, $($extra:tt)*) => {
        pub struct $name { $($f: $t),* }
        impl $name {
            fn new() -> Self { Self { $($f: $init),* } }
        }
        impl Parser for $name {
            $($extra)*
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

/// Copies the frame into the parser's reused scratch buffer (simd-json unescapes in place, so
/// it needs a mutable copy of the `&str`); timed, as part of the parse.
#[inline(always)]
fn copy_into(scratch: &mut Vec<u8>, l: Line) -> &mut [u8] {
    scratch.clear();
    scratch.extend_from_slice(l.b());
    scratch.as_mut_slice()
}

// ---------------------------------------------------------------- serde_json
parser!(keep SerdeJsonTyped {}, |s, l| serde_json::from_str::<Event>(l.s).map_err(e)?);
parser!(keep SerdeJsonBorrowed {}, |s, l| serde_json::from_str::<EventCow>(l.s).map_err(e)?);
parser!(keep SerdeJsonValue {}, |s, l| D(serde_json::from_str::<serde_json::Value>(l.s).map_err(e)?));
parser!(keep SerdeJsonBorrowValue {}, |s, l| D(
    serde_json::from_str::<serde_json_borrow::Value>(l.s).map_err(e)?
));

// ---------------------------------------------------------------- simd-json
parser!(keep SimdJsonTyped { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
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
parser!(keep SimdJsonOwnedValue { scratch: Vec<u8> = Vec::new(), buf: simd_json::Buffers = simd_json::Buffers::default() }, |s, l| {
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
parser!(keep SimdJsonDeriveTyped { scratch: Vec<u8> = Vec::new(), buf: sjd::Buffers = sjd::new_buffers() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    sjd::parse_owned(input, &mut s.buf)?
});
parser!(SimdJsonDeriveBorrowed { scratch: Vec<u8> = Vec::new(), buf: sjd::Buffers = sjd::new_buffers() }, |s, l| {
    let input = copy_into(&mut s.scratch, l);
    sjd::parse_borrowed(input, &mut s.buf)?
});

// ---------------------------------------------------------------- sonic-rs
parser!(keep SonicTyped {}, |s, l| sonic_rs::from_str::<Event>(l.s).map_err(e)?);
parser!(keep SonicBorrowed {}, |s, l| sonic_rs::from_str::<EventCow>(l.s).map_err(e)?);
parser!(keep SonicValue {}, |s, l| D(sonic_rs::from_str::<sonic_rs::Value>(l.s).map_err(e)?));
parser!(keep SonicLazy {}, |s, l| sonic_lazy(l.s)?);

/// sonic-rs lazy API: iterate the object's raw fields and materialize an owned `Event`.
#[inline(always)]
fn sonic_lazy(s: &str) -> R<Event> {
    use sonic_rs::JsonValueTrait;
    let mut ev = Event::default();
    for item in sonic_rs::to_object_iter(s) {
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
parser!(keep JiterValue {}, |s, l| D(jiter::JsonValue::parse(l.b(), false).map_err(e)?));
parser!(keep JiterValueScratch { scratch: jiter::JsonValueScratch<'static> = jiter::JsonValueScratch::new() }, |s, l| D(
    s.scratch.parse(l.b(), false, jiter::PartialMode::Off).map_err(e)?
));
parser!(keep JiterTyped {}, |s, l| jiter_event(l.b()).map_err(e)?);
parser!(keep JiterSerdeTyped {}, |s, l| jiter::serde::from_str::<Event>(l.s).map_err(e)?);
parser!(keep JiterSerdeBorrowed {}, |s, l| jiter::serde::from_str::<EventCow>(l.s).map_err(e)?);

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
parser!(keep JsonDom {}, |s, l| D(json::parse(l.s).map_err(e)?));

// ---------------------------------------------------------------- miniserde / nanoserde / tinyjson
parser!(keep MiniserdeTyped {}, |s, l| miniserde::json::from_str::<Event>(l.s)
    .map_err(e)?);
parser!(keep NanoserdeTyped {}, |s, l| {
    <Event as nanoserde::DeJson>::deserialize_json(l.s).map_err(e)?
});
parser!(keep TinyjsonDom {}, |s, l| D(l.s.parse::<tinyjson::JsonValue>().map_err(e)?));

// ---------------------------------------------------------------- hifijson
parser!(keep HifijsonValue {}, |s, l| {
    use hifijson::token::Lex;
    let mut lexer = hifijson::SliceLexer::new(l.b());
    let v: HifiValue = lexer
        .exactly_one(Lex::ws_peek, hifijson::value::parse_unbounded)
        .map_err(e)?;
    D(v)
});
parser!(keep HifijsonSerdeTyped {}, |s, l| {
    let mut lexer = hifijson::SliceLexer::new(l.b());
    hifijson::serde::exactly_one::<Event, _>(&mut lexer).map_err(e)?
});

// ---------------------------------------------------------------- struson
parser!(keep StrusonTyped {}, |s, l| struson_event(l.b()).map_err(e)?);

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
    keep SimdjsonRustTyped {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        // parse_str copies the frame into the parser's reused padded buffer (timed).
        let tape = s.p.parse_str(l.s).map_err(e)?;
        simdjson_rust::serde::from_tape::<Event>(tape).map_err(e)?
    }
);
parser!(
    SimdjsonRustBorrowed {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        let tape = s.p.parse_str(l.s).map_err(e)?;
        simdjson_rust::serde::from_tape::<EventRef>(tape).map_err(e)?
    }
);
parser!(
    SimdjsonRustDom {
        p: simdjson_rust::dom::Parser = simdjson_rust::dom::Parser::default()
    },
    |s, l| {
        let mut tape = s.p.parse_str(l.s).map_err(e)?;
        D(tape.parse_value().map_err(e)?)
    }
);

// ---------------------------------------------------------------- baseline
pub struct Utf8Baseline;

impl Parser for Utf8Baseline {
    #[inline(always)]
    fn fold(&mut self, l: Line) -> R<u64> {
        Ok(std::str::from_utf8(l.b()).map_err(e)?.len() as u64)
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

const COPY_TYPED: &str = "simd_json::serde::from_slice_with_buffers into owned Event. simd-json unescapes in place, so the &str frame is first copied into a reused scratch Vec<u8> (timed); Buffers reused across lines";

pub fn all() -> Vec<Entry> {
    vec![
        entry!(
            SerdeJsonTyped,
            "serde_json-typed",
            "serde_json",
            "typed",
            "string",
            "serde_json::from_str into owned Event (a &str input skips serde_json's UTF-8 check of string contents, which from_slice would repeat)"
        ),
        entry!(
            SerdeJsonBorrowed,
            "serde_json-borrowed",
            "serde_json",
            "borrowed",
            "string",
            "serde_json::from_str into Cow<'a, str> fields with #[serde(borrow)]: borrowed from the frame when the string has no escape, owned otherwise. Borrows only the input, so retained heap is measured with the frames kept alongside (counts the owned Cows + tag Vecs only)"
        ),
        entry!(
            SerdeJsonValue,
            "serde_json-value",
            "serde_json",
            "dom",
            "string",
            "serde_json::from_str::<serde_json::Value> (BTreeMap objects)"
        ),
        entry!(
            SerdeJsonBorrowValue,
            "serde_json_borrow-value",
            "serde_json_borrow",
            "dom",
            "string",
            "serde_json::from_str::<serde_json_borrow::Value>: Cow strings into the frame, objects as Vec of pairs. Borrows only the input, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            SimdJsonTyped,
            "simd_json-typed",
            "simd-json",
            "typed",
            "bytes-copy",
            COPY_TYPED
        ),
        entry!(
            SimdJsonBorrowedStruct,
            "simd_json-borrowed-struct",
            "simd-json",
            "borrowed",
            "bytes-copy",
            "simd_json::serde::from_slice_with_buffers into a struct of &str pointing into the in-place-unescaped scratch copy (copy timed, as simd_json-typed); results borrow the reused scratch, so no retained heap"
        ),
        entry!(
            SimdJsonBorrowedValue,
            "simd_json-borrowed",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::to_borrowed_value_with_buffers: BorrowedValue with Cow strings into the reused scratch copy (copy timed); borrows the scratch, so no retained heap"
        ),
        entry!(
            SimdJsonOwnedValue,
            "simd_json-owned-value",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::to_owned_value_with_buffers: OwnedValue (frame copied into a reused scratch buffer first, timed)"
        ),
        entry!(
            SimdJsonTape,
            "simd_json-tape",
            "simd-json",
            "dom",
            "bytes-copy",
            "simd_json::fill_tape into a reused Tape, read through tape::Value (frame copied into a reused scratch buffer first, timed); the tape is reused, so no retained heap"
        ),
        entry!(
            SimdJsonDeriveTyped,
            "simd_json_derive-typed",
            "simd-json-derive",
            "typed",
            "bytes-copy",
            "simd-json-derive (no serde) from_slice_with_buffers into owned Event, frame copied into a reused scratch buffer first (timed); runs on simd-json 0.17.3, which simd-json-derive 0.18 pins"
        ),
        entry!(
            SimdJsonDeriveBorrowed,
            "simd_json_derive-borrowed",
            "simd-json-derive",
            "borrowed",
            "bytes-copy",
            "simd-json-derive into a struct of &str into the reused scratch copy (copy timed); runs on simd-json 0.17.3; borrows the scratch, so no retained heap"
        ),
        entry!(
            SonicTyped,
            "sonic_rs-typed",
            "sonic-rs",
            "typed",
            "string",
            "sonic_rs::from_str into owned Event (&str input: no UTF-8 re-check, which from_slice would add)"
        ),
        entry!(
            SonicBorrowed,
            "sonic_rs-borrowed",
            "sonic-rs",
            "borrowed",
            "string",
            "sonic_rs::from_str into Cow<'a, str> fields with #[serde(borrow)]. Borrows only the input, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            SonicValue,
            "sonic_rs-value",
            "sonic-rs",
            "dom",
            "string",
            "sonic_rs::from_str::<sonic_rs::Value>"
        ),
        entry!(
            SonicLazy,
            "sonic_rs-lazy",
            "sonic-rs",
            "typed",
            "string",
            "lazy API on the &str: to_object_iter + LazyValue::as_str/into_array_iter, materialized into owned Event"
        ),
        entry!(
            JiterValue,
            "jiter-value",
            "jiter",
            "dom",
            "string",
            "jiter::JsonValue::parse(s.as_bytes()) (Cow strings into the frame, Arc'd arrays/objects; jiter takes bytes only and validates string contents itself). Borrows only the input, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            JiterValueScratch,
            "jiter-value-scratch",
            "jiter",
            "dom",
            "string",
            "JsonValueScratch::parse(s.as_bytes()) reused across lines (parse stacks and string tape kept); the value borrows only the frame, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            JiterTyped,
            "jiter-typed",
            "jiter",
            "typed",
            "string",
            "hand-written loop over jiter's pull API (Jiter::new(s.as_bytes()), next_object/next_str/...) into owned Event"
        ),
        entry!(
            JiterSerdeTyped,
            "jiter-serde-typed",
            "jiter",
            "typed",
            "string",
            "jiter::serde::from_str into owned Event (from_str is from_slice(s.as_bytes()))"
        ),
        entry!(
            JiterSerdeBorrowed,
            "jiter-serde-borrowed",
            "jiter",
            "borrowed",
            "string",
            "jiter::serde::from_str into Cow fields. Borrows only the input, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            JsonDom,
            "json-dom",
            "json",
            "dom",
            "string",
            "json::parse(&str) (json-rust JsonValue)"
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
            "nanoserde DeJson::deserialize_json(&str) into owned Event"
        ),
        entry!(
            TinyjsonDom,
            "tinyjson-dom",
            "tinyjson",
            "dom",
            "string",
            "str::parse::<tinyjson::JsonValue> (numbers are f64; exact for NIP-01 integers < 2^53)"
        ),
        entry!(
            HifijsonValue,
            "hifijson-value",
            "hifijson",
            "dom",
            "string",
            "hifijson value::parse_unbounded on SliceLexer::new(s.as_bytes()) (numbers kept as text, Cow strings into the frame). Borrows only the input, so retained heap is measured with the frames kept alongside"
        ),
        entry!(
            HifijsonSerdeTyped,
            "hifijson-serde-typed",
            "hifijson",
            "typed",
            "string",
            "hifijson::serde::exactly_one on SliceLexer::new(s.as_bytes()) into owned Event"
        ),
        entry!(
            StrusonTyped,
            "struson-typed",
            "struson",
            "typed",
            "string",
            "hand-written loop over struson's JsonStreamReader over s.as_bytes() (a Read over the slice) into owned Event"
        ),
        entry!(
            SimdjsonRustTyped,
            "simdjson_rust-typed",
            "simdjson-rust",
            "typed",
            "bytes-copy",
            "dom::Parser::parse_str (copies the frame into the parser's reused padded buffer, timed) + the crate's serde tape deserializer into owned Event; C++ allocations are not counted"
        ),
        entry!(
            SimdjsonRustBorrowed,
            "simdjson_rust-borrowed",
            "simdjson-rust",
            "borrowed",
            "bytes-copy",
            "as simdjson_rust-typed, into a struct of &str pointing into simdjson's reused string buffer, so no retained heap"
        ),
        entry!(
            SimdjsonRustDom,
            "simdjson_rust-dom",
            "simdjson-rust",
            "dom",
            "bytes-copy",
            "parse_str (padded copy timed), materialized as simdjson_rust::tape::Value (Vec-backed, &str into the reused string buffer, so no retained heap)"
        ),
        entry!(
            Utf8Baseline,
            "std-from_utf8",
            "std",
            "baseline",
            "bytes",
            "std::str::from_utf8 over the frame's bytes: the UTF-8 validation the WebSocket client already paid to hand over a &str (tungstenite Message::Text); no parser row pays it again"
        ),
    ]
}
