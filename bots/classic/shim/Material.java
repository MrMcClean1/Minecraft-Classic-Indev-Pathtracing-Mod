package net.minecraft.block.material;

/** Classic has no materials (only a liquid type); Compat.get_material picks one of these per block id. */
public class Material {
    public static final Material AIR = new Material(false, false), WOOD = new Material(true, false), STONE = new Material(true, false),
            IRON = new Material(true, false), WATER = new Material(false, true), LAVA = new Material(false, true), PLANT = new Material(false, false),
            SOLID = new Material(true, false);
    public static final Material SNOW = null, SNOW_LAYER = null; // no snow in Classic
    private final boolean solid, liquid;

    private Material(boolean solid, boolean liquid) { this.solid = solid; this.liquid = liquid; }

    public boolean blocksMovement() { return solid; }
    public boolean isLiquid() { return liquid; }
}
