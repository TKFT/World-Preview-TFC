package com.rustysnail.world.preview.tfc.backend.search.mountain;

public record MountainCandidate(int height, int x, int z) implements Comparable<MountainCandidate>
{
    @Override
    public int compareTo(MountainCandidate other)
    {
        return Integer.compare(other.height, this.height); // descending by height
    }
}
