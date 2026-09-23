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


COMMANDS = {
    "cases": cmd_cases,
    "region": cmd_region,
    "vectors": cmd_vectors,
    "verdict": cmd_verdict,
    "work": cmd_work,
    "verify": cmd_verify,
}

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in COMMANDS:
        raise SystemExit(f"usage: refdriver.py <{'|'.join(COMMANDS)}>")
    COMMANDS[sys.argv[1]]()
