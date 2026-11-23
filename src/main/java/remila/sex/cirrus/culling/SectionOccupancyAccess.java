package remila.sex.cirrus.culling;

/** Carries a compiled section's 4,096-bit opaque block occupancy mask and zero-alloc face-mask setter on VisibilitySet. */
public interface SectionOccupancyAccess {
    long[] cirrus$occupancy();
    void cirrus$occupancy(long[] mask);
    void cirrus$addFaceMask(int faceMask);
}
