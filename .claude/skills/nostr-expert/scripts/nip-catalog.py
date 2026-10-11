# Regenerates references/nip-catalog.md from the quartz source tree.
import os, re, sys
HEADER = """# Quartz package catalog

Every top-level package under `quartz/src/commonMain/kotlin/com/vitorpamplona/quartz/`
and the event classes it defines, keyed by their `const val KIND`. Generated from the
tree by `scripts/nip-catalog.py`; regenerate it after adding a package or a kind
instead of editing by hand:

```bash
python3 .claude/skills/nostr-expert/scripts/nip-catalog.py \\
  quartz/src/commonMain/kotlin/com/vitorpamplona/quartz \\
  > .claude/skills/nostr-expert/references/nip-catalog.md
```

"No event kinds" means the package holds tags, crypto, codecs or client helpers only.
The same kind under two classes is real (tag-dispatched kinds such as 30301/38000) —
grep the class. `ls` of the package root stays the ground truth, and
`scripts/nip-lookup.sh <NN>` lists one package's files.
"""
root = sys.argv[1]
kre = re.compile(r'const val KIND\s*=\s*([0-9_]+)')
def scan(d):
    out = []
    for dp, dn, fn in os.walk(d):
        for f in sorted(fn):
            if not f.endswith('.kt'): continue
            t = open(os.path.join(dp, f), encoding='utf-8').read()
            for m in kre.finditer(t):
                out.append((int(m.group(1).replace('_','')), f[:-3]))
    return sorted(set(out))
def row(name, path):
    ev = scan(path)
    cells = ', '.join(f'{c} {k}' for k, c in ev) if ev else '(no event kinds: tags/utilities)'
    return f'| `{name}/` | {cells} |'
dirs = sorted(d for d in os.listdir(root) if os.path.isdir(os.path.join(root, d)))
print(HEADER)
print('## Standard NIP packages (`nipXX*`)\n')
print('| Package | Event classes (kind) |\n|---|---|')
for d in dirs:
    if d.startswith('nip'): print(row(d, os.path.join(root, d)))
print('\n## Non-NIP protocol families\n')
print('| Package | Event classes (kind) |\n|---|---|')
for d in dirs:
    if not d.startswith('nip') and d not in ('experimental','utils'):
        print(row(d, os.path.join(root, d)))
print('\n## `experimental/` (draft / unmerged NIPs)\n')
print('| Package | Event classes (kind) |\n|---|---|')
ex = os.path.join(root, 'experimental')
for d in sorted(os.listdir(ex)):
    if os.path.isdir(os.path.join(ex, d)): print(row('experimental/'+d, os.path.join(ex, d)))
