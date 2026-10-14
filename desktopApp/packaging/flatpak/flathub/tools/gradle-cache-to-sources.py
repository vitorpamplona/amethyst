#!/usr/bin/env python3
"""Turn a Gradle user home's module cache into flatpak-builder sources.

Run the build once online with a fresh GRADLE_USER_HOME; the cache then holds
exactly what the build resolved. Every file becomes a `type: file` source laid
out as a Maven repository under `offline-repository/`, which the offline build
reads through an init script. URLs are found by asking the repositories
settings.gradle.kts declares, in its order, with a HEAD request; a file no
repository serves is an error, never a silent omission.

usage: gradle-cache-to-sources.py GRADLE_USER_HOME OUT.json [MORE_HOMES...]
"""
import concurrent.futures
import hashlib
import json
import os
import sys
import urllib.error
import urllib.request

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo.maven.apache.org/maven2"
PORTAL = "https://plugins.gradle.org/m2"
JITPACK = "https://jitpack.io"


def repos_for(group):
    google_first = group.startswith(("com.android", "com.google", "androidx"))
    order = [GOOGLE, CENTRAL, PORTAL, JITPACK] if google_first else [CENTRAL, PORTAL, GOOGLE, JITPACK]
    return order


def exists(url):
    req = urllib.request.Request(url, method="HEAD", headers={"User-Agent": "flatpak-sources-generator"})
    for _ in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.status == 200
        except urllib.error.HTTPError as e:
            if e.code in (404, 403, 410):
                return False
        except Exception:
            pass
    return False


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def module_urls(vdir):
    """Gradle module metadata names a file one way (the cache keeps `name`) and
    serves it under another (`url`, relative to the module's directory)."""
    urls = {}
    for sha1dir in os.listdir(vdir):
        for name in os.listdir(os.path.join(vdir, sha1dir)):
            if not name.endswith(".module"):
                continue
            with open(os.path.join(vdir, sha1dir, name)) as f:
                meta = json.load(f)
            for variant in meta.get("variants", []):
                for entry in variant.get("files", []):
                    urls[entry["name"]] = entry["url"]
    return urls


def collect(home, found):
    root = os.path.join(home, "caches", "modules-2", "files-2.1")
    for group in os.listdir(root):
        for module in os.listdir(os.path.join(root, group)):
            for version in os.listdir(os.path.join(root, group, module)):
                vdir = os.path.join(root, group, module, version)
                urls = module_urls(vdir)
                for sha1dir in os.listdir(vdir):
                    for cached in os.listdir(os.path.join(vdir, sha1dir)):
                        path = os.path.join(vdir, sha1dir, cached)
                        # The repository (and so the offline one) has it under its url.
                        name = urls.get(cached, cached)
                        key = (group, module, version, name)
                        digest = sha256(path)
                        prev = found.get(key)
                        if prev and prev[1] != digest:
                            print(f"warning: two different {key}; keeping the first", file=sys.stderr)
                            continue
                        found.setdefault(key, (path, digest))


def resolve(item):
    (group, module, version, name), (_, digest) = item
    rel = f"{group.replace('.', '/')}/{module}/{version}/{name}"
    for repo in repos_for(group):
        url = f"{repo}/{rel}"
        if exists(url):
            return {
                "type": "file",
                "url": url,
                "sha256": digest,
                "dest": f"offline-repository/{group.replace('.', '/')}/{module}/{version}",
                "dest-filename": name,
            }
    return {"missing": rel}


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    out = sys.argv[2]
    found = {}
    for home in [sys.argv[1]] + sys.argv[3:]:
        collect(home, found)
    print(f"{len(found)} files", file=sys.stderr)
    with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
        results = list(pool.map(resolve, sorted(found.items())))
    missing = [r["missing"] for r in results if "missing" in r]
    if missing:
        print(f"{len(missing)} files no repository serves:", file=sys.stderr)
        for m in missing:
            print("  " + m, file=sys.stderr)
        sys.exit(1)
    results.sort(key=lambda r: (r["dest"], r["dest-filename"]))
    with open(out, "w") as f:
        json.dump(results, f, indent=2)
        f.write("\n")
    print(f"wrote {len(results)} sources to {out}", file=sys.stderr)


if __name__ == "__main__":
    main()
