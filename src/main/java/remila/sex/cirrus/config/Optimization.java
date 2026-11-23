package remila.sex.cirrus.config;

public enum Optimization {
    TEXTURE_PREPARATION("texture_preparation"),
    CHUNK_BATCHING("chunk_batching"),
    PALETTE_PACKING("palette_packing"),
    DENSITY_BUFFERS("density_buffers"),
    TERRAIN_LOD("terrain_lod"),
    FAR_TERRAIN("far_terrain"),
    BIOME_SAMPLING("biome_sampling"),
    ITEM_LOD("item_lod"),
    TEXT_CACHE("text_cache"),
    QUAD_ENCODING("quad_encoding"),
    SECTION_VISIBILITY("section_visibility"),
    SECTION_LIGHTING("section_lighting"),
    MODEL_BATCHING("model_batching"),
    FACE_CACHE("face_cache"),
    TERRAIN_CULLING("terrain_culling"),
    ENTITY_OCCLUSION("entity_occlusion"),
    PARTICLE_CULLING("particle_culling"),
    BLOCK_ENTITY_CULLING("block_entity_culling");

    private final String key;

    Optimization(String key) { this.key = key; }
    public String key() { return key; }
    public boolean supports(String version) {
        return switch (this) {
            case CHUNK_BATCHING, PALETTE_PACKING, DENSITY_BUFFERS, FAR_TERRAIN -> version.equals("26.3");
            default -> supports(version.equals("1.21.1"));
        };
    }
    public boolean supports(boolean legacy) {
        return switch (this) {
            case CHUNK_BATCHING, PALETTE_PACKING, DENSITY_BUFFERS, FAR_TERRAIN -> false;
            case PARTICLE_CULLING, BLOCK_ENTITY_CULLING -> legacy;
            default -> true;
        };
    }
}
