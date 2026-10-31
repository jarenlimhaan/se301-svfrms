#!/usr/bin/env bash
# Behaviour-parity check (README §2.3, §3.3): runs the legacy program and run.jar N times each,
# masks every number as N, and diffs the sets of distinct stdout lines. Also counts legacy crashes.
#
# Usage:  tools/compare_legacy.sh [runs=20] [jar=run.jar]
set -euo pipefail
runs=${1:-20}
jar=$(cd "$(dirname "${2:-run.jar}")" && pwd)/$(basename "${2:-run.jar}")
root=$(cd "$(dirname "$0")/.." && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

javac -d "$work/legacy-bin" "$root/legacy/SVFRMS.java"
mask() { sed -E 's/[0-9]+(\.[0-9]+)?/N/g; s/saved to: .*/saved to: PATH/'; }

for i in $(seq 1 "$runs"); do
    mkdir -p "$work/legacy/$i" "$work/new/$i"
    (cd "$work/legacy/$i" && java -cp "$work/legacy-bin" org.example.SVFRMS 2> err.txt | mask > out.txt) || true
    (cd "$work/new/$i" && java -jar "$jar" 2> err.txt | mask > out.txt)
done

cat "$work"/legacy/*/out.txt | sort -u > "$work/legacy.txt"
cat "$work"/new/*/out.txt | sort -u > "$work/new.txt"
legacy_crashes=$(grep -l "Exception" "$work"/legacy/*/err.txt 2>/dev/null | wc -l)
new_errors=$(find "$work"/new -name err.txt -size +0 | wc -l)
echo "runs: $runs each"
echo "legacy runs with an exception on stderr: $legacy_crashes"
echo "refactored runs with any stderr output: $new_errors"
if diff "$work/legacy.txt" "$work/new.txt"; then
    echo "PARITY OK: $(wc -l < "$work/new.txt") distinct output lines, identical"
else
    echo "PARITY DIFFERENCE (< legacy, > refactored)"
    exit 1
fi
