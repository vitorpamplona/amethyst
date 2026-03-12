
#ifndef sodium_H
#define sodium_H

#include "sodium/version.h"

#include "sodium/core.h"
#include "sodium/crypto_aead_xchacha20poly1305.h"
#include "sodium/crypto_core_hchacha20.h"
#include "sodium/crypto_stream_chacha20.h"
#include "sodium/runtime.h"
#include "sodium/utils.h"

#ifndef SODIUM_LIBRARY_MINIMAL
#include "sodium/crypto_box_curve25519xchacha20poly1305.h"
#include "sodium/crypto_core_ed25519.h"
#include "sodium/crypto_core_ristretto255.h"
#include "sodium/crypto_pwhash_scryptsalsa208sha256.h"
#include "sodium/crypto_scalarmult_ed25519.h"
#include "sodium/crypto_scalarmult_ristretto255.h"
#include "sodium/crypto_secretbox_xchacha20poly1305.h"
#include "sodium/crypto_stream_salsa2012.h"
#include "sodium/crypto_stream_salsa208.h"
#include "sodium/crypto_stream_xchacha20.h"
#endif

#endif
