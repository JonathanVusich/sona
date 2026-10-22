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
most or least significant bit first, and aligns to the next byte. It takes from its byte source only the bytes a read
needs, by design, so a decoder knows exactly which bytes have been read (`FrameReader` keeps FLAC's CRCs over them) and
buffers its source itself. Its buffered bits are private, so a decoder can't make up for the reads below on its own:
each costs a decoder speed, or in the case of Huffman codes rules out the usual way of decoding them. Decoding two
minutes of CD audio as FLAC takes 293 ms (`FlacDecoderBenchmark`).

| Operation | Formats that need it | Until the library has it |
|---|---|---|
| **Unary codes.** Count the 0 bits before a 1, or the 1 bits before a 0, from the buffered word with `Long.numberOfLeadingZeros` (or trailing, reading least significant bit first) rather than a read per bit. | FLAC's Rice codes (zeros); ALAC's capped Rice prefix, AAC's spectral escape codes and WavPack's residuals (ones). | `FrameReader.readUnary` calls `readBits(1)` per bit, which JFR put at 14% of decoding time when it took 379 ms. |
| **Peek and skip.** Look at the next n bits without consuming them, then consume only as many as the code they start turned out to be, padding with 0 bits past the end of the data. Huffman decoders look codes up in a table this way. | MP3's big-values and count1 tables, AAC's spectral and scale factor codebooks, Vorbis codebooks. | Nothing yet: FLAC has no Huffman codes. Without it, a decoder walks the Huffman tree a bit at a time. |
