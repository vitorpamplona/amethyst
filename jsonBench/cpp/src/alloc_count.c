// Counting allocator: interposes the C allocation entry points in the executable and forwards
// to glibc's own implementations. Because the executable is first in the dynamic symbol
// lookup order (and is linked with -rdynamic), this also sees libstdc++'s operator new,
// aligned new, and every C library (yyjson, cJSON, json-c, Jansson, OpenSSL).
//
// Counting rule: every successful malloc/calloc/realloc/memalign-family call is one
// allocation; bytes are the requested size (for realloc, the full new size).
//
// Live bytes (for the retained-heap measurement): every block adds its malloc_usable_size()
// when it is handed out and subtracts it when it is freed; realloc applies the difference.
// That is the heap the block really occupies (requested size rounded up to glibc's chunk
// granularity), not counting glibc's 8-byte chunk header.
// The harness is single-threaded, so plain counters are enough.
#define _GNU_SOURCE
#include <errno.h>
#include <malloc.h>
#include <stddef.h>
#include <stdint.h>

extern void* __libc_malloc(size_t);
extern void* __libc_calloc(size_t, size_t);
extern void* __libc_realloc(void*, size_t);
extern void __libc_free(void*);
extern void* __libc_memalign(size_t, size_t);

uint64_t jb_alloc_count = 0;
uint64_t jb_alloc_bytes = 0;
int64_t jb_live_bytes = 0;

static inline void* counted(void* p, size_t n) {
  if (p) {
    jb_alloc_count++;
    jb_alloc_bytes += n;
    jb_live_bytes += (int64_t)malloc_usable_size(p);
  }
  return p;
}

void* malloc(size_t n) { return counted(__libc_malloc(n), n); }

void* calloc(size_t a, size_t b) {
  size_t n;
  if (__builtin_mul_overflow(a, b, &n)) return __libc_calloc(a, b);
  return counted(__libc_calloc(a, b), n);
}

void* realloc(void* p, size_t n) {
  const int64_t old = p ? (int64_t)malloc_usable_size(p) : 0;
  void* r = __libc_realloc(p, n);
  if (r) {
    jb_live_bytes += (int64_t)malloc_usable_size(r) - old;
    if (n) {
      jb_alloc_count++;
      jb_alloc_bytes += n;
    }
  } else if (n == 0) {
    jb_live_bytes -= old;  // glibc's realloc(p, 0) frees p
  }
  return r;
}

void free(void* p) {
  if (p) jb_live_bytes -= (int64_t)malloc_usable_size(p);
  __libc_free(p);
}

void* memalign(size_t align, size_t n) { return counted(__libc_memalign(align, n), n); }

void* aligned_alloc(size_t align, size_t n) { return counted(__libc_memalign(align, n), n); }

int posix_memalign(void** out, size_t align, size_t n) {
  if (align < sizeof(void*) || (align & (align - 1)) != 0) return EINVAL;
  void* p = __libc_memalign(align, n);
  if (!p) return ENOMEM;
  *out = counted(p, n);
  return 0;
}

void* valloc(size_t n) { return counted(__libc_memalign(4096, n), n); }

void* pvalloc(size_t n) {
  size_t r = (n + 4095) & ~(size_t)4095;
  return counted(__libc_memalign(4096, r ? r : 4096), r);
}
