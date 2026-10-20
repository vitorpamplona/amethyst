//! The event shapes the parsers decode into, and how a parse result is observed:
//! `fold` (timed: kind + tag count + content length) and `digest` (untimed, HARNESS.md).

use sha2::{Digest, Sha256};
use std::borrow::Cow;

pub type R<T> = Result<T, String>;

#[inline]
pub fn e<E: std::fmt::Display>(err: E) -> String {
    err.to_string()
}

/// The owned Nostr event: what a client keeps (`typed` mode).
#[derive(Debug, Default, serde::Deserialize, miniserde::Deserialize, nanoserde::DeJson)]
pub struct Event {
    pub id: String,
    pub pubkey: String,
    pub created_at: u64,
    pub kind: u32,
    pub tags: Vec<Vec<String>>,
    pub content: String,
    pub sig: String,
}

/// A string that borrows from the input when it has no escape, owned otherwise.
/// The newtype is needed for the strings inside `tags`: serde only borrows a `Cow` that
/// is a direct `#[serde(borrow)]` field, a bare `Vec<Cow<str>>` always copies.
#[derive(Debug, serde::Deserialize)]
pub struct CowStr<'a>(#[serde(borrow)] pub Cow<'a, str>);

/// serde `borrowed` mode: `Cow` fields (plain `&str` fails on the first escaped string).
#[derive(Debug, serde::Deserialize)]
pub struct EventCow<'a> {
    #[serde(borrow)]
    pub id: Cow<'a, str>,
    #[serde(borrow)]
    pub pubkey: Cow<'a, str>,
    pub created_at: u64,
    pub kind: u32,
    #[serde(borrow)]
    pub tags: Vec<Vec<CowStr<'a>>>,
    #[serde(borrow)]
    pub content: Cow<'a, str>,
    #[serde(borrow)]
    pub sig: Cow<'a, str>,
}

/// `borrowed` mode for parsers that unescape into a buffer they own (simd-json in place,
/// simdjson's string buffer), so every string can be a plain `&str`.
#[derive(Debug, serde::Deserialize)]
pub struct EventRef<'a> {
    pub id: &'a str,
    pub pubkey: &'a str,
    pub created_at: u64,
    pub kind: u32,
    pub tags: Vec<Vec<&'a str>>,
    pub content: &'a str,
    pub sig: &'a str,
}

const US: u8 = 0x1F;
const RS: u8 = 0x1E;
const GS: u8 = 0x1D;
const FS: u8 = 0x1C;

/// The cross-language digest of HARNESS.md (`digest_event` in corpus/prepare.py).
#[allow(clippy::too_many_arguments)]
pub fn digest_fields<'s, T, I>(
    h: &mut Sha256,
    id: &str,
    pubkey: &str,
    created_at: u64,
    kind: u64,
    tags: T,
    content: &str,
    sig: &str,
) where
    T: IntoIterator<Item = I>,
    I: IntoIterator<Item = &'s str>,
{
    let mut num = itoa_buf();
    h.update(id.as_bytes());
    h.update([US]);
    h.update(pubkey.as_bytes());
    h.update([US]);
    h.update(fmt_u64(&mut num, created_at));
    h.update([US]);
    h.update(fmt_u64(&mut num, kind));
    h.update([US]);
    for tag in tags {
        for v in tag {
            h.update(v.as_bytes());
            h.update([US]);
        }
        h.update([RS]);
    }
    h.update([GS]);
    h.update(content.as_bytes());
    h.update([US]);
    h.update(sig.as_bytes());
    h.update([US]);
    h.update([FS]);
}

fn itoa_buf() -> [u8; 20] {
    [0; 20]
}

fn fmt_u64(buf: &mut [u8; 20], mut v: u64) -> &[u8] {
    let mut i = buf.len();
    loop {
        i -= 1;
        buf[i] = b'0' + (v % 10) as u8;
        v /= 10;
        if v == 0 {
            break;
        }
    }
    &buf[i..]
}

/// What the harness does with one parse result.
pub trait View {
    /// Timed: fold something from the event into the sink.
    fn fold(&self) -> u64;
    /// Untimed: feed the decoded event into the digest.
    fn digest(&self, h: &mut Sha256) -> R<()>;
}

#[inline(always)]
fn sum(kind: u64, tags: usize, content: usize) -> u64 {
    kind.wrapping_add(tags as u64).wrapping_add(content as u64)
}

macro_rules! struct_view {
    ($ty:ty, |$s:ident| $tag_str:expr) => {
        impl View for $ty {
            #[inline(always)]
            fn fold(&self) -> u64 {
                sum(self.kind as u64, self.tags.len(), self.content.len())
            }
            fn digest(&self, h: &mut Sha256) -> R<()> {
                digest_fields(
                    h,
                    &self.id,
                    &self.pubkey,
                    self.created_at,
                    self.kind as u64,
                    self.tags.iter().map(|t| t.iter().map(|$s| $tag_str)),
                    &self.content,
                    &self.sig,
                );
                Ok(())
            }
        }
    };
}

struct_view!(Event, |s| s.as_str());
struct_view!(EventCow<'_>, |s| s.0.as_ref());
struct_view!(EventRef<'_>, |s| *s);
struct_view!(sjd::Event, |s| s.as_str());
struct_view!(sjd::EventRef<'_>, |s| *s);

/// Read access to a library's generic tree, so one `View` serves every `dom` row.
pub trait Dom {
    fn field(&self, key: &str) -> Option<&Self>;
    fn string(&self) -> Option<&str>;
    fn uint(&self) -> Option<u64>;
    fn array_len(&self) -> Option<usize>;
    /// Untimed only (digest): the array's elements.
    fn items(&self) -> Option<Vec<&Self>>;
}

/// Wraps a `Dom` value as a parse result.
pub struct D<T>(pub T);

impl<T: Dom> View for D<T> {
    #[inline(always)]
    fn fold(&self) -> u64 {
        let v = &self.0;
        let kind = v.field("kind").and_then(Dom::uint).unwrap_or(0);
        let tags = v.field("tags").and_then(Dom::array_len).unwrap_or(0);
        let content = v.field("content").and_then(Dom::string).map_or(0, str::len);
        sum(kind, tags, content)
    }

    fn digest(&self, h: &mut Sha256) -> R<()> {
        let v = &self.0;
        let s = |k: &str| {
            v.field(k)
                .and_then(Dom::string)
                .ok_or_else(|| format!("missing string {k}"))
        };
        let n = |k: &str| {
            v.field(k)
                .and_then(Dom::uint)
                .ok_or_else(|| format!("missing integer {k}"))
        };
        let mut tags: Vec<Vec<&str>> = Vec::new();
        for t in v.field("tags").and_then(Dom::items).ok_or("missing tags")? {
            let mut tag = Vec::new();
            for x in t.items().ok_or("tag is not an array")? {
                tag.push(x.string().ok_or("tag element is not a string")?);
            }
            tags.push(tag);
        }
        digest_fields(
            h,
            s("id")?,
            s("pubkey")?,
            n("created_at")?,
            n("kind")?,
            tags,
            s("content")?,
            s("sig")?,
        );
        Ok(())
    }
}
