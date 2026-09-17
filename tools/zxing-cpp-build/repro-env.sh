# Deterministic build environment for libzxingcpp_android.so.
#
# Sourced by build-zxingcpp.sh so every build path stays in lockstep. Makes the
# native library byte-for-byte reproducible, which is what lets F-Droid,
# Zapstore or any third party rebuild the shipped .so from this tag and confirm
# it matches — the same bar tools/arti-build holds libarti_android.so to.
#
# The caller must already have set:
#   SCRIPT_DIR   — tools/zxing-cpp-build
#   SOURCE_DIR   — the zxing-cpp clone (.zxing-cpp-source)
#   BUILD_ROOT   — the canonical build root
#
# What makes a C++ shared library non-reproducible, and the fix for each:
#   1. Compiler + linker version -> the pinned NDK (ANDROID_NDK_VERSION). clang
#      and lld stamp their versions into .comment exactly as rustc does.
#   2. Upstream source           -> pinned git tag (ZXING_CPP_VERSION), cloned
#      at that tag and nothing else.
#   3. Absolute paths baked into __FILE__, assertions and debug records
#                                -> -ffile-prefix-map rewrites them to stable
#      virtual paths, so two machines with different checkout dirs agree.
#   4. Timestamps                -> SOURCE_DATE_EPOCH, derived from the pinned
#      tag's commit rather than from when the build happens.
#   5. Archive metadata          -> ar writes mtimes/uids into static archives;
#      the D (deterministic) flag zeroes them. The NDK's llvm-ar defaults to D,
#      but it is set explicitly so a host ar cannot change the answer.

# Rewrite every host-specific absolute prefix the compiler would otherwise bake
# into the binary. Both flags are needed: -ffile-prefix-map covers __FILE__ and
# debug info, -fdebug-prefix-map is kept for older clangs that ignore the first.
REPRO_CFLAGS="-ffile-prefix-map=${SOURCE_DIR}=/zxing-cpp"
REPRO_CFLAGS="${REPRO_CFLAGS} -ffile-prefix-map=${BUILD_ROOT}=/build"
REPRO_CFLAGS="${REPRO_CFLAGS} -fdebug-prefix-map=${SOURCE_DIR}=/zxing-cpp"
REPRO_CFLAGS="${REPRO_CFLAGS} -fdebug-prefix-map=${BUILD_ROOT}=/build"

# No __DATE__/__TIME__ anywhere in the output, whatever upstream does with them.
REPRO_CFLAGS="${REPRO_CFLAGS} -Wno-builtin-macro-redefined -D__DATE__=\"redacted\" -D__TIME__=\"redacted\""

export ZXING_REPRO_CFLAGS="${REPRO_CFLAGS}"

# lld's default build-id is a hash of the content, so it is already a function
# of the input bytes — but pin it rather than inherit whatever the NDK's
# default becomes. A content hash also means a matching rebuild keeps the same
# BuildID, which is what an auditor compares.
export ZXING_REPRO_LDFLAGS="-Wl,--build-id=sha1"

# Pin SOURCE_DATE_EPOCH to the commit the tag points at: deterministic for a
# given ZXING_CPP_VERSION, and independent of when the build actually runs.
if [ -d "${SOURCE_DIR}/.git" ]; then
    _epoch="$(git -C "${SOURCE_DIR}" log -1 --format=%ct 2>/dev/null || true)"
    [ -n "${_epoch}" ] && export SOURCE_DATE_EPOCH="${_epoch}"
    unset _epoch
fi
