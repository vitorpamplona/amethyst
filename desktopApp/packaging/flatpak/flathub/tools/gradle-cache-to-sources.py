#!/usr/bin/env python3
"""Turn a Gradle user home's module cache into flatpak-builder sources.

Run the build once online with a fresh GRADLE_USER_HOME; the cache then holds
exactly what the build resolved. Every file becomes a `type: file` source laid
out as a Maven repository under `offline-repository/`, which the offline build
reads through an init script. URLs are found by asking the repositories
settings.gradle.kts declares, in its order, with a HEAD request; a file no
repository serves is an error, never a silent omission.

The groups Magpie resolves (Nostr + Blossom: quartz, the MLS bindings, crypto-kmp,
...) are not on any Maven repository. Gradle reads them offline from the plain
Maven mirror Magpie keeps in the Gradle home (magpie/mirror/<publisher>/), which
the online build fills. Every file there is a Blossom blob named by its sha256, so
it becomes a source fetched from the Blossom servers its release names, laid out
at that same mirror path under the build's gradle-home/.

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


def release_servers(magpie, pub, group, module, version):
    """The Blossom servers the release events Magpie cached for this version name
    (`server` tags), then the publisher's server list (kind 10063), in order."""
    servers = []
    for path in (os.path.join(magpie, "events", pub, group, module, f"{version}.json"), os.path.join(magpie, "servers", f"{pub}.json")):
        if not os.path.isfile(path):
            continue
        with open(path) as f:
            events = json.load(f)
        for event in events if isinstance(events, list) else [events]:
            for tag in event.get("tags", []):
                if len(tag) > 1 and tag[0] == "server" and tag[1].rstrip("/") not in servers:
                    servers.append(tag[1].rstrip("/"))
    return servers


def collect_magpie(home, found):
    """Magpie's offline mirror: (publisher, group, module, version, name) -> (digest, servers)."""
    magpie = os.path.join(home, "magpie")
    mirror = os.path.join(magpie, "mirror")
    if not os.path.isdir(mirror):
        return
    for pub in os.listdir(mirror):
        for dirpath, _, files in os.walk(os.path.join(mirror, pub)):
            parts = os.path.relpath(dirpath, os.path.join(mirror, pub)).split(os.sep)
            if not files:
                continue
            if len(parts) < 3:
                sys.exit(f"unexpected file in Magpie's mirror: {dirpath}")
            group, module, version = ".".join(parts[:-2]), parts[-2], parts[-1]
            servers = release_servers(magpie, pub, group, module, version)
            for name in files:
                digest = sha256(os.path.join(dirpath, name))
                if not os.path.isfile(os.path.join(magpie, "blobs", digest[:2], digest)):
                    sys.exit(f"{dirpath}/{name} is in Magpie's mirror but is no blob it fetched")
                key = (pub, group, module, version, name)
                prev = found.get(key)
                if prev and prev[0] != digest:
                    print(f"warning: two different {key}; keeping the first", file=sys.stderr)
                    continue
                found.setdefault(key, (digest, servers))


def resolve_magpie(item):
    (pub, group, module, version, name), (digest, servers) = item
    rel = f"{group.replace('.', '/')}/{module}/{version}"
    urls = [f"{server}/{digest}" for server in servers if exists(f"{server}/{digest}")]
    if not urls:
        return {"missing": f"{rel}/{name} (blob {digest}, servers: {', '.join(servers) or 'none known'})"}
    entry = {"type": "file", "url": urls[0]}
    if len(urls) > 1:
        entry["mirror-urls"] = urls[1:]
    entry.update(
        {
            "sha256": digest,
            "dest": f"gradle-home/magpie/mirror/{pub}/{rel}",
            "dest-filename": name,
        }
    )
    return entry


def collect(home, found, served_by_magpie):
    root = os.path.join(home, "caches", "modules-2", "files-2.1")
    for group in os.listdir(root):
        for module in os.listdir(os.path.join(root, group)):
            for version in os.listdir(os.path.join(root, group, module)):
                if (group, module, version) in served_by_magpie:
                    continue  # Gradle's copy of what Magpie served: it comes from the mirror
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
    homes = [sys.argv[1]] + sys.argv[3:]
    magpie = {}
    for home in homes:
        collect_magpie(home, magpie)
    served_by_magpie = {(group, module, version) for (_, group, module, version, _) in magpie}
    found = {}
    for home in homes:
        collect(home, found, served_by_magpie)
    print(f"{len(found)} Maven files, {len(magpie)} Magpie files", file=sys.stderr)
    with concurrent.futures.ThreadPoolExecutor(max_workers=32) as pool:
        results = list(pool.map(resolve, sorted(found.items())))
        results += list(pool.map(resolve_magpie, sorted(magpie.items())))
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
