//! simd-json-derive glue. The derive expands to `::simd_json::…` paths and simd-json-derive
//! 0.18 depends on simd-json 0.17, so the derived types live in this crate, which sees
//! simd-json 0.17 as `simd_json`. The harness crate uses simd-json 0.18 for its own rows.

use simd_json_derive::Deserialize;

pub use simd_json::Buffers;

/// The owned Nostr event (`typed` mode).
#[derive(Deserialize, Debug, Default)]
pub struct Event {
    pub id: String,
    pub pubkey: String,
    pub created_at: u64,
    pub kind: u32,
    pub tags: Vec<Vec<String>>,
    pub content: String,
    pub sig: String,
}

/// The event with strings borrowed from the (in-place unescaped) input buffer.
#[derive(Deserialize, Debug)]
pub struct EventRef<'a> {
    pub id: &'a str,
    pub pubkey: &'a str,
    pub created_at: u64,
    pub kind: u32,
    pub tags: Vec<Vec<&'a str>>,
    pub content: &'a str,
    pub sig: &'a str,
}

pub fn new_buffers() -> Buffers {
    Buffers::default()
}

#[inline]
pub fn parse_owned(input: &mut [u8], buffers: &mut Buffers) -> Result<Event, String> {
    Event::from_slice_with_buffers(input, buffers).map_err(|e| e.to_string())
}

#[inline]
pub fn parse_borrowed<'a>(input: &'a mut [u8], buffers: &mut Buffers) -> Result<EventRef<'a>, String> {
    EventRef::from_slice_with_buffers(input, buffers).map_err(|e| e.to_string())
}
