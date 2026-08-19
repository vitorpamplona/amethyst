"""Minimal BIP-340 schnorr signing (secp256k1) — enough for NIP-42 AUTH."""
import hashlib, os

p = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F
n = 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
G = (0x79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798,
     0x483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8)

def point_add(P, Q):
    if P is None: return Q
    if Q is None: return P
    if P[0] == Q[0] and P[1] != Q[1]: return None
    if P == Q: lam = 3 * P[0] * P[0] * pow(2 * P[1], p - 2, p) % p
    else: lam = (Q[1] - P[1]) * pow(Q[0] - P[0], p - 2, p) % p
    x3 = (lam * lam - P[0] - Q[0]) % p
    return (x3, (lam * (P[0] - x3) - P[1]) % p)

def point_mul(P, k):
    R = None
    while k:
        if k & 1: R = point_add(R, P)
        P = point_add(P, P); k >>= 1
    return R

def bytes_from_int(x): return x.to_bytes(32, "big")
def tagged_hash(tag, msg):
    t = hashlib.sha256(tag.encode()).digest()
    return hashlib.sha256(t + t + msg).digest()

def pubkey_xonly(seckey: bytes) -> bytes:
    P = point_mul(G, int.from_bytes(seckey, "big") % n)
    return bytes_from_int(P[0])

def sign(msg32: bytes, seckey: bytes) -> bytes:
    d0 = int.from_bytes(seckey, "big") % n
    P = point_mul(G, d0)
    d = d0 if P[1] % 2 == 0 else n - d0
    aux = os.urandom(32)
    t = bytes_from_int(d ^ int.from_bytes(tagged_hash("BIP0340/aux", aux), "big"))
    k0 = int.from_bytes(tagged_hash("BIP0340/nonce", t + bytes_from_int(P[0]) + msg32), "big") % n
    R = point_mul(G, k0)
    k = k0 if R[1] % 2 == 0 else n - k0
    e = int.from_bytes(tagged_hash("BIP0340/challenge",
        bytes_from_int(R[0]) + bytes_from_int(P[0]) + msg32), "big") % n
    return bytes_from_int(R[0]) + bytes_from_int((k + e * d) % n)

def lift_x(x):
    if x >= p: return None
    y_sq = (pow(x, 3, p) + 7) % p
    y = pow(y_sq, (p + 1) // 4, p)
    if pow(y, 2, p) != y_sq: return None
    return (x, y if y % 2 == 0 else p - y)

def verify(msg32: bytes, pubkey32: bytes, sig: bytes) -> bool:
    P = lift_x(int.from_bytes(pubkey32, "big"))
    if P is None: return False
    r = int.from_bytes(sig[:32], "big"); s = int.from_bytes(sig[32:], "big")
    if r >= p or s >= n: return False
    e = int.from_bytes(tagged_hash("BIP0340/challenge", sig[:32] + pubkey32 + msg32), "big") % n
    R = point_add(point_mul(G, s), point_mul(P, n - e))
    return R is not None and R[1] % 2 == 0 and R[0] == r
