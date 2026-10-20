//! A counting `#[global_allocator]` over the system allocator.
//!
//! The harness is single-threaded while it measures, so the counters use relaxed
//! load + store instead of a locked read-modify-write: that keeps the bookkeeping to two
//! plain adds per allocation, not a `lock xadd` that would tax allocation-heavy parsers.
//! A `realloc` counts as one allocation of the new size (what a malloc + copy would cost),
//! matching how Go's `TotalAlloc` and the JVM's allocated-bytes counter see growth.
//! Memory that C/C++ code allocates with its own `malloc` (simdjson-rust's parser) is not seen.

use std::alloc::{GlobalAlloc, Layout, System};
use std::sync::atomic::{AtomicU64, Ordering::Relaxed};

pub struct Counting;

static BYTES: AtomicU64 = AtomicU64::new(0);
static COUNT: AtomicU64 = AtomicU64::new(0);

#[inline(always)]
fn bump(size: usize) {
    BYTES.store(BYTES.load(Relaxed).wrapping_add(size as u64), Relaxed);
    COUNT.store(COUNT.load(Relaxed).wrapping_add(1), Relaxed);
}

unsafe impl GlobalAlloc for Counting {
    #[inline]
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        bump(layout.size());
        unsafe { System.alloc(layout) }
    }

    #[inline]
    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        bump(layout.size());
        unsafe { System.alloc_zeroed(layout) }
    }

    #[inline]
    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        unsafe { System.dealloc(ptr, layout) }
    }

    #[inline]
    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, new_size: usize) -> *mut u8 {
        bump(new_size);
        unsafe { System.realloc(ptr, layout, new_size) }
    }
}

/// (bytes, allocations) since process start.
#[inline]
pub fn snapshot() -> (u64, u64) {
    (BYTES.load(Relaxed), COUNT.load(Relaxed))
}
