package org.sona.format.mp3;

import dev.javax.bitstream.BitInputStream;

/**
 * A Layer III frame as {@link Mp3Decoder} reads it. Its granules' bits are read through the main data in order, and
 * count how many they take: each granule's channel takes the part2_3_length its side information gives.
 *
 * @param mainData the frame's main data, from where its granules start, which must be read before the next frame is
 * @param mainDataLength how many bytes of main data the frame's granules have
 */
record Mp3Frame(FrameHeader header, SideInfo sideInfo, BitInputStream mainData, int mainDataLength) {}
