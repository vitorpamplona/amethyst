#!/bin/bash
# Find NIP implementation files by NIP number or search term

set -e

QUARTZ_PATH="${QUARTZ_PATH:-./quartz/src/commonMain/kotlin/com/vitorpamplona/quartz}"

if [ $# -eq 0 ]; then
    echo "Usage: $0 <nip-number|search-term>"
    echo ""
    echo "Examples:"
    echo "  $0 01              # Find NIP-01 files"
    echo "  $0 44              # Find NIP-44 files"
    echo "  $0 B7              # Hex-id NIPs (5A, 7D, B7, ...)"
    echo "  $0 encryption      # Search for 'encryption' in NIP packages"
    echo ""
    echo "Set QUARTZ_PATH to override default quartz location"
    exit 1
fi

SEARCH_TERM="$1"

# A NIP id: decimal (44, 1 -> 01) or a two-char hex id (5A, B7, 7D)
if [[ "$SEARCH_TERM" =~ ^[0-9]+$ || "$SEARCH_TERM" =~ ^[0-9A-Fa-f]{2}$ ]]; then
    if [[ "$SEARCH_TERM" =~ ^[0-9]+$ ]]; then
        NIP_NUM=$(printf "%02d" "$((10#$SEARCH_TERM))")
    else
        NIP_NUM="$SEARCH_TERM"
    fi
    echo "Searching for NIP-$NIP_NUM implementation..."
    echo "================================================"
    echo ""

    # Find directories matching nip##*
    find "$QUARTZ_PATH" -type d -iname "nip${NIP_NUM}*" | while read -r dir; do
        echo "📁 $(basename "$dir")/"
        find "$dir" -name "*.kt" -type f | while read -r file; do
            rel_path="${file#$QUARTZ_PATH/}"
            echo "   └─ $rel_path"
        done
        echo ""
    done
else
    # Text search
    echo "Searching for '$SEARCH_TERM' in NIP packages..."
    echo "================================================"
    echo ""

    find "$QUARTZ_PATH" -type d -name "nip*" | while read -r dir; do
        if grep -r -l -i "$SEARCH_TERM" "$dir" --include="*.kt" 2>/dev/null | head -1 > /dev/null; then
            echo "📁 $(basename "$dir")/"
            grep -r -l -i "$SEARCH_TERM" "$dir" --include="*.kt" 2>/dev/null | while read -r file; do
                rel_path="${file#$QUARTZ_PATH/}"
                matches=$(grep -c -i "$SEARCH_TERM" "$file" 2>/dev/null || echo "0")
                echo "   └─ $rel_path ($matches matches)"
            done
            echo ""
        fi
    done
fi

echo "Done."
