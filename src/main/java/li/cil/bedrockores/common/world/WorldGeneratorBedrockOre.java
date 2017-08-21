package li.cil.bedrockores.common.world;

import com.google.common.base.Predicate;
import gnu.trove.list.TFloatList;
import gnu.trove.list.array.TFloatArrayList;
import li.cil.bedrockores.common.config.OreConfig;
import li.cil.bedrockores.common.config.Settings;
import li.cil.bedrockores.common.config.VeinConfig;
import li.cil.bedrockores.common.init.Blocks;
import li.cil.bedrockores.common.tileentity.TileEntityBedrockOre;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.fml.common.IWorldGenerator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public enum WorldGeneratorBedrockOre implements IWorldGenerator {
    INSTANCE;

    private final List<BlockPos> candidates = new ArrayList<>();
    private final TFloatList distribution = new TFloatArrayList();

    @Override
    public void generate(final Random random, final int chunkX, final int chunkZ, final World world, final IChunkGenerator chunkGenerator, final IChunkProvider chunkProvider) {
        if (random.nextFloat() >= Settings.veinChance) {
            return;
        }

        final OreConfig ore = VeinConfig.getOre(world.provider.getDimensionType(), random.nextFloat());
        if (ore == null) {
            return;
        }

        final int veinMinWidth = Math.max(1, ore.widthMin);
        final int veinMaxWidth = Math.max(veinMinWidth, ore.widthMax);
        final int veinMinHeight = Math.max(1, ore.heightMin);
        final int veinMaxHeight = Math.max(veinMinHeight, ore.heightMax);
        final int veinMinCount = Math.max(1, ore.countMin);
        final int veinMaxCount = Math.max(veinMinCount, ore.countMax);
        final int veinMinYield = Math.max(1, ore.yieldMin);
        final int veinMaxYield = Math.max(veinMinYield, ore.yieldMax);

        final int veinCount = veinMinCount == veinMaxCount ? veinMinCount : (veinMinCount + random.nextInt(veinMaxCount - veinMinCount));
        final int veinYield = Math.max(0, Math.round((veinMinYield == veinMaxYield ? veinMinYield : (veinMinYield + random.nextInt(veinMaxYield - veinMinYield))) * Settings.veinYieldBaseScale));
        if (veinYield == 0) {
            return;
        }

        // We generate veins in ellipsoid shapes in the bedrock. Pick a width
        // and height for the ellipse, as well as a center.
        final int a = MathHelper.ceil((veinMinWidth == veinMaxWidth ? veinMinWidth : (veinMinWidth + random.nextInt(veinMaxWidth - veinMinWidth))) / 2f);
        final int b = MathHelper.ceil((veinMinWidth == veinMaxWidth ? veinMinWidth : (veinMinWidth + random.nextInt(veinMaxWidth - veinMinWidth))) / 2f);
        final int maxWidth = Math.max(a, b);
        final int h = veinMinHeight + random.nextInt(veinMaxHeight - veinMinHeight);
        final float rotation = random.nextFloat() * (float) Math.PI;
        final int centerX = chunkX * 16 + 8 - maxWidth + random.nextInt(maxWidth * 2);
        final int centerZ = chunkZ * 16 + 8 - maxWidth + random.nextInt(maxWidth * 2);

        final BlockPos spawnPoint = world.getSpawnPoint();
        final double distanceToSpawn = new Vec3i(spawnPoint.getX(), 0, spawnPoint.getZ()).getDistance(centerX, 0, centerZ);
        final float veinScale;
        if (distanceToSpawn > Settings.veinDistanceScaleStart) {
            veinScale = Math.max(1, (float) Math.log((distanceToSpawn - Settings.veinDistanceScaleStart) / 10) * Settings.veinDistanceScaleMultiplier);
        } else {
            veinScale = 1;
        }

        final int adjustedCount = Math.round(veinCount * veinScale);
        final int adjustedYield = Math.round(veinYield * veinScale);

        final int minX = centerX - maxWidth;
        final int maxX = centerX + maxWidth;
        final int minZ = centerZ - maxWidth;
        final int maxZ = centerZ + maxWidth;

        assert candidates.isEmpty();
        assert distribution.isEmpty();

        try {
            // Only replace bedrock blocks.
            final Predicate<IBlockState> predicate = input -> input != null && input.getBlock() == net.minecraft.init.Blocks.BEDROCK;

            // Pick all candidate positions in the target bounds, those positions
            // being the ones that fall inside our ellipsoid.
            int maxY = 0;
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    if (!isPointInEllipse(x, z, centerX, centerZ,- a, b, rotation)) {
                        continue;
                    }
                    for (int y = Settings.veinBaseY; y >= 0; y--) {
                        final BlockPos pos = new BlockPos(x, y, z);
                        final IBlockState state = world.getBlockState(pos);
                        if (state.getBlock().isReplaceableOreGen(state, world, pos, predicate)) {
                            if (y > maxY) {
                                maxY = y;
                            }
                            candidates.add(pos);
                        }
                    }
                }
            }

            // We start at the typical max y-level for bedrock, but in case we
            // don't find anything at the higher levels make sure we still try
            // to use the full height.
            final int minY = maxY - h;
            candidates.removeIf(pos -> pos.getY() <= minY);

            // Inside the ellipsoid we pick a number of actually used blocks
            // in a uniform random fashion.
            if (candidates.size() > adjustedCount) {
                Collections.shuffle(candidates, random);
            }

            final int placeCount = Math.min(adjustedCount, candidates.size());

            // Each generated block gets a bit of randomness to its actual
            // amount to make things less boring.
            float sum = 0;
            for (int i = 0; i < placeCount; i++) {
                final float weight = random.nextFloat();
                sum += weight;
                distribution.add(weight);
            }

            // Half of the total yield is evenly distributed across blocks, the
            // rest falls into this random distribution. Adjust the normalizer
            // accordingly.
            final float fixedYield = adjustedYield / 2f;
            final int baseYield = MathHelper.ceil(fixedYield / placeCount);
            final float normalizer = (adjustedYield - fixedYield) / sum;
            int remaining = adjustedYield;
            for (int i = 0; i < placeCount && remaining > 0; i++) {
                final int amount = Math.min(remaining, baseYield + MathHelper.ceil(distribution.get(i) * normalizer));
                if (amount == 0) {
                    continue;
                }

                remaining -= amount;

                final BlockPos pos = candidates.get(i);
                world.setBlockState(pos, Blocks.bedrockOre.getDefaultState(), 2);

                final TileEntity tileEntity = world.getTileEntity(pos);
                if (tileEntity instanceof TileEntityBedrockOre) {
                    final TileEntityBedrockOre tileEntityBedrockOre = (TileEntityBedrockOre) tileEntity;
                    tileEntityBedrockOre.setOreBlockState(ore.state.getBlockState(), amount);
                }
            }

            assert remaining == 0;
        } finally {
            candidates.clear();
            distribution.clear();
        }
    }

    private static boolean isPointInEllipse(final int px, final int py, final int ex, final int ey, final float ea, final float eb, final float er) {
        final float cr = MathHelper.cos(er);
        final float sr = MathHelper.sin(er);

        final int dx = px - ex;
        final int dy = py - ey;

        final float leftTop = cr * dx + sr * dy;
        final float left = (leftTop * leftTop) / (ea * ea);

        final float rightTop = sr * dx - cr * dy;
        final float right = (rightTop * rightTop) / (eb * eb);

        return left + right <= 1;
    }
}
