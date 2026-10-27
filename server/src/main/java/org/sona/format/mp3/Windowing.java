package org.sona.format.mp3;

import java.util.List;

/**
 * How a granule's channel is windowed, and the Huffman tables its big values are coded with, which the
 * window_switching_flag picks between (ISO/IEC 11172-3 section 2.4.1.7), as {@link Mp3Decoder} reads them.
 */
sealed interface Windowing permits Windowing.Normal, Windowing.Switched {

    /**
     * How many windows a short block has.
     */
    int WINDOWS = 3;

    BlockType blockType();

    /**
     * @return which blocks the scale factors cover
     */
    BlockLayout layout();

    /**
     * @return whether the two lowest subbands are transformed with the normal window, whatever the block type
     */
    boolean mixedBlock();

    /**
     * One long block, whose big values are split into three regions, each with its own table.
     *
     * @param region0Count one less than how many scale factor bands the first region covers
     * @param region1Count one less than how many the second covers, the third taking the rest
     */
    record Normal(int table0, int table1, int table2, int region0Count, int region1Count) implements Windowing {

        @Override
        public BlockType blockType() {
            return BlockType.NORMAL;
        }

        @Override
        public BlockLayout layout() {
            return BlockLayout.LONG;
        }

        @Override
        public boolean mixedBlock() {
            return false;
        }
    }

    /**
     * A start, stop or short block, whose big values are split into two regions: the first is implicit, and the second
     * takes the rest.
     *
     * @param mixedBlock the mixed_block_flag, which puts the two lowest subbands on the normal window for every block
     *     type, and only gives a short block long scale factor bands below them
     * @param subblockGains each short window's gain, in steps of 8 of the global gain
     */
    record Switched(BlockType blockType, boolean mixedBlock, int table0, int table1, List<Integer> subblockGains)
            implements Windowing {

        public Switched {
            subblockGains = List.copyOf(subblockGains);
        }

        @Override
        public BlockLayout layout() {
            if (blockType != BlockType.SHORT) {
                return BlockLayout.LONG;
            }
            if (mixedBlock) {
                return BlockLayout.MIXED;
            }
            return BlockLayout.SHORT;
        }
    }
}
