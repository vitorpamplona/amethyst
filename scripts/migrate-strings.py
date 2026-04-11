#!/usr/bin/env python3
"""
Migrate Android string resources to Compose Multiplatform Resources.

Copies strings.xml from amethyst/src/main/res/values/ to
commons/src/commonMain/composeResources/values/, keeping the same
Android-format folder names (values-es-rES) since Compose MP Resources
uses the same qualifier format with lowercase 'r' prefix for regions.

Strips Android-specific attributes (tools:ignore, translatable="false")
and the tools namespace declaration.
"""

import os
import re
import shutil
from pathlib import Path

REPO_ROOT = Path(__file__).parent.parent
ANDROID_RES = REPO_ROOT / "amethyst" / "src" / "main" / "res"
COMPOSE_RES = REPO_ROOT / "commons" / "src" / "commonMain" / "composeResources"


def clean_strings_xml(content: str) -> str:
    """Strip Android-specific attributes from strings.xml content."""
    # Remove xmlns:tools declaration
    content = re.sub(r'\s+xmlns:tools="[^"]*"', '', content)
    # Remove tools:ignore attributes
    content = re.sub(r'\s+tools:ignore="[^"]*"', '', content)
    # Remove translatable="false" attributes (Compose MP doesn't use this)
    content = re.sub(r'\s+translatable="false"', '', content)
    return content


def merge_strings_xml(existing_content: str, android_content: str) -> str:
    """Merge existing commons strings with Android strings.
    
    Keeps existing commons entries at the top, adds Android entries below.
    Deduplicates by string name.
    """
    # Extract string names from existing
    existing_names = set(re.findall(r'name="([^"]+)"', existing_content))
    
    # Extract individual <string> entries from Android content
    android_entries = []
    in_string = False
    current_entry = []
    current_name = None
    
    for line in android_content.split('\n'):
        if not in_string:
            match = re.search(r'<string\s+name="([^"]+)"', line)
            if match:
                current_name = match.group(1)
                if current_name in existing_names:
                    if '</string>' not in line:
                        in_string = True
                    continue
                if '</string>' in line:
                    android_entries.append(line)
                else:
                    in_string = True
                    current_entry = [line]
        else:
            current_entry.append(line)
            if '</string>' in line:
                in_string = False
                if current_name not in existing_names:
                    android_entries.append('\n'.join(current_entry))
                current_entry = []
                current_name = None
    
    if android_entries:
        android_block = '\n    <!-- Migrated from Android resources -->\n    ' + '\n    '.join(android_entries)
        merged = existing_content.replace('</resources>', android_block + '\n</resources>')
        return merged
    
    return existing_content


def main():
    # Step 1: Process default values/strings.xml
    android_default = ANDROID_RES / "values" / "strings.xml"
    compose_default = COMPOSE_RES / "values" / "strings.xml"
    
    print(f"Reading Android strings: {android_default}")
    android_content = android_default.read_text(encoding='utf-8')
    android_clean = clean_strings_xml(android_content)
    
    print(f"Reading existing commons strings: {compose_default}")
    existing_content = compose_default.read_text(encoding='utf-8')
    
    merged = merge_strings_xml(existing_content, android_clean)
    compose_default.write_text(merged, encoding='utf-8')
    print(f"Wrote merged strings: {compose_default}")
    
    # Step 2: Copy translation files (keep same folder names — Compose MP
    # uses the same qualifier format as Android: values-es-rMX)
    copied = 0
    skipped = 0
    for android_dir in sorted(ANDROID_RES.iterdir()):
        if not android_dir.is_dir() or not android_dir.name.startswith('values-'):
            continue
        
        # Skip non-locale dirs
        if android_dir.name == 'values-night':
            skipped += 1
            continue
        
        android_strings = android_dir / "strings.xml"
        if not android_strings.exists():
            skipped += 1
            continue
        
        compose_dir = COMPOSE_RES / android_dir.name
        compose_dir.mkdir(parents=True, exist_ok=True)
        
        content = android_strings.read_text(encoding='utf-8')
        clean = clean_strings_xml(content)
        
        (compose_dir / "strings.xml").write_text(clean, encoding='utf-8')
        copied += 1
        print(f"  {android_dir.name}")
    
    print(f"\nDone: {copied} translations copied, {skipped} skipped")
    
    # Count entries
    final_content = compose_default.read_text(encoding='utf-8')
    entry_count = len(re.findall(r'<string\s+name=', final_content))
    print(f"Total string entries in commons: {entry_count}")


if __name__ == '__main__':
    main()
