package org.sona.metadata.resolver;

/**
 * The part of a release an album's files are: all of it, or one of its media, as when one disc of several was
 * uploaded.
 */
public sealed interface ReleaseScope {

    record WholeRelease() implements ReleaseScope {}

    /**
     * @param position of the medium on the release, from 1
     */
    record OneMedium(int position) implements ReleaseScope {}
}
