#!/usr/bin/env python3
"""Adapter over the cyberspace project's own reference implementations.

Speaks one JSON object per line on stdout so `sno-conformance.sh` can diff it
against `amy sno … --json` with jq. Nothing here reimplements anything: it
imports `decks/sno-reference.py` from the cyberspace spec repo (the §1.9
arbiter) and `cyberspace_core/avatar.py` from cyberspace-cli (the §8.10 work
and payment reference), and just re-emits what they answer.

  refdriver.py cases                 the deck's own rejection table, + Appendix A
  refdriver.py vectors               cyberspace-cli's avatar-work golden vectors
  refdriver.py verdict < payload     {"valid":…, "rule":…}
  refdriver.py work    < payload     {"required":…}
  refdriver.py verify  < event       {"ok":…, "required":…, "committed":…, "zeros":…, "reason":…}
  refdriver.py region COORD HEIGHT   {"key":…, "lookup_id":…, "x":…, "y":…, "z":…, "plane":…}
  refdriver.py hints                 §7.7's golden hint vectors, one per line
  refdriver.py encrypt COORD HEIGHT  seal stdin into a kind-33330 bag at that region
  refdriver.py box COORD HX HY HZ    the §7.7 hint tag for the box around that coord

Paths come from $CYBERSPACE_DIR and $CYBERSPACE_CLI_DIR.
"""
import importlib.util
import json
import os
import signal
import sys

# Used in pipelines (`| head`), where dying on SIGPIPE is the right answer
# rather than a traceback.
signal.signal(signal.SIGPIPE, signal.SIG_DFL)


def _load(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    if spec is None:
        raise SystemExit(f"cannot load {path}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules[name] = mod
    spec.loader.exec_module(mod)
    return mod


def _sno():
    root = os.environ.get("CYBERSPACE_DIR")
    if not root:
        raise SystemExit("CYBERSPACE_DIR is not set")
    return _load(os.path.join(root, "decks", "sno-reference.py"), "sno_reference")


def _avatar():
    root = os.environ.get("CYBERSPACE_CLI_DIR")
    if not root:
        raise SystemExit("CYBERSPACE_CLI_DIR is not set")
    sys.path.insert(0, os.path.join(root, "src"))
    return _load(os.path.join(root, "src", "cyberspace_core", "avatar.py"), "ref_avatar")


def emit(obj):
    print(json.dumps(obj, separators=(",", ":"), sort_keys=True))


def cmd_cases():
    sno = _sno()
    emit({"name": "appendix A", "rule": None, "payload": dict(sno.APPENDIX_A)})
    for label, payload in sno._rejections():
        emit({"name": label, "rule": label.replace("rule ", ""), "payload": payload})


def cmd_vectors():
    root = os.environ.get("CYBERSPACE_CLI_DIR")
    if not root:
        raise SystemExit("CYBERSPACE_CLI_DIR is not set")
    with open(os.path.join(root, "tests", "fixtures", "avatar_work.json")) as fh:
        for case in json.load(fh):
            emit({"name": case["name"], "required": case["required"], "payload": case["payload"]})


def cmd_verdict():
    sno = _sno()
    payload = json.load(sys.stdin)
    try:
        sno.validate(dict(payload))
        emit({"valid": True, "rule": None})
    except sno.SnoError as err:
        text = str(err)
        rule = text.split(":")[0].replace("rule ", "").strip() if text.startswith("rule ") else "?"
        emit({"valid": False, "rule": rule})


def cmd_work():
    avatar = _avatar()
    emit({"required": avatar.avatar_work(json.load(sys.stdin))})


def cmd_verify():
    avatar = _avatar()
    result = avatar.verify_avatar_work(json.load(sys.stdin))
    emit({k: result[k] for k in ("ok", "required", "committed", "zeros", "reason")})


def cmd_region():
    """§2.2 decode plus §7.2 derivation, from cyberspace-cli's own modules.

    `location_encryption.py` imports AESGCM at module scope and that binding is
    not always present; `cantor` and `movement` are the whole of what a region
    key needs, and going through them keeps this an adapter rather than a
    reimplementation.
    """
    sys.path.insert(0, os.path.join(os.environ["CYBERSPACE_CLI_DIR"], "src"))
    from cyberspace_core.coords import coord_to_xyz
    from cyberspace_core.cantor import cantor_pair, int_to_bytes_be_min, sha256
    from cyberspace_core.movement import compute_subtree_cantor

    coord_hex, height = sys.argv[2], int(sys.argv[3])
    x, y, z, plane = coord_to_xyz(int(coord_hex, 16))
    base = lambda v: (v >> height) << height if height > 0 else v
    region_n = cantor_pair(
        cantor_pair(
            compute_subtree_cantor(base(x), height),
            compute_subtree_cantor(base(y), height),
        ),
        compute_subtree_cantor(base(z), height),
    )
    key = sha256(int_to_bytes_be_min(region_n))
    emit({
        "key": key.hex(),
        "lookup_id": sha256(key).hex(),
        "x": str(x), "y": str(y), "z": str(z),
        "plane": "ideaspace" if plane else "dataspace",
    })


def cmd_hints():
    """§7.7's golden vectors, straight out of the spec's own hint-reference.py."""
    root = os.environ["CYBERSPACE_DIR"]
    # It sits at the repo root, beside CYBERSPACE_V2.md, rather than under
    # decks/ where the DECK references live; accept either in case it moves.
    path = next(
        (p for p in (os.path.join(root, "hint-reference.py"), os.path.join(root, "decks", "hint-reference.py")) if os.path.exists(p)),
        None,
    )
    if path is None:
        raise SystemExit("hint-reference.py not found under $CYBERSPACE_DIR")
    hint_ref = _load(path, "hint_reference")
    for name, vector in hint_ref.vectors().items():
        emit({"name": name, **vector})


def cmd_encrypt():
    """Seal stdin into a §8.6 bag, using cyberspace-cli's own cipher and event builder.

    The point of the round trip is that nothing on this side is ours: the key
    comes from `location_encryption`, the sealing from `encrypt_with_location_key`
    and the tags from `make_encrypted_content_event`. What `amy cyberspace open`
    then has to do is derive the same key from the same coordinate and read a
    ciphertext it never saw made.

    A fixed nonce keeps the fixture reproducible; §7.6 wants a fresh one per
    bag, which matters for a hider and not for a test that seals once.
    """
    sys.path.insert(0, os.path.join(os.environ["CYBERSPACE_CLI_DIR"], "src"))
    import base64

    from cyberspace_core.coords import coord_to_xyz
    from cyberspace_cli.nostr_event import make_encrypted_content_event
    from cyberspace_core.location_encryption import (
        derive_region_key_material_for_height,
        encrypt_with_location_key,
    )

    coord_hex, height = sys.argv[2], int(sys.argv[3])
    x, y, z, _plane = coord_to_xyz(int(coord_hex, 16))
    material = derive_region_key_material_for_height(x=x, y=y, z=z, height=height)
    payload = encrypt_with_location_key(
        sys.stdin.buffer.read(),
        location_decryption_key=material.location_decryption_key,
        nonce=bytes(range(12)),
    )
    event = make_encrypted_content_event(
        pubkey_hex="b" * 64,
        created_at=1,
        lookup_id_hex=material.lookup_id_hex,
        algorithm="aes-256-gcm",
        ciphertext_b64=base64.b64encode(payload).decode("ascii"),
        height_hint=height,
        content="",
        kind=33330,
    )
    emit({
        "event": event,
        "key": material.location_decryption_key.hex(),
        "lookup_id": material.lookup_id_hex,
    })


def cmd_box():
    """The §7.7 `hint` tag naming the aligned box around a coordinate.

    Built from the reference's own interleave so a sweep test is not marking
    its own homework: the box comes from their `coord_to_xyz`/`xyz_to_coord`,
    and if our alignment disagreed the bag would simply not be in the box we
    were handed.
    """
    sys.path.insert(0, os.path.join(os.environ["CYBERSPACE_CLI_DIR"], "src"))
    from cyberspace_core.coords import coord_to_xyz, xyz_to_coord

    coord_hex = sys.argv[2]
    hx, hy, hz = (int(v) for v in sys.argv[3:6])
    x, y, z, plane = coord_to_xyz(int(coord_hex, 16))
    base = xyz_to_coord((x >> hx) << hx, (y >> hy) << hy, (z >> hz) << hz, plane)
    emit({"tag": ["hint", f"{base:064x}", str(hx), str(hy), str(hz)]})


COMMANDS = {
    "box": cmd_box,
    "cases": cmd_cases,
    "encrypt": cmd_encrypt,
    "region": cmd_region,
    "hints": cmd_hints,
    "vectors": cmd_vectors,
    "verdict": cmd_verdict,
    "work": cmd_work,
    "verify": cmd_verify,
}

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        raise SystemExit(f"usage: refdriver.py <{'|'.join(COMMANDS)}>")
    COMMANDS[sys.argv[1]]()
