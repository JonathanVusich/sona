#!/bin/sh
# Builds the clean corpus in /w/corpus: FLACs from flac(1) with a reference decode (.raw) and fpcalc (.fp) each.
set -u
mkdir -p /w/corpus /w/tmp
mk() { # name rate channels seconds seed pcm_fmt flacargs...
  name=$1; rate=$2; ch=$3; dur=$4; seed=$5; fmt=$6; shift 6
  expr="0.25*sin(2*PI*(220+55*mod(floor(t*2.5+$seed),7))*t)+0.2*sin(2*PI*(330+40*mod(floor(t*2.5+$seed),5))*t)+0.15*sin(2*PI*(110*(1+mod(floor(t*1.25),4)))*t)"
  case $ch in 1) lay=mono;; 2) lay=stereo;; 3) lay=3.0;; 6) lay=5.1;; 8) lay=7.1;; esac
  gains=$(i=0; while [ $i -lt $ch ]; do [ $i -gt 0 ] && printf '|'; printf 'c%d=0.%d*c0' $i $((9-i)); i=$((i+1)); done)
  ffmpeg -v error -y -f lavfi -i "aevalsrc='$expr':s=$rate:d=$dur" -f lavfi -i "anoisesrc=c=pink:r=$rate:a=0.02:d=$dur:seed=$seed"     -filter_complex "[0][1]amix=inputs=2:normalize=0,aformat=channel_layouts=mono[m];[m]pan=$lay|$gains[o]"     -map '[o]' -c:a pcm_$fmt /w/tmp/$name.wav || { echo "ffmpeg failed $name"; return; }
  flac -s -f "$@" -o /w/corpus/$name.flac /w/tmp/$name.wav || echo "flac failed $name"
  rm -f /w/tmp/$name.wav
}
# rate ch dur seed fmt flacargs
mk r44100_c2_16_l5  44100 2 25 1 s16le -5
mk r44100_c2_16_l0  44100 2 12 2 s16le -0
mk r44100_c2_16_l8  44100 2 12 3 s16le -8 -e -p
mk r44100_c1_16     44100 1 12 4 s16le -5
mk r48000_c2_24     48000 2 15 5 s24le -5
mk r44100_c2_24     44100 2 15 6 s24le -8
mk r96000_c2_24     96000 2 12 7 s24le -5
mk r192000_c2_24    192000 2 8 8 s24le -5
mk r88200_c2_24     88200 2 10 9 s24le -5
mk r8000_c1_16      8000 1 12 10 s16le -5
mk r22050_c2_16     22050 2 12 11 s16le -5
mk r11025_c1_16     11025 1 12 12 s16le -5
mk r32000_c2_16     32000 2 12 13 s16le -5
mk r16000_c1_16     16000 1 12 14 s16le -5
mk r44100_c2_16_b16 44100 2 10 15 s16le -5 -b 16
mk r44100_c2_16_b4608 44100 2 10 16 s16le -5 -b 4608
mk r44100_c2_16_b32  44100 2 3 17 s16le -5 -b 32
mk r44100_c2_16_b65535 44100 2 10 18 s16le -5 -b 65535
mk r44100_c2_u8     44100 2 10 19 u8 -5
mk r44100_c2_32     44100 2 10 20 s32le -5
mk r44100_c3_16     44100 3 10 21 s16le -5
mk r48000_c6_16     48000 6 10 22 s16le -5
mk r48000_c6_24     48000 6 10 23 s24le -5
mk r48000_c8_16     48000 8 8 24 s16le -5
mk r44100_c2_16_short 44100 2 0.5 25 s16le -5
mk r44100_c2_16_1s   44100 2 1 26 s16le -5
mk r44100_c2_16_tiny 44100 2 0.01 27 s16le -5
mk r44100_c2_16_long 44100 2 135 28 s16le -5
mk r44100_c2_16_exact120 44100 2 120 29 s16le -5
mk r44100_c2_16_nomidside 44100 2 10 30 s16le -5 -M
mk r44100_c2_16_loose 44100 2 10 31 s16le -5 --loose-mid-side -l 12 -r 0
mk r44100_c2_16_apod 44100 2 10 32 s16le -8 -A "tukey(0.5);partial_tukey(2);punchout_tukey(3)"
mk r44100_c2_16_verb 44100 2 10 33 s16le -5 --lax -l 32 -q 15
ls /w/corpus | wc -l
# Blocks real files carry: a picture, a cue sheet, foreign chunks as APPLICATION blocks, many long tags.
# (the section below adds the metadata-rich files)
ffmpeg -v error -y -f lavfi -i testsrc=s=96x96:d=1 -frames:v 1 /w/tmp/cover.png
cat > /w/tmp/x.cue <<'CUE'
FILE "x.wav" WAVE
  TRACK 01 AUDIO
    ISRC USABC1234567
    INDEX 01 00:00:00
  TRACK 02 AUDIO
    INDEX 00 00:04:50
    INDEX 01 00:05:00
CUE
mk meta_picture 44100 2 12 40 s16le -5 "--picture=3|image/png|cover||/w/tmp/cover.png"
mk meta_cuesheet 44100 2 12 41 s16le -5 --cuesheet=/w/tmp/x.cue
mk meta_foreign 44100 2 12 42 s16le -5 --keep-foreign-metadata
tags=""; i=0; while [ $i -lt 60 ]; do tags="$tags -T ARTIST=Artist$i -T COMMENT=$(printf 'long_comment_%.0s' 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20)$i"; i=$((i+1)); done
mk meta_many_tags 44100 2 12 43 s16le -5 $tags
mk meta_everything 44100 2 12 44 s16le -8 "--picture=3|image/png|cover||/w/tmp/cover.png" --cuesheet=/w/tmp/x.cue --keep-foreign-metadata -T TITLE=Everything -T MUSICBRAINZ_TRACKID=00000000-0000-0000-0000-000000000000
ls /w/corpus | wc -l
