# Known gaps

Data Sona doesn't store or serve yet, and what the API does in the meantime. Fields with no stored data are left null rather
than guessed from related rows. The last section lists what Sona's decoders need from the bitstream library. When you
close a gap, remove it from here.

## Library

| Gap | Until it's stored |
|---|---|
| **Play history.** | Nothing records plays, so there is no recently played or not-played-yet list. |
| **Every credited artist.** Only the first artist of a recording's, release's or release group's credit is kept, so "feat." and "&" credits are lost. | Each track, release and release group has one artist. |

## Pages

Data a page will show that its endpoint doesn't serve yet.

| Gap | Until it's served |
|---|---|
| **Playing music.** Sona doesn't stream yet, and playlists are still mocked in the frontend. | The Play, Shuffle and More buttons on the release and release group pages, the artist page's Shuffle button, and the play button on album cards, do nothing, and the library's tracks can't be added to playlists. |

## Bitstream library

Sona's hand-written decoders read bits through `dev.javax:bitstream` (0.1.0-RC), which reads 1 to 63 bits at a time,
most or least significant bit first, and aligns to the next byte. The operations below would serve the decoders Sona
has and the ones VISION plans (every common format). The FLAC decoder works around their absence in `FrameReader` and
`FlacDecoder`; decoding two minutes of CD audio takes 293 ms (`FlacDecoderBenchmark`), about 30% of it in reading bits.

| Operation | Formats that need it | Until the library has it |
|---|---|---|
| **Refill a whole word at a time.** `refill` takes only the 1 or 2 bytes the current read is short of, so the byte source is called every few bits. | Every format. | `FrameReader` buffers the stream itself and hands bytes over one at a time. It can't take more than the library asks for, since its CRCs count exactly the bytes taken, so a word refill needs the CRCs to move to the byte positions below. |
| **Read from a byte array.** `wrap(byte[], offset, length)`, reading eight bytes at once, with no `ByteSource` call per refill. | MP3's bit reservoir, where a granule's main data starts up to 511 bytes back in earlier frames and must be stitched together; Ogg packets (Vorbis, Opus) and MP4 samples (AAC, ALAC), which are read whole before decoding. | Nothing yet: only FLAC is decoded, from a stream. |
| **Unary codes.** Count the 0 bits before a 1 (`readUnaryZeros`), or the 1 bits before a 0 (`readUnaryOnes`), with `Long.numberOfLeadingZeros` on the buffered word rather than a read per bit. | FLAC's Rice codes (zeros); ALAC's capped Rice prefix, AAC's spectral escape codes and WavPack's residuals (ones). | `FrameReader.readUnary` calls `readBits(1)` per bit, 14% of decoding time before the buffering fix. |
| **Signed reads.** `readSigned(n)`, sign-extending an n-bit two's-complement field. | FLAC warm-up samples, LPC coefficients and shift; ALAC's predictor coefficients. | `FrameReader.readSigned` shifts the field to the top of a `long` and back. |
| **Reading 0 bits.** `readBits(0)` returns 0 rather than throwing. | FLAC's Rice parameter 0 and escaped partitions of 0 bits; MP3 scale factors whose length `scalefac_compress` sets to 0; Vorbis fields whose width is `ilog` of 0. | `FlacDecoder.readResidual` checks for 0 before each read. |
| **Peek and skip.** `peekBits(n)` without consuming them, then `skipBits(length)`, padding with 0 bits past the end. Table-driven Huffman decoding looks up the next n bits, then consumes only the code's length. | MP3's big-values and count1 tables, AAC's spectral and scale factor codebooks, Vorbis codebooks, ALAC's adaptive Rice prefix. | Nothing yet: FLAC has no Huffman codes. |
| **Bit position.** How many bits have been read since a mark, and how many are left in a byte array. | MP3's `part2_3_length`, which says in bits where a granule's Huffman data ends; Vorbis and Opus, where reading past the end of a packet is an end-of-packet condition rather than an error. | Nothing yet. |
| **Single flags.** `readBit()` returning a `boolean`. | Every format's header and side-info flags (FLAC's wasted-bits flag, MP3's `window_switching_flag`, AAC's `common_window`, Vorbis's `codebook_ordered`). | `readBits(1) != 0`. |
| **Unreflected CRCs** over a range of bytes read. The JDK's `CRC32` and Commons Codec's `Crc16` only compute reflected ones. | FLAC's CRC-8 and CRC-16; the CRC-16 (with 0xFFFF as its initial value) of MP3 frames with a protection bit and of AAC ADTS headers; Ogg's page CRC-32. | `FrameReader` keeps its own CRC-8 and CRC-16 tables. |
| **Sync search.** Skip to the next byte-aligned bit pattern. | Resyncing after a corrupt frame, or seeking: MP3's 11-bit frame sync, ADTS's 12-bit sync, FLAC's 14-bit frame sync, Ogg's `OggS` capture pattern. | Nothing yet: the FLAC decoder reads frames back to back from the start and refuses a corrupt one. |

Opus mostly reads through a range decoder, which also reads its raw bits backwards from the end of each frame. That's
an entropy coder of its own rather than bit reads, so it isn't listed here.
