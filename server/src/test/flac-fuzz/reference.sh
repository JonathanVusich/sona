#!/bin/sh
# For each /w/<dir>/*.flac, writes .rc (flac -t exit code), .raw (bit-exact decode if it succeeds), .fp (fpcalc).
d=$1
for f in /w/$d/*.flac; do
  b=${f%.flac}
  flac -s -t "$f" 2>/dev/null; echo $? > $b.rc
  flac -s -d -c --force-raw-format --endian=little --sign=signed "$f" > $b.raw 2>/dev/null
  fpcalc -plain "$f" 2>/dev/null > $b.fp
done
