package com.tfar.examplemod;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import com.mojang.blaze3d.vertex.MatrixApplyingVertexBuilder;
import com.mojang.blaze3d.vertex.VertexBuilderUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.settings.AbstractOption;
import net.minecraft.client.settings.CloudOption;
import net.minecraft.client.settings.ParticleStatus;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.crash.ReportedException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.*;
import net.minecraft.particles.IParticleData;
import net.minecraft.particles.ItemParticleData;
import net.minecraft.particles.ParticleTypes;
import net.minecraft.profiler.IProfiler;
import net.minecraft.resources.IResourceManager;
import net.minecraft.tags.FluidTags;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.util.math.shapes.ISelectionContext;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

public class WorldRendererEX extends WorldRenderer {

  private static final Logger LOGGER = LogManager.getLogger();
  private static final ResourceLocation MOON_LOCATION = new ResourceLocation("textures/environment/moon_phases.png");
  private static final ResourceLocation SUN_LOCATION = new ResourceLocation("textures/environment/sun.png");
  private static final ResourceLocation CLOUDS_LOCATION = new ResourceLocation("textures/environment/clouds.png");
  private static final ResourceLocation END_SKY_LOCATION = new ResourceLocation("textures/environment/end_sky.png");
  private static final ResourceLocation FORCEFIELD_LOCATION = new ResourceLocation("textures/misc/forcefield.png");
  private static final ResourceLocation RAIN_LOCATION = new ResourceLocation("textures/environment/rain.png");
  private static final ResourceLocation SNOW_LOCATION = new ResourceLocation("textures/environment/snow.png");
  public static final Direction[] DIRECTIONS = Direction.values();
  private final Minecraft minecraft;
  private final TextureManager textureManager;
  private final EntityRendererManager entityRenderDispatcher;
  public final RenderTypeBuffers renderBuffers;
  private ClientWorld level;
  private Set<ChunkRenderDispatcher.ChunkRender> chunksToCompile = Sets.newLinkedHashSet();
  private final ObjectList<LocalRenderInformationContainer> renderChunks = new ObjectArrayList<>(69696);
  private final Set<TileEntity> globalBlockEntities = Sets.newHashSet();
  private ViewFrustrum2 viewArea;
  private final VertexFormat skyFormat = DefaultVertexFormats.POSITION;
  @Nullable
  private VertexBuffer starBuffer;
  @Nullable
  private VertexBuffer skyBuffer;
  @Nullable
  private VertexBuffer darkBuffer;
  private boolean generateClouds = true;
  @Nullable
  private VertexBuffer cloudBuffer;
  private RenderTimeManager frameTimes = new RenderTimeManager(100);
  private int ticks;
  private final Int2ObjectMap<DestroyBlockProgress> destroyingBlocks = new Int2ObjectOpenHashMap<>();
  private final Long2ObjectMap<SortedSet<DestroyBlockProgress>> destructionProgress = new Long2ObjectOpenHashMap<>();
  private final Map<BlockPos, ISound> playingRecords = Maps.newHashMap();
  private Framebuffer entityTarget;
  private ShaderGroup entityEffect;
  private double lastCameraX = Double.MIN_VALUE;
  private double lastCameraY = Double.MIN_VALUE;
  private double lastCameraZ = Double.MIN_VALUE;
  private int lastCameraChunkX = Integer.MIN_VALUE;
  private int lastCameraChunkY = Integer.MIN_VALUE;
  private int lastCameraChunkZ = Integer.MIN_VALUE;
  private double prevCamX = Double.MIN_VALUE;
  private double prevCamY = Double.MIN_VALUE;
  private double prevCamZ = Double.MIN_VALUE;
  private double prevCamRotX = Double.MIN_VALUE;
  private double prevCamRotY = Double.MIN_VALUE;
  private int prevCloudX = Integer.MIN_VALUE;
  private int prevCloudY = Integer.MIN_VALUE;
  private int prevCloudZ = Integer.MIN_VALUE;
  private Vec3d prevCloudColor = Vec3d.ZERO;
  private CloudOption prevCloudsType;
  private ChunkRenderDispatcher chunkRenderDispatcher;
  private final VertexFormat format = DefaultVertexFormats.BLOCK;
  private int lastViewDistance = -1;
  private int renderedEntities;
  private int culledEntities;
  private boolean captureFrustum;
  @Nullable
  private ClippingHelperImpl capturedFrustum;
  private final Vector4f[] frustumPoints = new Vector4f[8];
  private final Vector3d frustumPos = new Vector3d(0.0D, 0.0D, 0.0D);
  private double xTransparentOld;
  private double yTransparentOld;
  private double zTransparentOld;
  private boolean needsUpdate = true;
  private int frameId;
  private int rainSoundTime;
  private final float[] rainSizeX = new float[1024];
  private final float[] rainSizeZ = new float[1024];

  public WorldRendererEX(Minecraft p_i225967_1_, RenderTypeBuffers p_i225967_2_) {
    super(p_i225967_1_,p_i225967_2_);
    this.minecraft = p_i225967_1_;
    this.entityRenderDispatcher = p_i225967_1_.getEntityRenderDispatcher();
    this.renderBuffers = p_i225967_2_;
    this.textureManager = p_i225967_1_.getTextureManager();

    for (int i = 0; i < 32; ++i) {
      for (int j = 0; j < 32; ++j) {
        float f = (float) (j - 16);
        float f1 = (float) (i - 16);
        float f2 = MathHelper.sqrt(f * f + f1 * f1);
        this.rainSizeX[i << 5 | j] = -f1 / f2;
        this.rainSizeZ[i << 5 | j] = f / f2;
      }
    }

    this.createStars();
    this.createLightSky();
    this.createDarkSky();
  }

  private void renderSnowAndRain(LightTexture p_228438_1_, float p_228438_2_, double p_228438_3_, double p_228438_5_, double p_228438_7_) {
    net.minecraftforge.client.IRenderHandler renderer = this.minecraft.level.getDimension().getWeatherRenderer();
    if (renderer != null) {
      renderer.render(this.ticks, p_228438_2_, this.minecraft.level, this.minecraft);
      return;
    }
    float f = this.minecraft.level.getRainLevel(p_228438_2_);
    if (!(f <= 0.0F)) {
      p_228438_1_.turnOnLightLayer();
      World world = this.minecraft.level;
      int i = MathHelper.floor(p_228438_3_);
      int j = MathHelper.floor(p_228438_5_);
      int k = MathHelper.floor(p_228438_7_);
      Tessellator tessellator = Tessellator.getInstance();
      BufferBuilder bufferbuilder = tessellator.getBuilder();
      RenderSystem.disableCull();
      RenderSystem.normal3f(0.0F, 1.0F, 0.0F);
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.defaultAlphaFunc();
      int l = 5;
      if (this.minecraft.options.fancyGraphics) {
        l = 10;
      }

      int i1 = -1;
      float f1 = (float) this.ticks + p_228438_2_;
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      BlockPos.Mutable blockpos$mutable = new BlockPos.Mutable();

      for (int j1 = k - l; j1 <= k + l; ++j1) {
        for (int k1 = i - l; k1 <= i + l; ++k1) {
          int l1 = (j1 - k + 16) * 32 + k1 - i + 16;
          double d0 = (double) this.rainSizeX[l1] * 0.5D;
          double d1 = (double) this.rainSizeZ[l1] * 0.5D;
          blockpos$mutable.set(k1, 0, j1);
          Biome biome = world.getBiome(blockpos$mutable);
          if (biome.getPrecipitation() != Biome.RainType.NONE) {
            int i2 = world.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING, blockpos$mutable).getY();
            int j2 = j - l;
            int k2 = j + l;
            if (j2 < i2) {
              j2 = i2;
            }

            if (k2 < i2) {
              k2 = i2;
            }

            int l2 = i2;
            if (i2 < j) {
              l2 = j;
            }

            if (j2 != k2) {
              Random random = new Random((long) (k1 * k1 * 3121 + k1 * 45238971 ^ j1 * j1 * 418711 + j1 * 13761));
              blockpos$mutable.set(k1, j2, j1);
              float f2 = biome.getTemperature(blockpos$mutable);
              if (f2 >= 0.15F) {
                if (i1 != 0) {
                  if (i1 >= 0) {
                    tessellator.end();
                  }

                  i1 = 0;
                  this.minecraft.getTextureManager().bind(RAIN_LOCATION);
                  bufferbuilder.begin(7, DefaultVertexFormats.PARTICLE);
                }

                int i3 = this.ticks + k1 * k1 * 3121 + k1 * 45238971 + j1 * j1 * 418711 + j1 * 13761 & 31;
                float f3 = -((float) i3 + p_228438_2_) / 32.0F * (3.0F + random.nextFloat());
                double d2 = (double) ((float) k1 + 0.5F) - p_228438_3_;
                double d4 = (double) ((float) j1 + 0.5F) - p_228438_7_;
                float f4 = MathHelper.sqrt(d2 * d2 + d4 * d4) / (float) l;
                float f5 = ((1.0F - f4 * f4) * 0.5F + 0.5F) * f;
                blockpos$mutable.set(k1, l2, j1);
                int j3 = getLightColor(world, blockpos$mutable);
                bufferbuilder.vertex((double) k1 - p_228438_3_ - d0 + 0.5D, (double) k2 - p_228438_5_, (double) j1 - p_228438_7_ - d1 + 0.5D).uv(0.0F, (float) j2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, f5).uv2(j3).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ + d0 + 0.5D, (double) k2 - p_228438_5_, (double) j1 - p_228438_7_ + d1 + 0.5D).uv(1.0F, (float) j2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, f5).uv2(j3).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ + d0 + 0.5D, (double) j2 - p_228438_5_, (double) j1 - p_228438_7_ + d1 + 0.5D).uv(1.0F, (float) k2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, f5).uv2(j3).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ - d0 + 0.5D, (double) j2 - p_228438_5_, (double) j1 - p_228438_7_ - d1 + 0.5D).uv(0.0F, (float) k2 * 0.25F + f3).color(1.0F, 1.0F, 1.0F, f5).uv2(j3).endVertex();
              } else {
                if (i1 != 1) {
                  if (i1 >= 0) {
                    tessellator.end();
                  }

                  i1 = 1;
                  this.minecraft.getTextureManager().bind(SNOW_LOCATION);
                  bufferbuilder.begin(7, DefaultVertexFormats.PARTICLE);
                }

                float f6 = -((float) (this.ticks & 511) + p_228438_2_) / 512.0F;
                float f7 = (float) (random.nextDouble() + (double) f1 * 0.01D * (double) ((float) random.nextGaussian()));
                float f8 = (float) (random.nextDouble() + (double) (f1 * (float) random.nextGaussian()) * 0.001D);
                double d3 = (double) ((float) k1 + 0.5F) - p_228438_3_;
                double d5 = (double) ((float) j1 + 0.5F) - p_228438_7_;
                float f9 = MathHelper.sqrt(d3 * d3 + d5 * d5) / (float) l;
                float f10 = ((1.0F - f9 * f9) * 0.3F + 0.5F) * f;
                blockpos$mutable.set(k1, l2, j1);
                int k3 = getLightColor(world, blockpos$mutable);
                int l3 = k3 >> 16 & '\uffff';
                int i4 = (k3 & '\uffff') * 3;
                int j4 = (l3 * 3 + 240) / 4;
                int k4 = (i4 * 3 + 240) / 4;
                bufferbuilder.vertex((double) k1 - p_228438_3_ - d0 + 0.5D, (double) k2 - p_228438_5_, (double) j1 - p_228438_7_ - d1 + 0.5D).uv(0.0F + f7, (float) j2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, f10).uv2(k4, j4).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ + d0 + 0.5D, (double) k2 - p_228438_5_, (double) j1 - p_228438_7_ + d1 + 0.5D).uv(1.0F + f7, (float) j2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, f10).uv2(k4, j4).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ + d0 + 0.5D, (double) j2 - p_228438_5_, (double) j1 - p_228438_7_ + d1 + 0.5D).uv(1.0F + f7, (float) k2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, f10).uv2(k4, j4).endVertex();
                bufferbuilder.vertex((double) k1 - p_228438_3_ - d0 + 0.5D, (double) j2 - p_228438_5_, (double) j1 - p_228438_7_ - d1 + 0.5D).uv(0.0F + f7, (float) k2 * 0.25F + f6 + f8).color(1.0F, 1.0F, 1.0F, f10).uv2(k4, j4).endVertex();
              }
            }
          }
        }
      }

      if (i1 >= 0) {
        tessellator.end();
      }

      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      RenderSystem.defaultAlphaFunc();
      p_228438_1_.turnOffLightLayer();
    }
  }

  public void tickRain(ActiveRenderInfo p_228436_1_) {
    float f = this.minecraft.level.getRainLevel(1.0F);
    if (!this.minecraft.options.fancyGraphics) {
      f /= 2.0F;
    }

    if (f != 0.0F) {
      Random random = new Random((long) this.ticks * 312987231L);
      IWorldReader iworldreader = this.minecraft.level;
      BlockPos blockpos = new BlockPos(p_228436_1_.getPosition());
      int i = 10;
      double d0 = 0.0D;
      double d1 = 0.0D;
      double d2 = 0.0D;
      int j = 0;
      int k = (int) (100.0F * f * f);
      if (this.minecraft.options.particles == ParticleStatus.DECREASED) {
        k >>= 1;
      } else if (this.minecraft.options.particles == ParticleStatus.MINIMAL) {
        k = 0;
      }

      for (int l = 0; l < k; ++l) {
        BlockPos blockpos1 = iworldreader.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING, blockpos.offset(random.nextInt(10) - random.nextInt(10), 0, random.nextInt(10) - random.nextInt(10)));
        Biome biome = iworldreader.getBiome(blockpos1);
        BlockPos blockpos2 = blockpos1.below();
        if (blockpos1.getY() <= blockpos.getY() + 10 && blockpos1.getY() >= blockpos.getY() - 10 && biome.getPrecipitation() == Biome.RainType.RAIN && biome.getTemperature(blockpos1) >= 0.15F) {
          double d3 = random.nextDouble();
          double d4 = random.nextDouble();
          BlockState blockstate = iworldreader.getBlockState(blockpos2);
          IFluidState ifluidstate = iworldreader.getFluidState(blockpos1);
          VoxelShape voxelshape = blockstate.getCollisionShape(iworldreader, blockpos2);
          double d7 = voxelshape.max(Direction.Axis.Y, d3, d4);
          double d8 = (double) ifluidstate.getHeight(iworldreader, blockpos1);
          double d5;
          double d6;
          if (d7 >= d8) {
            d5 = d7;
            d6 = voxelshape.min(Direction.Axis.Y, d3, d4);
          } else {
            d5 = 0.0D;
            d6 = 0.0D;
          }

          if (d5 > -Double.MAX_VALUE) {
            if (!ifluidstate.is(FluidTags.LAVA) && blockstate.getBlock() != Blocks.MAGMA_BLOCK && (blockstate.getBlock() != Blocks.CAMPFIRE || !blockstate.getValue(CampfireBlock.LIT))) {
              ++j;
              if (random.nextInt(j) == 0) {
                d0 = (double) blockpos2.getX() + d3;
                d1 = (double) ((float) blockpos2.getY() + 0.1F) + d5 - 1.0D;
                d2 = (double) blockpos2.getZ() + d4;
              }

              this.minecraft.level.addParticle(ParticleTypes.RAIN, (double) blockpos2.getX() + d3, (double) ((float) blockpos2.getY() + 0.1F) + d5, (double) blockpos2.getZ() + d4, 0.0D, 0.0D, 0.0D);
            } else {
              this.minecraft.level.addParticle(ParticleTypes.SMOKE, (double) blockpos1.getX() + d3, (double) ((float) blockpos1.getY() + 0.1F) - d6, (double) blockpos1.getZ() + d4, 0.0D, 0.0D, 0.0D);
            }
          }
        }
      }

      if (j > 0 && random.nextInt(3) < this.rainSoundTime++) {
        this.rainSoundTime = 0;
        if (d1 > (double) (blockpos.getY() + 1) && iworldreader.getHeightmapPos(Heightmap.Type.MOTION_BLOCKING, blockpos).getY() > MathHelper.floor((float) blockpos.getY())) {
          this.minecraft.level.playLocalSound(d0, d1, d2, SoundEvents.WEATHER_RAIN_ABOVE, SoundCategory.WEATHER, 0.1F, 0.5F, false);
        } else {
          this.minecraft.level.playLocalSound(d0, d1, d2, SoundEvents.WEATHER_RAIN, SoundCategory.WEATHER, 0.2F, 1.0F, false);
        }
      }

    }
  }

  public void close() {
    if (this.entityEffect != null) {
      this.entityEffect.close();
    }

  }

  public void onResourceManagerReload(IResourceManager p_195410_1_) {
    this.textureManager.bind(FORCEFIELD_LOCATION);
    RenderSystem.texParameter(3553, 10242, 10497);
    RenderSystem.texParameter(3553, 10243, 10497);
    RenderSystem.bindTexture(0);
    this.initOutline();
  }

  public void initOutline() {
    if (this.entityEffect != null) {
      this.entityEffect.close();
    }

    ResourceLocation resourcelocation = new ResourceLocation("shaders/post/entity_outline.json");

    try {
      this.entityEffect = new ShaderGroup(this.minecraft.getTextureManager(), this.minecraft.getResourceManager(), this.minecraft.getMainRenderTarget(), resourcelocation);
      this.entityEffect.resize(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight());
      this.entityTarget = this.entityEffect.getTempTarget("final");
    } catch (IOException ioexception) {
      LOGGER.warn("Failed to load shader: {}", resourcelocation, ioexception);
      this.entityEffect = null;
      this.entityTarget = null;
    } catch (JsonSyntaxException jsonsyntaxexception) {
      LOGGER.warn("Failed to load shader: {}", resourcelocation, jsonsyntaxexception);
      this.entityEffect = null;
      this.entityTarget = null;
    }

  }

  public void doEntityOutline() {
    if (this.shouldShowEntityOutlines()) {
      RenderSystem.enableBlend();
      RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
      this.entityTarget.blitToScreen(this.minecraft.getWindow().getWidth(), this.minecraft.getWindow().getHeight(), false);
      RenderSystem.disableBlend();
    }

  }

  protected boolean shouldShowEntityOutlines() {
    return this.entityTarget != null && this.entityEffect != null && this.minecraft.player != null;
  }

  private void createDarkSky() {
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder bufferbuilder = tessellator.getBuilder();
    if (this.darkBuffer != null) {
      this.darkBuffer.close();
    }

    this.darkBuffer = new VertexBuffer(this.skyFormat);
    this.drawSkyHemisphere(bufferbuilder, -16.0F, true);
    bufferbuilder.end();
    this.darkBuffer.upload(bufferbuilder);
  }

  private void createLightSky() {
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder bufferbuilder = tessellator.getBuilder();
    if (this.skyBuffer != null) {
      this.skyBuffer.close();
    }

    this.skyBuffer = new VertexBuffer(this.skyFormat);
    this.drawSkyHemisphere(bufferbuilder, 16.0F, false);
    bufferbuilder.end();
    this.skyBuffer.upload(bufferbuilder);
  }

  private void drawSkyHemisphere(BufferBuilder p_174968_1_, float p_174968_2_, boolean p_174968_3_) {
    int i = 64;
    int j = 6;
    p_174968_1_.begin(7, DefaultVertexFormats.POSITION);

    for (int k = -384; k <= 384; k += 64) {
      for (int l = -384; l <= 384; l += 64) {
        float f = (float) k;
        float f1 = (float) (k + 64);
        if (p_174968_3_) {
          f1 = (float) k;
          f = (float) (k + 64);
        }

        p_174968_1_.vertex((double) f, (double) p_174968_2_, (double) l).endVertex();
        p_174968_1_.vertex((double) f1, (double) p_174968_2_, (double) l).endVertex();
        p_174968_1_.vertex((double) f1, (double) p_174968_2_, (double) (l + 64)).endVertex();
        p_174968_1_.vertex((double) f, (double) p_174968_2_, (double) (l + 64)).endVertex();
      }
    }

  }

  private void createStars() {
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder bufferbuilder = tessellator.getBuilder();
    if (this.starBuffer != null) {
      this.starBuffer.close();
    }

    this.starBuffer = new VertexBuffer(this.skyFormat);
    this.drawStars(bufferbuilder);
    bufferbuilder.end();
    this.starBuffer.upload(bufferbuilder);
  }

  private void drawStars(BufferBuilder p_180444_1_) {
    Random random = new Random(10842L);
    p_180444_1_.begin(7, DefaultVertexFormats.POSITION);

    for (int i = 0; i < 1500; ++i) {
      double d0 = (double) (random.nextFloat() * 2.0F - 1.0F);
      double d1 = (double) (random.nextFloat() * 2.0F - 1.0F);
      double d2 = (double) (random.nextFloat() * 2.0F - 1.0F);
      double d3 = (double) (0.15F + random.nextFloat() * 0.1F);
      double d4 = d0 * d0 + d1 * d1 + d2 * d2;
      if (d4 < 1.0D && d4 > 0.01D) {
        d4 = 1.0D / Math.sqrt(d4);
        d0 = d0 * d4;
        d1 = d1 * d4;
        d2 = d2 * d4;
        double d5 = d0 * 100.0D;
        double d6 = d1 * 100.0D;
        double d7 = d2 * 100.0D;
        double d8 = Math.atan2(d0, d2);
        double d9 = Math.sin(d8);
        double d10 = Math.cos(d8);
        double d11 = Math.atan2(Math.sqrt(d0 * d0 + d2 * d2), d1);
        double d12 = Math.sin(d11);
        double d13 = Math.cos(d11);
        double d14 = random.nextDouble() * Math.PI * 2.0D;
        double d15 = Math.sin(d14);
        double d16 = Math.cos(d14);

        for (int j = 0; j < 4; ++j) {
          double d17 = 0.0D;
          double d18 = (double) ((j & 2) - 1) * d3;
          double d19 = (double) ((j + 1 & 2) - 1) * d3;
          double d20 = 0.0D;
          double d21 = d18 * d16 - d19 * d15;
          double d22 = d19 * d16 + d18 * d15;
          double d23 = d21 * d12 + 0.0D * d13;
          double d24 = 0.0D * d12 - d21 * d13;
          double d25 = d24 * d9 - d22 * d10;
          double d26 = d22 * d9 + d24 * d10;
          p_180444_1_.vertex(d5 + d25, d6 + d23, d7 + d26).endVertex();
        }
      }
    }

  }

  public void setLevel(@Nullable ClientWorld p_72732_1_) {
    this.lastCameraX = Double.MIN_VALUE;
    this.lastCameraY = Double.MIN_VALUE;
    this.lastCameraZ = Double.MIN_VALUE;
    this.lastCameraChunkX = Integer.MIN_VALUE;
    this.lastCameraChunkY = Integer.MIN_VALUE;
    this.lastCameraChunkZ = Integer.MIN_VALUE;
    this.entityRenderDispatcher.setLevel(p_72732_1_);
    this.level = p_72732_1_;
    if (p_72732_1_ != null) {
      this.allChanged();
    } else {
      this.chunksToCompile.clear();
      this.renderChunks.clear();
      if (this.viewArea != null) {
        this.viewArea.releaseAllBuffers();
        this.viewArea = null;
      }

      if (this.chunkRenderDispatcher != null) {
        this.chunkRenderDispatcher.dispose();
      }

      this.chunkRenderDispatcher = null;
      this.globalBlockEntities.clear();
    }

  }

  public void allChanged() {
    if (this.level != null) {
      this.level.clearTintCaches();
      if (this.chunkRenderDispatcher == null) {
        this.chunkRenderDispatcher = new ChunkRenderDispatcher(this.level, this, Util.backgroundExecutor(), this.minecraft.is64Bit(), this.renderBuffers.fixedBufferPack());
      } else {
        this.chunkRenderDispatcher.setLevel(this.level);
      }

      this.needsUpdate = true;
      this.generateClouds = true;
      RenderTypeLookup.setFancy(this.minecraft.options.fancyGraphics);
      this.lastViewDistance = this.minecraft.options.renderDistance;
      if (this.viewArea != null) {
        this.viewArea.releaseAllBuffers();
      }

      this.resetChunksToCompile();
      synchronized (this.globalBlockEntities) {
        this.globalBlockEntities.clear();
      }

      this.viewArea = new ViewFrustrum2(this.chunkRenderDispatcher, this.level, this.minecraft.options.renderDistance, this);
      if (this.level != null) {
        Entity entity = this.minecraft.getCameraEntity();
        if (entity != null) {
          this.viewArea.repositionCamera(entity.getX(), entity.getZ());
        }
      }

    }
  }

  protected void resetChunksToCompile() {
    this.chunksToCompile.clear();
    this.chunkRenderDispatcher.blockUntilClear();
  }

  public void resize(int p_72720_1_, int p_72720_2_) {
    this.needsUpdate();
    if (this.entityEffect != null) {
      this.entityEffect.resize(p_72720_1_, p_72720_2_);
    }

  }

  public String getChunkStatistics() {
    int i = this.viewArea.chunks.length;
    int j = this.countRenderedChunks();
    return String.format("C: %d/%d %sD: %d, %s", j, i, this.minecraft.smartCull ? "(s) " : "", this.lastViewDistance, this.chunkRenderDispatcher == null ? "null" : this.chunkRenderDispatcher.getStats());
  }

  protected int countRenderedChunks() {
    int i = 0;

    for (LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer : this.renderChunks) {
      if (!worldrenderer$localrenderinformationcontainer.chunk.getCompiledChunk().hasNoRenderableLayers()) {
        ++i;
      }
    }

    return i;
  }

  public String getEntityStatistics() {
    return "E: " + this.renderedEntities + "/" + this.level.getEntityCount() + ", B: " + this.culledEntities;
  }

  private void setupRender(ActiveRenderInfo p_228437_1_, ClippingHelperImpl p_228437_2_, boolean p_228437_3_, int p_228437_4_, boolean p_228437_5_) {
    Vec3d vec3d = p_228437_1_.getPosition();
    if (this.minecraft.options.renderDistance != this.lastViewDistance) {
      this.allChanged();
    }

    this.level.getProfiler().push("camera");
    double d0 = this.minecraft.player.getX() - this.lastCameraX;
    double d1 = this.minecraft.player.getY() - this.lastCameraY;
    double d2 = this.minecraft.player.getZ() - this.lastCameraZ;
    if (this.lastCameraChunkX != this.minecraft.player.xChunk || this.lastCameraChunkY != this.minecraft.player.yChunk || this.lastCameraChunkZ != this.minecraft.player.zChunk || d0 * d0 + d1 * d1 + d2 * d2 > 16.0D) {
      this.lastCameraX = this.minecraft.player.getX();
      this.lastCameraY = this.minecraft.player.getY();
      this.lastCameraZ = this.minecraft.player.getZ();
      this.lastCameraChunkX = this.minecraft.player.xChunk;
      this.lastCameraChunkY = this.minecraft.player.yChunk;
      this.lastCameraChunkZ = this.minecraft.player.zChunk;
      this.viewArea.repositionCamera(this.minecraft.player.getX(), this.minecraft.player.getZ());
    }

    this.chunkRenderDispatcher.setCamera(vec3d);
    this.level.getProfiler().popPush("cull");
    this.minecraft.getProfiler().popPush("culling");
    BlockPos blockpos = p_228437_1_.getBlockPosition();
    ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender = this.viewArea.getRenderChunkAt(blockpos);
    int i = 16;
    BlockPos blockpos1 = new BlockPos(MathHelper.floor(vec3d.x / 16.0D) * 16, MathHelper.floor(vec3d.y / 16.0D) * 16, MathHelper.floor(vec3d.z / 16.0D) * 16);
    float f = p_228437_1_.getXRot();
    float f1 = p_228437_1_.getYRot();
    this.needsUpdate = this.needsUpdate || !this.chunksToCompile.isEmpty() || vec3d.x != this.prevCamX || vec3d.y != this.prevCamY || vec3d.z != this.prevCamZ || (double) f != this.prevCamRotX || (double) f1 != this.prevCamRotY;
    this.prevCamX = vec3d.x;
    this.prevCamY = vec3d.y;
    this.prevCamZ = vec3d.z;
    this.prevCamRotX = (double) f;
    this.prevCamRotY = (double) f1;
    this.minecraft.getProfiler().popPush("update");
    if (!p_228437_3_ && this.needsUpdate) {
      this.needsUpdate = false;
      this.renderChunks.clear();
      Queue<LocalRenderInformationContainer> queue = Queues.newArrayDeque();
      Entity.setViewScale(MathHelper.clamp((double) this.minecraft.options.renderDistance / 8.0D, 1.0D, 2.5D));
      boolean flag = this.minecraft.smartCull;
      if (chunkrenderdispatcher$chunkrender != null) {
        boolean flag1 = false;
        LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer3 = new LocalRenderInformationContainer(chunkrenderdispatcher$chunkrender, (Direction) null, 0);
        Set<Direction> set1 = this.getVisibleDirections(blockpos);
        if (set1.size() == 1) {
          Vector3f vector3f = p_228437_1_.getLookVector();
          Direction direction1 = Direction.getNearest(vector3f.x(), vector3f.y(), vector3f.z()).getOpposite();
          set1.remove(direction1);
        }

        if (set1.isEmpty()) {
          flag1 = true;
        }

        if (flag1 && !p_228437_5_) {
          this.renderChunks.add(worldrenderer$localrenderinformationcontainer3);
        } else {
          if (p_228437_5_ && this.level.getBlockState(blockpos).isSolidRender(this.level, blockpos)) {
            flag = false;
          }

          chunkrenderdispatcher$chunkrender.setFrame(p_228437_4_);
          queue.add(worldrenderer$localrenderinformationcontainer3);
        }
      } else {
        int j = blockpos.getY() > 0 ? 248 : 8;
        int k = MathHelper.floor(vec3d.x / 16.0D) * 16;
        int l = MathHelper.floor(vec3d.z / 16.0D) * 16;
        List<LocalRenderInformationContainer> list = Lists.newArrayList();

        for (int i1 = -this.lastViewDistance; i1 <= this.lastViewDistance; ++i1) {
          for (int j1 = -this.lastViewDistance; j1 <= this.lastViewDistance; ++j1) {
            ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender1 = this.viewArea.getRenderChunkAt(new BlockPos(k + (i1 << 4) + 8, j, l + (j1 << 4) + 8));
            if (chunkrenderdispatcher$chunkrender1 != null && p_228437_2_.isVisible(chunkrenderdispatcher$chunkrender1.bb)) {
              chunkrenderdispatcher$chunkrender1.setFrame(p_228437_4_);
              list.add(new LocalRenderInformationContainer(chunkrenderdispatcher$chunkrender1, (Direction) null, 0));
            }
          }
        }

        list.sort(Comparator.comparingDouble((p_230016_1_) -> {
          return blockpos.distSqr(p_230016_1_.chunk.getOrigin().offset(8, 8, 8));
        }));
        queue.addAll(list);
      }

      this.minecraft.getProfiler().push("iteration");

      while (!queue.isEmpty()) {
        LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer1 = queue.poll();
        ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender3 = worldrenderer$localrenderinformationcontainer1.chunk;
        Direction direction = worldrenderer$localrenderinformationcontainer1.sourceDirection;
        this.renderChunks.add(worldrenderer$localrenderinformationcontainer1);

        for (Direction direction2 : DIRECTIONS) {
          ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender2 = this.getRelativeFrom(blockpos1, chunkrenderdispatcher$chunkrender3, direction2);
          if ((!flag || !worldrenderer$localrenderinformationcontainer1.hasDirection(direction2.getOpposite())) && (!flag || direction == null || chunkrenderdispatcher$chunkrender3.getCompiledChunk().facesCanSeeEachother(direction.getOpposite(), direction2)) && chunkrenderdispatcher$chunkrender2 != null && chunkrenderdispatcher$chunkrender2.hasAllNeighbors() && chunkrenderdispatcher$chunkrender2.setFrame(p_228437_4_) && p_228437_2_.isVisible(chunkrenderdispatcher$chunkrender2.bb)) {
            LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer = new LocalRenderInformationContainer(chunkrenderdispatcher$chunkrender2, direction2, worldrenderer$localrenderinformationcontainer1.step + 1);
            worldrenderer$localrenderinformationcontainer.setDirections(worldrenderer$localrenderinformationcontainer1.directions, direction2);
            queue.add(worldrenderer$localrenderinformationcontainer);
          }
        }
      }

      this.minecraft.getProfiler().pop();
    }

    this.minecraft.getProfiler().popPush("rebuildNear");
    Set<ChunkRenderDispatcher.ChunkRender> set = this.chunksToCompile;
    this.chunksToCompile = Sets.newLinkedHashSet();

    for (LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer2 : this.renderChunks) {
      ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender4 = worldrenderer$localrenderinformationcontainer2.chunk;
      if (chunkrenderdispatcher$chunkrender4.isDirty() || set.contains(chunkrenderdispatcher$chunkrender4)) {
        this.needsUpdate = true;
        BlockPos blockpos2 = chunkrenderdispatcher$chunkrender4.getOrigin().offset(8, 8, 8);
        boolean flag2 = blockpos2.distSqr(blockpos) < 768.0D;
        if (net.minecraftforge.common.ForgeConfig.CLIENT.alwaysSetupTerrainOffThread.get() || !chunkrenderdispatcher$chunkrender4.isDirtyFromPlayer() && !flag2) {
          this.chunksToCompile.add(chunkrenderdispatcher$chunkrender4);
        } else {
          this.minecraft.getProfiler().push("build near");
          this.chunkRenderDispatcher.rebuildChunkSync(chunkrenderdispatcher$chunkrender4);
          chunkrenderdispatcher$chunkrender4.setNotDirty();
          this.minecraft.getProfiler().pop();
        }
      }
    }

    this.chunksToCompile.addAll(set);
    this.minecraft.getProfiler().pop();
  }

  private Set<Direction> getVisibleDirections(BlockPos p_174978_1_) {
    VisGraph visgraph = new VisGraph();
    BlockPos blockpos = new BlockPos(p_174978_1_.getX() >> 4 << 4, p_174978_1_.getY() >> 4 << 4, p_174978_1_.getZ() >> 4 << 4);
    Chunk chunk = this.level.getChunkAt(blockpos);

    for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos, blockpos.offset(15, 15, 15))) {
      if (chunk.getBlockState(blockpos1).isSolidRender(this.level, blockpos1)) {
        visgraph.setOpaque(blockpos1);
      }
    }

    return visgraph.floodFill(p_174978_1_);
  }

  @Nullable
  private ChunkRenderDispatcher.ChunkRender getRelativeFrom(BlockPos p_181562_1_, ChunkRenderDispatcher.ChunkRender p_181562_2_, Direction p_181562_3_) {
    BlockPos blockpos = p_181562_2_.getRelativeOrigin(p_181562_3_);
    if (MathHelper.abs(p_181562_1_.getX() - blockpos.getX()) > this.lastViewDistance * 16) {
      return null;
    } else if (blockpos.getY() >= 0 && blockpos.getY() < 256) {
      return MathHelper.abs(p_181562_1_.getZ() - blockpos.getZ()) > this.lastViewDistance * 16 ? null : this.viewArea.getRenderChunkAt(blockpos);
    } else {
      return null;
    }
  }

  private void captureFrustum(Matrix4f p_228419_1_, Matrix4f p_228419_2_, double p_228419_3_, double p_228419_5_, double p_228419_7_, ClippingHelperImpl p_228419_9_) {
    this.capturedFrustum = p_228419_9_;
    Matrix4f matrix4f = p_228419_2_.copy();
    matrix4f.multiply(p_228419_1_);
    matrix4f.invert();
    this.frustumPos.x = p_228419_3_;
    this.frustumPos.y = p_228419_5_;
    this.frustumPos.z = p_228419_7_;
    this.frustumPoints[0] = new Vector4f(-1.0F, -1.0F, -1.0F, 1.0F);
    this.frustumPoints[1] = new Vector4f(1.0F, -1.0F, -1.0F, 1.0F);
    this.frustumPoints[2] = new Vector4f(1.0F, 1.0F, -1.0F, 1.0F);
    this.frustumPoints[3] = new Vector4f(-1.0F, 1.0F, -1.0F, 1.0F);
    this.frustumPoints[4] = new Vector4f(-1.0F, -1.0F, 1.0F, 1.0F);
    this.frustumPoints[5] = new Vector4f(1.0F, -1.0F, 1.0F, 1.0F);
    this.frustumPoints[6] = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    this.frustumPoints[7] = new Vector4f(-1.0F, 1.0F, 1.0F, 1.0F);

    for (int i = 0; i < 8; ++i) {
      this.frustumPoints[i].transform(matrix4f);
      this.frustumPoints[i].perspectiveDivide();
    }

  }

  public void renderLevel(MatrixStack matrixStack, float p_228426_2_, long p_228426_3_, boolean p_228426_5_, ActiveRenderInfo p_228426_6_, GameRenderer p_228426_7_, LightTexture p_228426_8_, Matrix4f p_228426_9_) {
    TileEntityRendererDispatcher.instance.prepare(this.level, this.minecraft.getTextureManager(), this.minecraft.font, p_228426_6_, this.minecraft.hitResult);
    this.entityRenderDispatcher.prepare(this.level, p_228426_6_, this.minecraft.crosshairPickEntity);
    IProfiler iprofiler = this.level.getProfiler();
    iprofiler.popPush("light_updates");
    this.minecraft.level.getChunkSource().getLightEngine().runUpdates(Integer.MAX_VALUE, true, true);
    Vec3d vec3d = p_228426_6_.getPosition();
    double x = vec3d.x();
    double y = vec3d.y();
    double z = vec3d.z();
    Matrix4f matrix4f = matrixStack.last().pose();
    iprofiler.popPush("culling");
    boolean flag = this.capturedFrustum != null;
    ClippingHelperImpl clippinghelperimpl;
    if (flag) {
      clippinghelperimpl = this.capturedFrustum;
      clippinghelperimpl.prepare(this.frustumPos.x, this.frustumPos.y, this.frustumPos.z);
    } else {
      clippinghelperimpl = new ClippingHelperImpl(matrix4f, p_228426_9_);
      clippinghelperimpl.prepare(x, y, z);
    }

    this.minecraft.getProfiler().popPush("captureFrustum");
    if (this.captureFrustum) {
      this.captureFrustum(matrix4f, p_228426_9_, vec3d.x, vec3d.y, vec3d.z, flag ? new ClippingHelperImpl(matrix4f, p_228426_9_) : clippinghelperimpl);
      this.captureFrustum = false;
    }

    iprofiler.popPush("clear");
    FogRenderer.setupColor(p_228426_6_, p_228426_2_, this.minecraft.level, this.minecraft.options.renderDistance, p_228426_7_.getDarkenWorldAmount(p_228426_2_));
    RenderSystem.clear(16640, Minecraft.ON_OSX);
    float f = p_228426_7_.getRenderDistance();
    boolean flag1 = this.minecraft.level.dimension.isFoggyAt(MathHelper.floor(x), MathHelper.floor(y)) || this.minecraft.gui.getBossOverlay().shouldCreateWorldFog();
    if (this.minecraft.options.renderDistance >= 4) {
      FogRenderer.setupFog(p_228426_6_, FogRenderer.FogType.FOG_SKY, f, flag1, p_228426_2_);
      iprofiler.popPush("sky");
      this.renderSky(matrixStack, p_228426_2_);
    }

    iprofiler.popPush("fog");
    FogRenderer.setupFog(p_228426_6_, FogRenderer.FogType.FOG_TERRAIN, Math.max(f - 16.0F, 32.0F), flag1, p_228426_2_);
    iprofiler.popPush("terrain_setup");
    this.setupRender(p_228426_6_, clippinghelperimpl, flag, this.frameId++, this.minecraft.player.isSpectator());
    iprofiler.popPush("updatechunks");
    int i = 30;
    int j = this.minecraft.options.framerateLimit;
    long k = 33333333L;
    long l;
    if ((double) j == AbstractOption.FRAMERATE_LIMIT.getMaxValue()) {
      l = 0L;
    } else {
      l = (long) (1000000000 / j);
    }

    long i1 = Util.getNanos() - p_228426_3_;
    long j1 = this.frameTimes.registerValueAndGetMean(i1);
    long k1 = j1 * 3L / 2L;
    long l1 = MathHelper.clamp(k1, l, 33333333L);
    this.compileChunksUntil(p_228426_3_ + l1);
    iprofiler.popPush("terrain");
    this.renderChunkLayer(RenderType.solid(), matrixStack, x, y, z);
    this.minecraft.getModelManager().getAtlas(AtlasTexture.LOCATION_BLOCKS).setFilter(false, this.minecraft.options.mipmapLevels > 0); // FORGE: fix flickering leaves when mods mess up the blurMipmap settings
    this.renderChunkLayer(RenderType.cutoutMipped(), matrixStack, x, y, z);
    this.minecraft.getModelManager().getAtlas(AtlasTexture.LOCATION_BLOCKS).restoreLastBlurMipmap();
    this.renderChunkLayer(RenderType.cutout(), matrixStack, x, y, z);
    RenderHelper.setupLevel(matrixStack.last().pose());
    iprofiler.popPush("entities");
    iprofiler.push("prepare");
    this.renderedEntities = 0;
    this.culledEntities = 0;
    iprofiler.popPush("entities");
    if (this.shouldShowEntityOutlines()) {
      this.entityTarget.clear(Minecraft.ON_OSX);
      this.minecraft.getMainRenderTarget().bindWrite(false);
    }

    boolean flag2 = false;
    IRenderTypeBuffer.Impl irendertypebuffer$impl = this.renderBuffers.bufferSource();

    for (Entity entity : this.level.entitiesForRendering()) {
      if ((this.entityRenderDispatcher.shouldRender(entity, clippinghelperimpl, x, y, z) || entity.hasIndirectPassenger(this.minecraft.player)) && (entity != p_228426_6_.getEntity() || p_228426_6_.isDetached() || p_228426_6_.getEntity() instanceof LivingEntity && ((LivingEntity) p_228426_6_.getEntity()).isSleeping()) && (!(entity instanceof ClientPlayerEntity) || p_228426_6_.getEntity() == entity)) {
        ++this.renderedEntities;
        if (entity.tickCount == 0) {
          entity.xOld = entity.getX();
          entity.yOld = entity.getY();
          entity.zOld = entity.getZ();
        }

        IRenderTypeBuffer irendertypebuffer;
        if (this.shouldShowEntityOutlines() && entity.isGlowing()) {
          flag2 = true;
          OutlineLayerBuffer outlinelayerbuffer = this.renderBuffers.outlineBufferSource();
          irendertypebuffer = outlinelayerbuffer;
          int i2 = entity.getTeamColor();
          int j2 = 255;
          int k2 = i2 >> 16 & 255;
          int l2 = i2 >> 8 & 255;
          int i3 = i2 & 255;
          outlinelayerbuffer.setColor(k2, l2, i3, 255);
        } else {
          irendertypebuffer = irendertypebuffer$impl;
        }

        this.renderEntity(entity, x, y, z, p_228426_2_, matrixStack, irendertypebuffer);
      }
    }

    this.checkPoseStack(matrixStack);
    irendertypebuffer$impl.endBatch(RenderType.entitySolid(AtlasTexture.LOCATION_BLOCKS));
    irendertypebuffer$impl.endBatch(RenderType.entityCutout(AtlasTexture.LOCATION_BLOCKS));
    irendertypebuffer$impl.endBatch(RenderType.entityCutoutNoCull(AtlasTexture.LOCATION_BLOCKS));
    irendertypebuffer$impl.endBatch(RenderType.entitySmoothCutout(AtlasTexture.LOCATION_BLOCKS));
    iprofiler.popPush("blockentities");

    for (LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer : this.renderChunks) {
      List<TileEntity> list = worldrenderer$localrenderinformationcontainer.chunk.getCompiledChunk().getRenderableBlockEntities();
      if (!list.isEmpty()) {
        for (TileEntity tileentity1 : list) {
          if (!clippinghelperimpl.isVisible(tileentity1.getRenderBoundingBox())) continue;
          BlockPos blockpos3 = tileentity1.getBlockPos();
          IRenderTypeBuffer irendertypebuffer1 = irendertypebuffer$impl;
          matrixStack.pushPose();
          matrixStack.translate((double) blockpos3.getX() - x, (double) blockpos3.getY() - y, (double) blockpos3.getZ() - z);
          SortedSet<DestroyBlockProgress> sortedset = this.destructionProgress.get(blockpos3.asLong());
          if (sortedset != null && !sortedset.isEmpty()) {
            int k3 = sortedset.last().getProgress();
            if (k3 >= 0) {
              IVertexBuilder ivertexbuilder = new MatrixApplyingVertexBuilder(this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(k3)), matrixStack.last());
              irendertypebuffer1 = (p_230014_2_) -> {
                IVertexBuilder ivertexbuilder3 = irendertypebuffer$impl.getBuffer(p_230014_2_);
                return p_230014_2_.affectsCrumbling() ? VertexBuilderUtils.create(ivertexbuilder, ivertexbuilder3) : ivertexbuilder3;
              };
            }
          }

          TileEntityRendererDispatcher.instance.render(tileentity1, p_228426_2_, matrixStack, irendertypebuffer1);
          matrixStack.popPose();
        }
      }
    }

    synchronized (this.globalBlockEntities) {
      for (TileEntity tileentity : this.globalBlockEntities) {
        if (!clippinghelperimpl.isVisible(tileentity.getRenderBoundingBox())) continue;
        BlockPos blockpos2 = tileentity.getBlockPos();
        matrixStack.pushPose();
        matrixStack.translate((double) blockpos2.getX() - x, (double) blockpos2.getY() - y, (double) blockpos2.getZ() - z);
        TileEntityRendererDispatcher.instance.render(tileentity, p_228426_2_, matrixStack, irendertypebuffer$impl);
        matrixStack.popPose();
      }
    }

    this.checkPoseStack(matrixStack);
    irendertypebuffer$impl.endBatch(RenderType.solid());
    irendertypebuffer$impl.endBatch(Atlases.solidBlockSheet());
    irendertypebuffer$impl.endBatch(Atlases.cutoutBlockSheet());
    irendertypebuffer$impl.endBatch(Atlases.bedSheet());
    irendertypebuffer$impl.endBatch(Atlases.shulkerBoxSheet());
    irendertypebuffer$impl.endBatch(Atlases.signSheet());
    irendertypebuffer$impl.endBatch(Atlases.chestSheet());
    this.renderBuffers.outlineBufferSource().endOutlineBatch();
    if (flag2) {
      this.entityEffect.process(p_228426_2_);
      this.minecraft.getMainRenderTarget().bindWrite(false);
    }

    iprofiler.popPush("destroyProgress");

    for (Long2ObjectMap.Entry<SortedSet<DestroyBlockProgress>> entry : this.destructionProgress.long2ObjectEntrySet()) {
      BlockPos blockpos1 = BlockPos.of(entry.getLongKey());
      double d3 = (double) blockpos1.getX() - x;
      double d4 = (double) blockpos1.getY() - y;
      double d5 = (double) blockpos1.getZ() - z;
      if (!(d3 * d3 + d4 * d4 + d5 * d5 > 1024.0D)) {
        SortedSet<DestroyBlockProgress> sortedset1 = entry.getValue();
        if (sortedset1 != null && !sortedset1.isEmpty()) {
          int j3 = sortedset1.last().getProgress();
          matrixStack.pushPose();
          matrixStack.translate((double) blockpos1.getX() - x, (double) blockpos1.getY() - y, (double) blockpos1.getZ() - z);
          IVertexBuilder ivertexbuilder1 = new MatrixApplyingVertexBuilder(this.renderBuffers.crumblingBufferSource().getBuffer(ModelBakery.DESTROY_TYPES.get(j3)), matrixStack.last());
          this.minecraft.getBlockRenderer().renderBreakingTexture(this.level.getBlockState(blockpos1), blockpos1, this.level, matrixStack, ivertexbuilder1);
          matrixStack.popPose();
        }
      }
    }

    this.checkPoseStack(matrixStack);
    iprofiler.pop();
    RayTraceResult raytraceresult = this.minecraft.hitResult;

    if (p_228426_5_ && raytraceresult != null && raytraceresult.getType() == RayTraceResult.Type.BLOCK) {
      iprofiler.popPush("outline");
      BlockPos blockpos = ((BlockRayTraceResult) raytraceresult).getBlockPos();
      BlockState blockstate = this.level.getBlockState(blockpos);

      if (!net.minecraftforge.client.ForgeHooksClient.onDrawBlockHighlight(this, p_228426_6_, minecraft.hitResult, 0, p_228426_2_))
        if (!blockstate.isAir(this.level, blockpos) && this.level.getWorldBorder().isWithinBounds(blockpos)) {
          IVertexBuilder ivertexbuilder2 = irendertypebuffer$impl.getBuffer(RenderType.lines());
          this.renderHitOutline(matrixStack, ivertexbuilder2, p_228426_6_.getEntity(), x, y, z, blockpos, blockstate);

        }
    }

    RenderSystem.pushMatrix();
    RenderSystem.multMatrix(matrixStack.last().pose());
    this.minecraft.debugRenderer.render(matrixStack, irendertypebuffer$impl, x, y, z);
    this.renderWorldBounds(p_228426_6_);
    RenderSystem.popMatrix();


    irendertypebuffer$impl.endBatch(Atlases.translucentBlockSheet());
    irendertypebuffer$impl.endBatch(Atlases.bannerSheet());
    irendertypebuffer$impl.endBatch(Atlases.shieldSheet());
    irendertypebuffer$impl.endBatch(RenderType.glint());


    irendertypebuffer$impl.endBatch(RenderType.entityGlint());
    irendertypebuffer$impl.endBatch(RenderType.waterMask());
    this.renderBuffers.crumblingBufferSource().endBatch();
//
    irendertypebuffer$impl.endBatch(RenderType.lines());
    irendertypebuffer$impl.endBatch();
    //

    if (Screen.hasAltDown()) {
      IVertexBuilder ivertexbuilder3 = irendertypebuffer$impl.getBuffer(RenderType.lines());
      asdf.renderBoxes(matrixStack, ivertexbuilder3, x, y, z);
    }

    irendertypebuffer$impl.endBatch(RenderType.lines());

    iprofiler.popPush("translucent");
    this.renderChunkLayer(RenderType.translucent(), matrixStack, x, y, z);
    iprofiler.popPush("particles");
    this.minecraft.particleEngine.render(matrixStack, irendertypebuffer$impl, p_228426_8_, p_228426_6_, p_228426_2_);
    RenderSystem.pushMatrix();
    RenderSystem.multMatrix(matrixStack.last().pose());
    iprofiler.popPush("cloudsLayers");
    if (this.minecraft.options.getCloudsType() != CloudOption.OFF) {
      iprofiler.popPush("clouds");
      this.renderClouds(matrixStack, p_228426_2_, x, y, z);
    }

    RenderSystem.depthMask(false);

    iprofiler.popPush("weather");
    this.renderSnowAndRain(p_228426_8_, p_228426_2_, x, y, z);
    RenderSystem.depthMask(true);
    this.renderDebug(p_228426_6_);
    RenderSystem.shadeModel(7424);
    RenderSystem.depthMask(true);
    RenderSystem.disableBlend();
    RenderSystem.popMatrix();
    FogRenderer.setupNoFog();
  }

  private void checkPoseStack(MatrixStack p_228423_1_) {
    if (!p_228423_1_.clear()) {
      throw new IllegalStateException("Pose stack not empty");
    }
  }

  private void renderEntity(Entity p_228418_1_, double p_228418_2_, double p_228418_4_, double p_228418_6_, float p_228418_8_, MatrixStack p_228418_9_, IRenderTypeBuffer p_228418_10_) {
    double d0 = MathHelper.lerp((double) p_228418_8_, p_228418_1_.xOld, p_228418_1_.getX());
    double d1 = MathHelper.lerp((double) p_228418_8_, p_228418_1_.yOld, p_228418_1_.getY());
    double d2 = MathHelper.lerp((double) p_228418_8_, p_228418_1_.zOld, p_228418_1_.getZ());
    float f = MathHelper.lerp(p_228418_8_, p_228418_1_.yRotO, p_228418_1_.yRot);
    this.entityRenderDispatcher.render(p_228418_1_, d0 - p_228418_2_, d1 - p_228418_4_, d2 - p_228418_6_, f, p_228418_8_, p_228418_9_, p_228418_10_, this.entityRenderDispatcher.getPackedLightCoords(p_228418_1_, p_228418_8_));
  }

  private void renderChunkLayer(RenderType p_228441_1_, MatrixStack p_228441_2_, double p_228441_3_, double p_228441_5_, double p_228441_7_) {
    p_228441_1_.setupRenderState();
    if (p_228441_1_ == RenderType.translucent()) {
      this.minecraft.getProfiler().push("translucent_sort");
      double d0 = p_228441_3_ - this.xTransparentOld;
      double d1 = p_228441_5_ - this.yTransparentOld;
      double d2 = p_228441_7_ - this.zTransparentOld;
      if (d0 * d0 + d1 * d1 + d2 * d2 > 1.0D) {
        this.xTransparentOld = p_228441_3_;
        this.yTransparentOld = p_228441_5_;
        this.zTransparentOld = p_228441_7_;
        int i = 0;

        for (LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer : this.renderChunks) {
          if (i < 15 && worldrenderer$localrenderinformationcontainer.chunk.resortTransparency(p_228441_1_, this.chunkRenderDispatcher)) {
            ++i;
          }
        }
      }

      this.minecraft.getProfiler().pop();
    }

    this.minecraft.getProfiler().push("filterempty");
    this.minecraft.getProfiler().popPush(() -> {
      return "render_" + p_228441_1_;
    });
    boolean flag = p_228441_1_ != RenderType.translucent();
    ObjectListIterator<LocalRenderInformationContainer> objectlistiterator = this.renderChunks.listIterator(flag ? 0 : this.renderChunks.size());

    while (true) {
      if (flag) {
        if (!objectlistiterator.hasNext()) {
          break;
        }
      } else if (!objectlistiterator.hasPrevious()) {
        break;
      }

      LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer1 = flag ? objectlistiterator.next() : objectlistiterator.previous();
      ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender = worldrenderer$localrenderinformationcontainer1.chunk;
      if (!chunkrenderdispatcher$chunkrender.getCompiledChunk().isEmpty(p_228441_1_)) {
        VertexBuffer vertexbuffer = chunkrenderdispatcher$chunkrender.getBuffer(p_228441_1_);
        p_228441_2_.pushPose();
        BlockPos blockpos = chunkrenderdispatcher$chunkrender.getOrigin();
        p_228441_2_.translate((double) blockpos.getX() - p_228441_3_, (double) blockpos.getY() - p_228441_5_, (double) blockpos.getZ() - p_228441_7_);
        vertexbuffer.bind();
        this.format.setupBufferState(0L);
        vertexbuffer.draw(p_228441_2_.last().pose(), 7);
        p_228441_2_.popPose();
      }
    }

    VertexBuffer.unbind();
    RenderSystem.clearCurrentColor();
    this.format.clearBufferState();
    this.minecraft.getProfiler().pop();
    p_228441_1_.clearRenderState();
  }

  private void renderDebug(ActiveRenderInfo p_228446_1_) {
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder bufferbuilder = tessellator.getBuilder();
    if (this.minecraft.chunkPath || this.minecraft.chunkVisibility) {
      double d0 = p_228446_1_.getPosition().x();
      double d1 = p_228446_1_.getPosition().y();
      double d2 = p_228446_1_.getPosition().z();
      RenderSystem.depthMask(true);
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.disableTexture();

      for (LocalRenderInformationContainer worldrenderer$localrenderinformationcontainer : this.renderChunks) {
        ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender = worldrenderer$localrenderinformationcontainer.chunk;
        RenderSystem.pushMatrix();
        BlockPos blockpos = chunkrenderdispatcher$chunkrender.getOrigin();
        RenderSystem.translated((double) blockpos.getX() - d0, (double) blockpos.getY() - d1, (double) blockpos.getZ() - d2);
        if (this.minecraft.chunkPath) {
          bufferbuilder.begin(1, DefaultVertexFormats.POSITION_COLOR);
          RenderSystem.lineWidth(10.0F);
          int i = worldrenderer$localrenderinformationcontainer.step == 0 ? 0 : MathHelper.hsvToRgb((float) worldrenderer$localrenderinformationcontainer.step / 50.0F, 0.9F, 0.9F);
          int j = i >> 16 & 255;
          int k = i >> 8 & 255;
          int l = i & 255;
          Direction direction = worldrenderer$localrenderinformationcontainer.sourceDirection;
          if (direction != null) {
            bufferbuilder.vertex(8.0D, 8.0D, 8.0D).color(j, k, l, 255).endVertex();
            bufferbuilder.vertex((double) (8 - 16 * direction.getStepX()), (double) (8 - 16 * direction.getStepY()), (double) (8 - 16 * direction.getStepZ())).color(j, k, l, 255).endVertex();
          }

          tessellator.end();
          RenderSystem.lineWidth(1.0F);
        }

        if (this.minecraft.chunkVisibility && !chunkrenderdispatcher$chunkrender.getCompiledChunk().hasNoRenderableLayers()) {
          bufferbuilder.begin(1, DefaultVertexFormats.POSITION_COLOR);
          RenderSystem.lineWidth(10.0F);
          int i1 = 0;

          for (Direction direction2 : Direction.values()) {
            for (Direction direction1 : Direction.values()) {
              boolean flag = chunkrenderdispatcher$chunkrender.getCompiledChunk().facesCanSeeEachother(direction2, direction1);
              if (!flag) {
                ++i1;
                bufferbuilder.vertex((double) (8 + 8 * direction2.getStepX()), (double) (8 + 8 * direction2.getStepY()), (double) (8 + 8 * direction2.getStepZ())).color(1, 0, 0, 1).endVertex();
                bufferbuilder.vertex((double) (8 + 8 * direction1.getStepX()), (double) (8 + 8 * direction1.getStepY()), (double) (8 + 8 * direction1.getStepZ())).color(1, 0, 0, 1).endVertex();
              }
            }
          }

          tessellator.end();
          RenderSystem.lineWidth(1.0F);
          if (i1 > 0) {
            bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
            float f = 0.5F;
            float f1 = 0.2F;
            bufferbuilder.vertex(0.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 15.5D, 0.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 15.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(15.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            bufferbuilder.vertex(0.5D, 0.5D, 15.5D).color(0.9F, 0.9F, 0.0F, 0.2F).endVertex();
            tessellator.end();
          }
        }

        RenderSystem.popMatrix();
      }

      RenderSystem.depthMask(true);
      RenderSystem.disableBlend();
      RenderSystem.enableCull();
      RenderSystem.enableTexture();
    }

    if (this.capturedFrustum != null) {
      RenderSystem.disableCull();
      RenderSystem.disableTexture();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      RenderSystem.lineWidth(10.0F);
      RenderSystem.pushMatrix();
      RenderSystem.translatef((float) (this.frustumPos.x - p_228446_1_.getPosition().x), (float) (this.frustumPos.y - p_228446_1_.getPosition().y), (float) (this.frustumPos.z - p_228446_1_.getPosition().z));
      RenderSystem.depthMask(true);
      bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
      this.addFrustumQuad(bufferbuilder, 0, 1, 2, 3, 0, 1, 1);
      this.addFrustumQuad(bufferbuilder, 4, 5, 6, 7, 1, 0, 0);
      this.addFrustumQuad(bufferbuilder, 0, 1, 5, 4, 1, 1, 0);
      this.addFrustumQuad(bufferbuilder, 2, 3, 7, 6, 0, 0, 1);
      this.addFrustumQuad(bufferbuilder, 0, 4, 7, 3, 0, 1, 0);
      this.addFrustumQuad(bufferbuilder, 1, 5, 6, 2, 1, 0, 1);
      tessellator.end();
      RenderSystem.depthMask(false);
      bufferbuilder.begin(1, DefaultVertexFormats.POSITION);
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      this.addFrustumVertex(bufferbuilder, 0);
      this.addFrustumVertex(bufferbuilder, 1);
      this.addFrustumVertex(bufferbuilder, 1);
      this.addFrustumVertex(bufferbuilder, 2);
      this.addFrustumVertex(bufferbuilder, 2);
      this.addFrustumVertex(bufferbuilder, 3);
      this.addFrustumVertex(bufferbuilder, 3);
      this.addFrustumVertex(bufferbuilder, 0);
      this.addFrustumVertex(bufferbuilder, 4);
      this.addFrustumVertex(bufferbuilder, 5);
      this.addFrustumVertex(bufferbuilder, 5);
      this.addFrustumVertex(bufferbuilder, 6);
      this.addFrustumVertex(bufferbuilder, 6);
      this.addFrustumVertex(bufferbuilder, 7);
      this.addFrustumVertex(bufferbuilder, 7);
      this.addFrustumVertex(bufferbuilder, 4);
      this.addFrustumVertex(bufferbuilder, 0);
      this.addFrustumVertex(bufferbuilder, 4);
      this.addFrustumVertex(bufferbuilder, 1);
      this.addFrustumVertex(bufferbuilder, 5);
      this.addFrustumVertex(bufferbuilder, 2);
      this.addFrustumVertex(bufferbuilder, 6);
      this.addFrustumVertex(bufferbuilder, 3);
      this.addFrustumVertex(bufferbuilder, 7);
      tessellator.end();
      RenderSystem.popMatrix();
      RenderSystem.depthMask(true);
      RenderSystem.disableBlend();
      RenderSystem.enableCull();
      RenderSystem.enableTexture();
      RenderSystem.lineWidth(1.0F);
    }

  }

  private void addFrustumVertex(IVertexBuilder p_228433_1_, int p_228433_2_) {
    p_228433_1_.vertex((double) this.frustumPoints[p_228433_2_].x(), (double) this.frustumPoints[p_228433_2_].y(), (double) this.frustumPoints[p_228433_2_].z()).endVertex();
  }

  private void addFrustumQuad(IVertexBuilder p_228434_1_, int p_228434_2_, int p_228434_3_, int p_228434_4_, int p_228434_5_, int p_228434_6_, int p_228434_7_, int p_228434_8_) {
    float f = 0.25F;
    p_228434_1_.vertex((double) this.frustumPoints[p_228434_2_].x(), (double) this.frustumPoints[p_228434_2_].y(), (double) this.frustumPoints[p_228434_2_].z()).color((float) p_228434_6_, (float) p_228434_7_, (float) p_228434_8_, 0.25F).endVertex();
    p_228434_1_.vertex((double) this.frustumPoints[p_228434_3_].x(), (double) this.frustumPoints[p_228434_3_].y(), (double) this.frustumPoints[p_228434_3_].z()).color((float) p_228434_6_, (float) p_228434_7_, (float) p_228434_8_, 0.25F).endVertex();
    p_228434_1_.vertex((double) this.frustumPoints[p_228434_4_].x(), (double) this.frustumPoints[p_228434_4_].y(), (double) this.frustumPoints[p_228434_4_].z()).color((float) p_228434_6_, (float) p_228434_7_, (float) p_228434_8_, 0.25F).endVertex();
    p_228434_1_.vertex((double) this.frustumPoints[p_228434_5_].x(), (double) this.frustumPoints[p_228434_5_].y(), (double) this.frustumPoints[p_228434_5_].z()).color((float) p_228434_6_, (float) p_228434_7_, (float) p_228434_8_, 0.25F).endVertex();
  }

  public void tick() {
    ++this.ticks;
    if (this.ticks % 20 == 0) {
      Iterator<DestroyBlockProgress> iterator = this.destroyingBlocks.values().iterator();

      while (iterator.hasNext()) {
        DestroyBlockProgress destroyblockprogress = iterator.next();
        int i = destroyblockprogress.getUpdatedRenderTick();
        if (this.ticks - i > 400) {
          iterator.remove();
          this.removeProgress(destroyblockprogress);
        }
      }

    }
  }

  private void removeProgress(DestroyBlockProgress p_228442_1_) {
    long i = p_228442_1_.getPos().asLong();
    Set<DestroyBlockProgress> set = this.destructionProgress.get(i);
    set.remove(p_228442_1_);
    if (set.isEmpty()) {
      this.destructionProgress.remove(i);
    }

  }

  private void renderEndSky(MatrixStack p_228444_1_) {
    RenderSystem.disableAlphaTest();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.depthMask(false);
    this.textureManager.bind(END_SKY_LOCATION);
    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder bufferbuilder = tessellator.getBuilder();

    for (int i = 0; i < 6; ++i) {
      p_228444_1_.pushPose();
      if (i == 1) {
        p_228444_1_.mulPose(Vector3f.XP.rotationDegrees(90.0F));
      }

      if (i == 2) {
        p_228444_1_.mulPose(Vector3f.XP.rotationDegrees(-90.0F));
      }

      if (i == 3) {
        p_228444_1_.mulPose(Vector3f.XP.rotationDegrees(180.0F));
      }

      if (i == 4) {
        p_228444_1_.mulPose(Vector3f.ZP.rotationDegrees(90.0F));
      }

      if (i == 5) {
        p_228444_1_.mulPose(Vector3f.ZP.rotationDegrees(-90.0F));
      }

      Matrix4f matrix4f = p_228444_1_.last().pose();
      bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
      bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, -100.0F).uv(0.0F, 0.0F).color(40, 40, 40, 255).endVertex();
      bufferbuilder.vertex(matrix4f, -100.0F, -100.0F, 100.0F).uv(0.0F, 16.0F).color(40, 40, 40, 255).endVertex();
      bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, 100.0F).uv(16.0F, 16.0F).color(40, 40, 40, 255).endVertex();
      bufferbuilder.vertex(matrix4f, 100.0F, -100.0F, -100.0F).uv(16.0F, 0.0F).color(40, 40, 40, 255).endVertex();
      tessellator.end();
      p_228444_1_.popPose();
    }

    RenderSystem.depthMask(true);
    RenderSystem.enableTexture();
    RenderSystem.disableBlend();
    RenderSystem.enableAlphaTest();
  }

  public void renderSky(MatrixStack p_228424_1_, float p_228424_2_) {
    net.minecraftforge.client.IRenderHandler renderer = this.level.getDimension().getSkyRenderer();
    if (renderer != null) {
      renderer.render(this.ticks, p_228424_2_, this.level, this.minecraft);
      return;
    }
    if (this.minecraft.level.dimension.getType() == DimensionType.THE_END) {
      this.renderEndSky(p_228424_1_);
    } else if (this.minecraft.level.dimension.isNaturalDimension()) {
      RenderSystem.disableTexture();
      Vec3d vec3d = this.level.getSkyColor(this.minecraft.gameRenderer.getMainCamera().getBlockPosition(), p_228424_2_);
      float f = (float) vec3d.x;
      float f1 = (float) vec3d.y;
      float f2 = (float) vec3d.z;
      FogRenderer.levelFogColor();
      BufferBuilder bufferbuilder = Tessellator.getInstance().getBuilder();
      RenderSystem.depthMask(false);
      RenderSystem.enableFog();
      RenderSystem.color3f(f, f1, f2);
      this.skyBuffer.bind();
      this.skyFormat.setupBufferState(0L);
      this.skyBuffer.draw(p_228424_1_.last().pose(), 7);
      VertexBuffer.unbind();
      this.skyFormat.clearBufferState();
      RenderSystem.disableFog();
      RenderSystem.disableAlphaTest();
      RenderSystem.enableBlend();
      RenderSystem.defaultBlendFunc();
      float[] afloat = this.level.dimension.getSunriseColor(this.level.getTimeOfDay(p_228424_2_), p_228424_2_);
      if (afloat != null) {
        RenderSystem.disableTexture();
        RenderSystem.shadeModel(7425);
        p_228424_1_.pushPose();
        p_228424_1_.mulPose(Vector3f.XP.rotationDegrees(90.0F));
        float f3 = MathHelper.sin(this.level.getSunAngle(p_228424_2_)) < 0.0F ? 180.0F : 0.0F;
        p_228424_1_.mulPose(Vector3f.ZP.rotationDegrees(f3));
        p_228424_1_.mulPose(Vector3f.ZP.rotationDegrees(90.0F));
        float f4 = afloat[0];
        float f5 = afloat[1];
        float f6 = afloat[2];
        Matrix4f matrix4f = p_228424_1_.last().pose();
        bufferbuilder.begin(6, DefaultVertexFormats.POSITION_COLOR);
        bufferbuilder.vertex(matrix4f, 0.0F, 100.0F, 0.0F).color(f4, f5, f6, afloat[3]).endVertex();
        int i = 16;

        for (int j = 0; j <= 16; ++j) {
          float f7 = (float) j * ((float) Math.PI * 2F) / 16.0F;
          float f8 = MathHelper.sin(f7);
          float f9 = MathHelper.cos(f7);
          bufferbuilder.vertex(matrix4f, f8 * 120.0F, f9 * 120.0F, -f9 * 40.0F * afloat[3]).color(afloat[0], afloat[1], afloat[2], 0.0F).endVertex();
        }

        bufferbuilder.end();
        WorldVertexBufferUploader.end(bufferbuilder);
        p_228424_1_.popPose();
        RenderSystem.shadeModel(7424);
      }

      RenderSystem.enableTexture();
      RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
      p_228424_1_.pushPose();
      float f11 = 1.0F - this.level.getRainLevel(p_228424_2_);
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, f11);
      p_228424_1_.mulPose(Vector3f.YP.rotationDegrees(-90.0F));
      p_228424_1_.mulPose(Vector3f.XP.rotationDegrees(this.level.getTimeOfDay(p_228424_2_) * 360.0F));
      Matrix4f matrix4f1 = p_228424_1_.last().pose();
      float f12 = 30.0F;
      this.textureManager.bind(SUN_LOCATION);
      bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
      bufferbuilder.vertex(matrix4f1, -f12, 100.0F, -f12).uv(0.0F, 0.0F).endVertex();
      bufferbuilder.vertex(matrix4f1, f12, 100.0F, -f12).uv(1.0F, 0.0F).endVertex();
      bufferbuilder.vertex(matrix4f1, f12, 100.0F, f12).uv(1.0F, 1.0F).endVertex();
      bufferbuilder.vertex(matrix4f1, -f12, 100.0F, f12).uv(0.0F, 1.0F).endVertex();
      bufferbuilder.end();
      WorldVertexBufferUploader.end(bufferbuilder);
      f12 = 20.0F;
      this.textureManager.bind(MOON_LOCATION);
      int k = this.level.getMoonPhase();
      int l = k % 4;
      int i1 = k / 4 % 2;
      float f13 = (float) (l + 0) / 4.0F;
      float f14 = (float) (i1 + 0) / 2.0F;
      float f15 = (float) (l + 1) / 4.0F;
      float f16 = (float) (i1 + 1) / 2.0F;
      bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
      bufferbuilder.vertex(matrix4f1, -f12, -100.0F, f12).uv(f15, f16).endVertex();
      bufferbuilder.vertex(matrix4f1, f12, -100.0F, f12).uv(f13, f16).endVertex();
      bufferbuilder.vertex(matrix4f1, f12, -100.0F, -f12).uv(f13, f14).endVertex();
      bufferbuilder.vertex(matrix4f1, -f12, -100.0F, -f12).uv(f15, f14).endVertex();
      bufferbuilder.end();
      WorldVertexBufferUploader.end(bufferbuilder);
      RenderSystem.disableTexture();
      float f10 = this.level.getStarBrightness(p_228424_2_) * f11;
      if (f10 > 0.0F) {
        RenderSystem.color4f(f10, f10, f10, f10);
        this.starBuffer.bind();
        this.skyFormat.setupBufferState(0L);
        this.starBuffer.draw(p_228424_1_.last().pose(), 7);
        VertexBuffer.unbind();
        this.skyFormat.clearBufferState();
      }

      RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableBlend();
      RenderSystem.enableAlphaTest();
      RenderSystem.enableFog();
      p_228424_1_.popPose();
      RenderSystem.disableTexture();
      RenderSystem.color3f(0.0F, 0.0F, 0.0F);
      double d0 = this.minecraft.player.getEyePosition(p_228424_2_).y - this.level.getHorizonHeight();
      if (d0 < 0.0D) {
        p_228424_1_.pushPose();
        p_228424_1_.translate(0.0D, 12.0D, 0.0D);
        this.darkBuffer.bind();
        this.skyFormat.setupBufferState(0L);
        this.darkBuffer.draw(p_228424_1_.last().pose(), 7);
        VertexBuffer.unbind();
        this.skyFormat.clearBufferState();
        p_228424_1_.popPose();
      }

      if (this.level.dimension.hasGround()) {
        RenderSystem.color3f(f * 0.2F + 0.04F, f1 * 0.2F + 0.04F, f2 * 0.6F + 0.1F);
      } else {
        RenderSystem.color3f(f, f1, f2);
      }

      RenderSystem.enableTexture();
      RenderSystem.depthMask(true);
      RenderSystem.disableFog();
    }
  }

  public void renderClouds(MatrixStack p_228425_1_, float p_228425_2_, double p_228425_3_, double p_228425_5_, double p_228425_7_) {
    if (this.minecraft.level.dimension.isNaturalDimension()) {
      RenderSystem.disableCull();
      RenderSystem.enableBlend();
      RenderSystem.enableAlphaTest();
      RenderSystem.enableDepthTest();
      RenderSystem.defaultAlphaFunc();
      RenderSystem.defaultBlendFunc();
      RenderSystem.enableFog();
      float f = 12.0F;
      float f1 = 4.0F;
      double d0 = 2.0E-4D;
      double d1 = (double) (((float) this.ticks + p_228425_2_) * 0.03F);
      double d2 = (p_228425_3_ + d1) / 12.0D;
      double d3 = (double) (this.level.dimension.getCloudHeight() - (float) p_228425_5_ + 0.33F);
      double d4 = p_228425_7_ / 12.0D + (double) 0.33F;
      d2 = d2 - (double) (MathHelper.floor(d2 / 2048.0D) * 2048);
      d4 = d4 - (double) (MathHelper.floor(d4 / 2048.0D) * 2048);
      float f2 = (float) (d2 - (double) MathHelper.floor(d2));
      float f3 = (float) (d3 / 4.0D - (double) MathHelper.floor(d3 / 4.0D)) * 4.0F;
      float f4 = (float) (d4 - (double) MathHelper.floor(d4));
      Vec3d vec3d = this.level.getCloudColor(p_228425_2_);
      int i = (int) Math.floor(d2);
      int j = (int) Math.floor(d3 / 4.0D);
      int k = (int) Math.floor(d4);
      if (i != this.prevCloudX || j != this.prevCloudY || k != this.prevCloudZ || this.minecraft.options.getCloudsType() != this.prevCloudsType || this.prevCloudColor.distanceToSqr(vec3d) > 2.0E-4D) {
        this.prevCloudX = i;
        this.prevCloudY = j;
        this.prevCloudZ = k;
        this.prevCloudColor = vec3d;
        this.prevCloudsType = this.minecraft.options.getCloudsType();
        this.generateClouds = true;
      }

      if (this.generateClouds) {
        this.generateClouds = false;
        BufferBuilder bufferbuilder = Tessellator.getInstance().getBuilder();
        if (this.cloudBuffer != null) {
          this.cloudBuffer.close();
        }

        this.cloudBuffer = new VertexBuffer(DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        this.buildClouds(bufferbuilder, d2, d3, d4, vec3d);
        bufferbuilder.end();
        this.cloudBuffer.upload(bufferbuilder);
      }

      this.textureManager.bind(CLOUDS_LOCATION);
      p_228425_1_.pushPose();
      p_228425_1_.scale(12.0F, 1.0F, 12.0F);
      p_228425_1_.translate((double) (-f2), (double) f3, (double) (-f4));
      if (this.cloudBuffer != null) {
        this.cloudBuffer.bind();
        DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL.setupBufferState(0L);
        int i1 = this.prevCloudsType == CloudOption.FANCY ? 0 : 1;

        for (int l = i1; l < 2; ++l) {
          if (l == 0) {
            RenderSystem.colorMask(false, false, false, false);
          } else {
            RenderSystem.colorMask(true, true, true, true);
          }

          this.cloudBuffer.draw(p_228425_1_.last().pose(), 7);
        }

        VertexBuffer.unbind();
        DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL.clearBufferState();
      }

      p_228425_1_.popPose();
      RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
      RenderSystem.disableAlphaTest();
      RenderSystem.enableCull();
      RenderSystem.disableBlend();
      RenderSystem.disableFog();
    }
  }

  private void buildClouds(BufferBuilder p_204600_1_, double p_204600_2_, double p_204600_4_, double p_204600_6_, Vec3d p_204600_8_) {
    float f = 4.0F;
    float f1 = 0.00390625F;
    int i = 8;
    int j = 4;
    float f2 = 9.765625E-4F;
    float f3 = (float) MathHelper.floor(p_204600_2_) * 0.00390625F;
    float f4 = (float) MathHelper.floor(p_204600_6_) * 0.00390625F;
    float f5 = (float) p_204600_8_.x;
    float f6 = (float) p_204600_8_.y;
    float f7 = (float) p_204600_8_.z;
    float f8 = f5 * 0.9F;
    float f9 = f6 * 0.9F;
    float f10 = f7 * 0.9F;
    float f11 = f5 * 0.7F;
    float f12 = f6 * 0.7F;
    float f13 = f7 * 0.7F;
    float f14 = f5 * 0.8F;
    float f15 = f6 * 0.8F;
    float f16 = f7 * 0.8F;
    p_204600_1_.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
    float f17 = (float) Math.floor(p_204600_4_ / 4.0D) * 4.0F;
    if (this.prevCloudsType == CloudOption.FANCY) {
      for (int k = -3; k <= 4; ++k) {
        for (int l = -3; l <= 4; ++l) {
          float f18 = (float) (k * 8);
          float f19 = (float) (l * 8);
          if (f17 > -5.0F) {
            p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + 8.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f11, f12, f13, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 0.0F), (double) (f19 + 8.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f11, f12, f13, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 0.0F), (double) (f19 + 0.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f11, f12, f13, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + 0.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f11, f12, f13, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
          }

          if (f17 <= 5.0F) {
            p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 4.0F - 9.765625E-4F), (double) (f19 + 8.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 4.0F - 9.765625E-4F), (double) (f19 + 8.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 4.0F - 9.765625E-4F), (double) (f19 + 0.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
            p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 4.0F - 9.765625E-4F), (double) (f19 + 0.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, 1.0F, 0.0F).endVertex();
          }

          if (k > -1) {
            for (int i1 = 0; i1 < 8; ++i1) {
              p_204600_1_.vertex((double) (f18 + (float) i1 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + 8.0F)).uv((f18 + (float) i1 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) i1 + 0.0F), (double) (f17 + 4.0F), (double) (f19 + 8.0F)).uv((f18 + (float) i1 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) i1 + 0.0F), (double) (f17 + 4.0F), (double) (f19 + 0.0F)).uv((f18 + (float) i1 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) i1 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + 0.0F)).uv((f18 + (float) i1 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(-1.0F, 0.0F, 0.0F).endVertex();
            }
          }

          if (k <= 1) {
            for (int j2 = 0; j2 < 8; ++j2) {
              p_204600_1_.vertex((double) (f18 + (float) j2 + 1.0F - 9.765625E-4F), (double) (f17 + 0.0F), (double) (f19 + 8.0F)).uv((f18 + (float) j2 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) j2 + 1.0F - 9.765625E-4F), (double) (f17 + 4.0F), (double) (f19 + 8.0F)).uv((f18 + (float) j2 + 0.5F) * 0.00390625F + f3, (f19 + 8.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) j2 + 1.0F - 9.765625E-4F), (double) (f17 + 4.0F), (double) (f19 + 0.0F)).uv((f18 + (float) j2 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + (float) j2 + 1.0F - 9.765625E-4F), (double) (f17 + 0.0F), (double) (f19 + 0.0F)).uv((f18 + (float) j2 + 0.5F) * 0.00390625F + f3, (f19 + 0.0F) * 0.00390625F + f4).color(f8, f9, f10, 0.8F).normal(1.0F, 0.0F, 0.0F).endVertex();
            }
          }

          if (l > -1) {
            for (int k2 = 0; k2 < 8; ++k2) {
              p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 4.0F), (double) (f19 + (float) k2 + 0.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float) k2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 4.0F), (double) (f19 + (float) k2 + 0.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float) k2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 0.0F), (double) (f19 + (float) k2 + 0.0F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float) k2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + (float) k2 + 0.0F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float) k2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, -1.0F).endVertex();
            }
          }

          if (l <= 1) {
            for (int l2 = 0; l2 < 8; ++l2) {
              p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 4.0F), (double) (f19 + (float) l2 + 1.0F - 9.765625E-4F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float) l2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 4.0F), (double) (f19 + (float) l2 + 1.0F - 9.765625E-4F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float) l2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 8.0F), (double) (f17 + 0.0F), (double) (f19 + (float) l2 + 1.0F - 9.765625E-4F)).uv((f18 + 8.0F) * 0.00390625F + f3, (f19 + (float) l2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
              p_204600_1_.vertex((double) (f18 + 0.0F), (double) (f17 + 0.0F), (double) (f19 + (float) l2 + 1.0F - 9.765625E-4F)).uv((f18 + 0.0F) * 0.00390625F + f3, (f19 + (float) l2 + 0.5F) * 0.00390625F + f4).color(f14, f15, f16, 0.8F).normal(0.0F, 0.0F, 1.0F).endVertex();
            }
          }
        }
      }
    } else {
      int j1 = 1;
      int k1 = 32;

      for (int l1 = -32; l1 < 32; l1 += 32) {
        for (int i2 = -32; i2 < 32; i2 += 32) {
          p_204600_1_.vertex((double) (l1 + 0), (double) f17, (double) (i2 + 32)).uv((float) (l1 + 0) * 0.00390625F + f3, (float) (i2 + 32) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
          p_204600_1_.vertex((double) (l1 + 32), (double) f17, (double) (i2 + 32)).uv((float) (l1 + 32) * 0.00390625F + f3, (float) (i2 + 32) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
          p_204600_1_.vertex((double) (l1 + 32), (double) f17, (double) (i2 + 0)).uv((float) (l1 + 32) * 0.00390625F + f3, (float) (i2 + 0) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
          p_204600_1_.vertex((double) (l1 + 0), (double) f17, (double) (i2 + 0)).uv((float) (l1 + 0) * 0.00390625F + f3, (float) (i2 + 0) * 0.00390625F + f4).color(f5, f6, f7, 0.8F).normal(0.0F, -1.0F, 0.0F).endVertex();
        }
      }
    }

  }

  private void compileChunksUntil(long p_174967_1_) {
    this.needsUpdate |= this.chunkRenderDispatcher.uploadAllPendingUploads();
    long i = Util.getNanos();
    int j = 0;
    if (!this.chunksToCompile.isEmpty()) {
      Iterator<ChunkRenderDispatcher.ChunkRender> iterator = this.chunksToCompile.iterator();

      while (iterator.hasNext()) {
        ChunkRenderDispatcher.ChunkRender chunkrenderdispatcher$chunkrender = iterator.next();
        if (chunkrenderdispatcher$chunkrender.isDirtyFromPlayer()) {
          this.chunkRenderDispatcher.rebuildChunkSync(chunkrenderdispatcher$chunkrender);
        } else {
          chunkrenderdispatcher$chunkrender.rebuildChunkAsync(this.chunkRenderDispatcher);
        }

        chunkrenderdispatcher$chunkrender.setNotDirty();
        iterator.remove();
        ++j;
        long k = Util.getNanos();
        long l = k - i;
        long i1 = l / (long) j;
        long j1 = p_174967_1_ - k;
        if (j1 < i1) {
          break;
        }
      }
    }

  }

  private void renderWorldBounds(ActiveRenderInfo p_228447_1_) {
    BufferBuilder bufferbuilder = Tessellator.getInstance().getBuilder();
    WorldBorder worldborder = this.level.getWorldBorder();
    double d0 = (double) (this.minecraft.options.renderDistance * 16);
    if (!(p_228447_1_.getPosition().x < worldborder.getMaxX() - d0) || !(p_228447_1_.getPosition().x > worldborder.getMinX() + d0) || !(p_228447_1_.getPosition().z < worldborder.getMaxZ() - d0) || !(p_228447_1_.getPosition().z > worldborder.getMinZ() + d0)) {
      double d1 = 1.0D - worldborder.getDistanceToBorder(p_228447_1_.getPosition().x, p_228447_1_.getPosition().z) / d0;
      d1 = Math.pow(d1, 4.0D);
      double d2 = p_228447_1_.getPosition().x;
      double d3 = p_228447_1_.getPosition().y;
      double d4 = p_228447_1_.getPosition().z;
      RenderSystem.enableBlend();
      RenderSystem.enableDepthTest();
      RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
      this.textureManager.bind(FORCEFIELD_LOCATION);
      RenderSystem.depthMask(false);
      RenderSystem.pushMatrix();
      int i = worldborder.getStatus().getColor();
      float f = (float) (i >> 16 & 255) / 255.0F;
      float f1 = (float) (i >> 8 & 255) / 255.0F;
      float f2 = (float) (i & 255) / 255.0F;
      RenderSystem.color4f(f, f1, f2, (float) d1);
      RenderSystem.polygonOffset(-3.0F, -3.0F);
      RenderSystem.enablePolygonOffset();
      RenderSystem.defaultAlphaFunc();
      RenderSystem.enableAlphaTest();
      RenderSystem.disableCull();
      float f3 = (float) (Util.getMillis() % 3000L) / 3000.0F;
      float f4 = 0.0F;
      float f5 = 0.0F;
      float f6 = 128.0F;
      bufferbuilder.begin(7, DefaultVertexFormats.POSITION_TEX);
      double d5 = Math.max((double) MathHelper.floor(d4 - d0), worldborder.getMinZ());
      double d6 = Math.min((double) MathHelper.ceil(d4 + d0), worldborder.getMaxZ());
      if (d2 > worldborder.getMaxX() - d0) {
        float f7 = 0.0F;

        for (double d7 = d5; d7 < d6; f7 += 0.5F) {
          double d8 = Math.min(1.0D, d6 - d7);
          float f8 = (float) d8 * 0.5F;
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMaxX(), 256, d7, f3 + f7, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMaxX(), 256, d7 + d8, f3 + f8 + f7, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMaxX(), 0, d7 + d8, f3 + f8 + f7, f3 + 128.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMaxX(), 0, d7, f3 + f7, f3 + 128.0F);
          ++d7;
        }
      }

      if (d2 < worldborder.getMinX() + d0) {
        float f9 = 0.0F;

        for (double d9 = d5; d9 < d6; f9 += 0.5F) {
          double d12 = Math.min(1.0D, d6 - d9);
          float f12 = (float) d12 * 0.5F;
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMinX(), 256, d9, f3 + f9, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMinX(), 256, d9 + d12, f3 + f12 + f9, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMinX(), 0, d9 + d12, f3 + f12 + f9, f3 + 128.0F);
          this.vertex(bufferbuilder, d2, d3, d4, worldborder.getMinX(), 0, d9, f3 + f9, f3 + 128.0F);
          ++d9;
        }
      }

      d5 = Math.max((double) MathHelper.floor(d2 - d0), worldborder.getMinX());
      d6 = Math.min((double) MathHelper.ceil(d2 + d0), worldborder.getMaxX());
      if (d4 > worldborder.getMaxZ() - d0) {
        float f10 = 0.0F;

        for (double d10 = d5; d10 < d6; f10 += 0.5F) {
          double d13 = Math.min(1.0D, d6 - d10);
          float f13 = (float) d13 * 0.5F;
          this.vertex(bufferbuilder, d2, d3, d4, d10, 256, worldborder.getMaxZ(), f3 + f10, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d10 + d13, 256, worldborder.getMaxZ(), f3 + f13 + f10, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d10 + d13, 0, worldborder.getMaxZ(), f3 + f13 + f10, f3 + 128.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d10, 0, worldborder.getMaxZ(), f3 + f10, f3 + 128.0F);
          ++d10;
        }
      }

      if (d4 < worldborder.getMinZ() + d0) {
        float f11 = 0.0F;

        for (double d11 = d5; d11 < d6; f11 += 0.5F) {
          double d14 = Math.min(1.0D, d6 - d11);
          float f14 = (float) d14 * 0.5F;
          this.vertex(bufferbuilder, d2, d3, d4, d11, 256, worldborder.getMinZ(), f3 + f11, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d11 + d14, 256, worldborder.getMinZ(), f3 + f14 + f11, f3 + 0.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d11 + d14, 0, worldborder.getMinZ(), f3 + f14 + f11, f3 + 128.0F);
          this.vertex(bufferbuilder, d2, d3, d4, d11, 0, worldborder.getMinZ(), f3 + f11, f3 + 128.0F);
          ++d11;
        }
      }

      bufferbuilder.end();
      WorldVertexBufferUploader.end(bufferbuilder);
      RenderSystem.enableCull();
      RenderSystem.disableAlphaTest();
      RenderSystem.polygonOffset(0.0F, 0.0F);
      RenderSystem.disablePolygonOffset();
      RenderSystem.enableAlphaTest();
      RenderSystem.disableBlend();
      RenderSystem.popMatrix();
      RenderSystem.depthMask(true);
    }
  }

  private void vertex(BufferBuilder p_228422_1_, double p_228422_2_, double p_228422_4_, double p_228422_6_, double p_228422_8_, int p_228422_10_, double p_228422_11_, float p_228422_13_, float p_228422_14_) {
    p_228422_1_.vertex(p_228422_8_ - p_228422_2_, (double) p_228422_10_ - p_228422_4_, p_228422_11_ - p_228422_6_).uv(p_228422_13_, p_228422_14_).endVertex();
  }

  private void renderHitOutline(MatrixStack p_228429_1_, IVertexBuilder p_228429_2_, Entity entity, double x, double y, double z, BlockPos blockPos, BlockState state) {
    renderShape(p_228429_1_, p_228429_2_, state.getShape(this.level, blockPos, ISelectionContext.of(entity)), blockPos.getX() - x, blockPos.getY() - y,blockPos.getZ() - z, 0.0F, 0.0F, 0.0F, 0.4F);
  }



  private static void renderShape(MatrixStack p_228445_0_, IVertexBuilder p_228445_1_, VoxelShape p_228445_2_, double p_228445_3_, double p_228445_5_, double p_228445_7_, float p_228445_9_, float p_228445_10_, float p_228445_11_, float p_228445_12_) {
    Matrix4f matrix4f = p_228445_0_.last().pose();
    p_228445_2_.forAllEdges((p_230013_12_, p_230013_14_, p_230013_16_, p_230013_18_, p_230013_20_, p_230013_22_) -> {
      p_228445_1_.vertex(matrix4f, (float) (p_230013_12_ + p_228445_3_), (float) (p_230013_14_ + p_228445_5_), (float) (p_230013_16_ + p_228445_7_)).color(p_228445_9_, p_228445_10_, p_228445_11_, p_228445_12_).endVertex();
      p_228445_1_.vertex(matrix4f, (float) (p_230013_18_ + p_228445_3_), (float) (p_230013_20_ + p_228445_5_), (float) (p_230013_22_ + p_228445_7_)).color(p_228445_9_, p_228445_10_, p_228445_11_, p_228445_12_).endVertex();
    });
  }

  public void blockChanged(IBlockReader p_184376_1_, BlockPos p_184376_2_, BlockState p_184376_3_, BlockState p_184376_4_, int p_184376_5_) {
    this.setBlockDirty(p_184376_2_, (p_184376_5_ & 8) != 0);
  }

  private void setBlockDirty(BlockPos p_215324_1_, boolean p_215324_2_) {
    for (int i = p_215324_1_.getZ() - 1; i <= p_215324_1_.getZ() + 1; ++i) {
      for (int j = p_215324_1_.getX() - 1; j <= p_215324_1_.getX() + 1; ++j) {
        for (int k = p_215324_1_.getY() - 1; k <= p_215324_1_.getY() + 1; ++k) {
          this.setSectionDirty(j >> 4, k >> 4, i >> 4, p_215324_2_);
        }
      }
    }

  }

  public void setBlocksDirty(int p_147585_1_, int p_147585_2_, int p_147585_3_, int p_147585_4_, int p_147585_5_, int p_147585_6_) {
    for (int i = p_147585_3_ - 1; i <= p_147585_6_ + 1; ++i) {
      for (int j = p_147585_1_ - 1; j <= p_147585_4_ + 1; ++j) {
        for (int k = p_147585_2_ - 1; k <= p_147585_5_ + 1; ++k) {
          this.setSectionDirty(j >> 4, k >> 4, i >> 4);
        }
      }
    }

  }

  public void setBlockDirty(BlockPos p_224746_1_, BlockState p_224746_2_, BlockState p_224746_3_) {
    if (this.minecraft.getModelManager().requiresRender(p_224746_2_, p_224746_3_)) {
      this.setBlocksDirty(p_224746_1_.getX(), p_224746_1_.getY(), p_224746_1_.getZ(), p_224746_1_.getX(), p_224746_1_.getY(), p_224746_1_.getZ());
    }

  }

  public void setSectionDirtyWithNeighbors(int p_215321_1_, int p_215321_2_, int p_215321_3_) {
    for (int i = p_215321_3_ - 1; i <= p_215321_3_ + 1; ++i) {
      for (int j = p_215321_1_ - 1; j <= p_215321_1_ + 1; ++j) {
        for (int k = p_215321_2_ - 1; k <= p_215321_2_ + 1; ++k) {
          this.setSectionDirty(j, k, i);
        }
      }
    }

  }

  public void setSectionDirty(int p_215328_1_, int p_215328_2_, int p_215328_3_) {
    this.setSectionDirty(p_215328_1_, p_215328_2_, p_215328_3_, false);
  }

  private void setSectionDirty(int p_215319_1_, int p_215319_2_, int p_215319_3_, boolean p_215319_4_) {
    this.viewArea.setDirty(p_215319_1_, p_215319_2_, p_215319_3_, p_215319_4_);
  }

  public void playStreamingMusic(@Nullable SoundEvent p_184377_1_, BlockPos p_184377_2_) {
    ISound isound = this.playingRecords.get(p_184377_2_);
    if (isound != null) {
      this.minecraft.getSoundManager().stop(isound);
      this.playingRecords.remove(p_184377_2_);
    }

    if (p_184377_1_ != null) {
      MusicDiscItem musicdiscitem = MusicDiscItem.getBySound(p_184377_1_);
      if (musicdiscitem != null) {
        this.minecraft.gui.setNowPlaying(musicdiscitem.getDisplayName().getColoredString());
      }

      ISound simplesound = SimpleSound.forRecord(p_184377_1_, (float) p_184377_2_.getX(), (float) p_184377_2_.getY(), (float) p_184377_2_.getZ());
      this.playingRecords.put(p_184377_2_, simplesound);
      this.minecraft.getSoundManager().play(simplesound);
    }

    this.notifyNearbyEntities(this.level, p_184377_2_, p_184377_1_ != null);
  }

  private void notifyNearbyEntities(World p_193054_1_, BlockPos p_193054_2_, boolean p_193054_3_) {
    for (LivingEntity livingentity : p_193054_1_.getEntitiesOfClass(LivingEntity.class, (new AxisAlignedBB(p_193054_2_)).inflate(3.0D))) {
      livingentity.setRecordPlayingNearby(p_193054_2_, p_193054_3_);
    }

  }

  public void addParticle(IParticleData p_195461_1_, boolean p_195461_2_, double p_195461_3_, double p_195461_5_, double p_195461_7_, double p_195461_9_, double p_195461_11_, double p_195461_13_) {
    this.addParticle(p_195461_1_, p_195461_2_, false, p_195461_3_, p_195461_5_, p_195461_7_, p_195461_9_, p_195461_11_, p_195461_13_);
  }

  public void addParticle(IParticleData p_195462_1_, boolean p_195462_2_, boolean p_195462_3_, double p_195462_4_, double p_195462_6_, double p_195462_8_, double p_195462_10_, double p_195462_12_, double p_195462_14_) {
    try {
      this.addParticleInternal(p_195462_1_, p_195462_2_, p_195462_3_, p_195462_4_, p_195462_6_, p_195462_8_, p_195462_10_, p_195462_12_, p_195462_14_);
    } catch (Throwable throwable) {
      CrashReport crashreport = CrashReport.forThrowable(throwable, "Exception while adding particle");
      CrashReportCategory crashreportcategory = crashreport.addCategory("Particle being added");
      crashreportcategory.setDetail("ID", Registry.PARTICLE_TYPE.getKey(p_195462_1_.getType()));
      crashreportcategory.setDetail("Parameters", p_195462_1_.writeToString());
      crashreportcategory.setDetail("Position", () -> {
        return CrashReportCategory.formatLocation(p_195462_4_, p_195462_6_, p_195462_8_);
      });
      throw new ReportedException(crashreport);
    }
  }

  private <T extends IParticleData> void addParticle(T p_195467_1_, double p_195467_2_, double p_195467_4_, double p_195467_6_, double p_195467_8_, double p_195467_10_, double p_195467_12_) {
    this.addParticle(p_195467_1_, p_195467_1_.getType().getOverrideLimiter(), p_195467_2_, p_195467_4_, p_195467_6_, p_195467_8_, p_195467_10_, p_195467_12_);
  }

  @Nullable
  private Particle addParticleInternal(IParticleData p_195471_1_, boolean p_195471_2_, double p_195471_3_, double p_195471_5_, double p_195471_7_, double p_195471_9_, double p_195471_11_, double p_195471_13_) {
    return this.addParticleInternal(p_195471_1_, p_195471_2_, false, p_195471_3_, p_195471_5_, p_195471_7_, p_195471_9_, p_195471_11_, p_195471_13_);
  }

  @Nullable
  private Particle addParticleInternal(IParticleData p_195469_1_, boolean p_195469_2_, boolean p_195469_3_, double p_195469_4_, double p_195469_6_, double p_195469_8_, double p_195469_10_, double p_195469_12_, double p_195469_14_) {
    ActiveRenderInfo activerenderinfo = this.minecraft.gameRenderer.getMainCamera();
    if (this.minecraft != null && activerenderinfo.isInitialized() && this.minecraft.particleEngine != null) {
      ParticleStatus particlestatus = this.calculateParticleLevel(p_195469_3_);
      if (p_195469_2_) {
        return this.minecraft.particleEngine.createParticle(p_195469_1_, p_195469_4_, p_195469_6_, p_195469_8_, p_195469_10_, p_195469_12_, p_195469_14_);
      } else if (activerenderinfo.getPosition().distanceToSqr(p_195469_4_, p_195469_6_, p_195469_8_) > 1024.0D) {
        return null;
      } else {
        return particlestatus == ParticleStatus.MINIMAL ? null : this.minecraft.particleEngine.createParticle(p_195469_1_, p_195469_4_, p_195469_6_, p_195469_8_, p_195469_10_, p_195469_12_, p_195469_14_);
      }
    } else {
      return null;
    }
  }

  private ParticleStatus calculateParticleLevel(boolean p_215327_1_) {
    ParticleStatus particlestatus = this.minecraft.options.particles;
    if (p_215327_1_ && particlestatus == ParticleStatus.MINIMAL && this.level.random.nextInt(10) == 0) {
      particlestatus = ParticleStatus.DECREASED;
    }

    if (particlestatus == ParticleStatus.DECREASED && this.level.random.nextInt(3) == 0) {
      particlestatus = ParticleStatus.MINIMAL;
    }

    return particlestatus;
  }

  public void clear() {
  }

  public void globalLevelEvent(int p_180440_1_, BlockPos p_180440_2_, int p_180440_3_) {
    switch (p_180440_1_) {
      case 1023:
      case 1028:
      case 1038:
        ActiveRenderInfo activerenderinfo = this.minecraft.gameRenderer.getMainCamera();
        if (activerenderinfo.isInitialized()) {
          double d0 = (double) p_180440_2_.getX() - activerenderinfo.getPosition().x;
          double d1 = (double) p_180440_2_.getY() - activerenderinfo.getPosition().y;
          double d2 = (double) p_180440_2_.getZ() - activerenderinfo.getPosition().z;
          double d3 = Math.sqrt(d0 * d0 + d1 * d1 + d2 * d2);
          double d4 = activerenderinfo.getPosition().x;
          double d5 = activerenderinfo.getPosition().y;
          double d6 = activerenderinfo.getPosition().z;
          if (d3 > 0.0D) {
            d4 += d0 / d3 * 2.0D;
            d5 += d1 / d3 * 2.0D;
            d6 += d2 / d3 * 2.0D;
          }

          if (p_180440_1_ == 1023) {
            this.level.playLocalSound(d4, d5, d6, SoundEvents.WITHER_SPAWN, SoundCategory.HOSTILE, 1.0F, 1.0F, false);
          } else if (p_180440_1_ == 1038) {
            this.level.playLocalSound(d4, d5, d6, SoundEvents.END_PORTAL_SPAWN, SoundCategory.HOSTILE, 1.0F, 1.0F, false);
          } else {
            this.level.playLocalSound(d4, d5, d6, SoundEvents.ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 5.0F, 1.0F, false);
          }
        }
      default:
    }
  }

  public void levelEvent(PlayerEntity p_180439_1_, int p_180439_2_, BlockPos p_180439_3_, int p_180439_4_) {
    Random random = this.level.random;
    switch (p_180439_2_) {
      case 1000:
        this.level.playLocalSound(p_180439_3_, SoundEvents.DISPENSER_DISPENSE, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
        break;
      case 1001:
        this.level.playLocalSound(p_180439_3_, SoundEvents.DISPENSER_FAIL, SoundCategory.BLOCKS, 1.0F, 1.2F, false);
        break;
      case 1002:
        this.level.playLocalSound(p_180439_3_, SoundEvents.DISPENSER_LAUNCH, SoundCategory.BLOCKS, 1.0F, 1.2F, false);
        break;
      case 1003:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ENDER_EYE_LAUNCH, SoundCategory.NEUTRAL, 1.0F, 1.2F, false);
        break;
      case 1004:
        this.level.playLocalSound(p_180439_3_, SoundEvents.FIREWORK_ROCKET_SHOOT, SoundCategory.NEUTRAL, 1.0F, 1.2F, false);
        break;
      case 1005:
        this.level.playLocalSound(p_180439_3_, SoundEvents.IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1006:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1007:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1008:
        this.level.playLocalSound(p_180439_3_, SoundEvents.FENCE_GATE_OPEN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1009:
        this.level.playLocalSound(p_180439_3_, SoundEvents.FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F + (random.nextFloat() - random.nextFloat()) * 0.8F, false);
        break;
      case 1010:
        if (Item.byId(p_180439_4_) instanceof MusicDiscItem) {
          this.playStreamingMusic(((MusicDiscItem) Item.byId(p_180439_4_)).getSound(), p_180439_3_);
        } else {
          this.playStreamingMusic((SoundEvent) null, p_180439_3_);
        }
        break;
      case 1011:
        this.level.playLocalSound(p_180439_3_, SoundEvents.IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1012:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1013:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1014:
        this.level.playLocalSound(p_180439_3_, SoundEvents.FENCE_GATE_CLOSE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1015:
        this.level.playLocalSound(p_180439_3_, SoundEvents.GHAST_WARN, SoundCategory.HOSTILE, 10.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1016:
        this.level.playLocalSound(p_180439_3_, SoundEvents.GHAST_SHOOT, SoundCategory.HOSTILE, 10.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1017:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ENDER_DRAGON_SHOOT, SoundCategory.HOSTILE, 10.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1018:
        this.level.playLocalSound(p_180439_3_, SoundEvents.BLAZE_SHOOT, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1019:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1020:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1021:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1022:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WITHER_BREAK_BLOCK, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1024:
        this.level.playLocalSound(p_180439_3_, SoundEvents.WITHER_SHOOT, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1025:
        this.level.playLocalSound(p_180439_3_, SoundEvents.BAT_TAKEOFF, SoundCategory.NEUTRAL, 0.05F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1026:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_INFECT, SoundCategory.HOSTILE, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1027:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundCategory.NEUTRAL, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1029:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ANVIL_DESTROY, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1030:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ANVIL_USE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1031:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ANVIL_LAND, SoundCategory.BLOCKS, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1032:
        this.minecraft.getSoundManager().play(SimpleSound.forUI(SoundEvents.PORTAL_TRAVEL, random.nextFloat() * 0.4F + 0.8F));
        break;
      case 1033:
        this.level.playLocalSound(p_180439_3_, SoundEvents.CHORUS_FLOWER_GROW, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
        break;
      case 1034:
        this.level.playLocalSound(p_180439_3_, SoundEvents.CHORUS_FLOWER_DEATH, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
        break;
      case 1035:
        this.level.playLocalSound(p_180439_3_, SoundEvents.BREWING_STAND_BREW, SoundCategory.BLOCKS, 1.0F, 1.0F, false);
        break;
      case 1036:
        this.level.playLocalSound(p_180439_3_, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1037:
        this.level.playLocalSound(p_180439_3_, SoundEvents.IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1039:
        this.level.playLocalSound(p_180439_3_, SoundEvents.PHANTOM_BITE, SoundCategory.HOSTILE, 0.3F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1040:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ZOMBIE_CONVERTED_TO_DROWNED, SoundCategory.NEUTRAL, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1041:
        this.level.playLocalSound(p_180439_3_, SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, SoundCategory.NEUTRAL, 2.0F, (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F, false);
        break;
      case 1042:
        this.level.playLocalSound(p_180439_3_, SoundEvents.GRINDSTONE_USE, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1043:
        this.level.playLocalSound(p_180439_3_, SoundEvents.BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 1500:
        ComposterBlock.handleFill(this.level, p_180439_3_, p_180439_4_ > 0);
        break;
      case 1501:
        this.level.playLocalSound(p_180439_3_, SoundEvents.LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.5F, 2.6F + (this.level.getRandom().nextFloat() - this.level.getRandom().nextFloat()) * 0.8F, false);

        for (int k1 = 0; k1 < 8; ++k1) {
          this.level.addParticle(ParticleTypes.LARGE_SMOKE, (double) p_180439_3_.getX() + Math.random(), (double) p_180439_3_.getY() + 1.2D, (double) p_180439_3_.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
        }
        break;
      case 1502:
        this.level.playLocalSound(p_180439_3_, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundCategory.BLOCKS, 0.5F, 2.6F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.8F, false);

        for (int j1 = 0; j1 < 5; ++j1) {
          double d16 = (double) p_180439_3_.getX() + random.nextDouble() * 0.6D + 0.2D;
          double d22 = (double) p_180439_3_.getY() + random.nextDouble() * 0.6D + 0.2D;
          double d27 = (double) p_180439_3_.getZ() + random.nextDouble() * 0.6D + 0.2D;
          this.level.addParticle(ParticleTypes.SMOKE, d16, d22, d27, 0.0D, 0.0D, 0.0D);
        }
        break;
      case 1503:
        this.level.playLocalSound(p_180439_3_, SoundEvents.END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0F, 1.0F, false);

        for (int i1 = 0; i1 < 16; ++i1) {
          double d15 = (double) ((float) p_180439_3_.getX() + (5.0F + random.nextFloat() * 6.0F) / 16.0F);
          double d21 = (double) ((float) p_180439_3_.getY() + 0.8125F);
          double d26 = (double) ((float) p_180439_3_.getZ() + (5.0F + random.nextFloat() * 6.0F) / 16.0F);
          double d29 = 0.0D;
          double d30 = 0.0D;
          double d5 = 0.0D;
          this.level.addParticle(ParticleTypes.SMOKE, d15, d21, d26, 0.0D, 0.0D, 0.0D);
        }
        break;
      case 2000:
        Direction direction = Direction.from3DDataValue(p_180439_4_);
        int l = direction.getStepX();
        int l1 = direction.getStepY();
        int i2 = direction.getStepZ();
        double d20 = (double) p_180439_3_.getX() + (double) l * 0.6D + 0.5D;
        double d25 = (double) p_180439_3_.getY() + (double) l1 * 0.6D + 0.5D;
        double d28 = (double) p_180439_3_.getZ() + (double) i2 * 0.6D + 0.5D;

        for (int i3 = 0; i3 < 10; ++i3) {
          double d31 = random.nextDouble() * 0.2D + 0.01D;
          double d32 = d20 + (double) l * 0.01D + (random.nextDouble() - 0.5D) * (double) i2 * 0.5D;
          double d33 = d25 + (double) l1 * 0.01D + (random.nextDouble() - 0.5D) * (double) l1 * 0.5D;
          double d34 = d28 + (double) i2 * 0.01D + (random.nextDouble() - 0.5D) * (double) l * 0.5D;
          double d35 = (double) l * d31 + random.nextGaussian() * 0.01D;
          double d36 = (double) l1 * d31 + random.nextGaussian() * 0.01D;
          double d10 = (double) i2 * d31 + random.nextGaussian() * 0.01D;
          this.addParticle(ParticleTypes.SMOKE, d32, d33, d34, d35, d36, d10);
        }
        break;
      case 2001:
        BlockState blockstate = Block.stateById(p_180439_4_);
        if (!blockstate.isAir(this.level, p_180439_3_)) {
          SoundType soundtype = blockstate.getSoundType(this.level, p_180439_3_, null);
          this.level.playLocalSound(p_180439_3_, soundtype.getBreakSound(), SoundCategory.BLOCKS, (soundtype.getVolume() + 1.0F) / 2.0F, soundtype.getPitch() * 0.8F, false);
        }

        this.minecraft.particleEngine.destroy(p_180439_3_, blockstate);
        break;
      case 2002:
      case 2007:
        double d11 = (double) p_180439_3_.getX();
        double d14 = (double) p_180439_3_.getY();
        double d19 = (double) p_180439_3_.getZ();

        for (int k2 = 0; k2 < 8; ++k2) {
          this.addParticle(new ItemParticleData(ParticleTypes.ITEM, new ItemStack(Items.SPLASH_POTION)), d11, d14, d19, random.nextGaussian() * 0.15D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.15D);
        }

        float f4 = (float) (p_180439_4_ >> 16 & 255) / 255.0F;
        float f2 = (float) (p_180439_4_ >> 8 & 255) / 255.0F;
        float f5 = (float) (p_180439_4_ >> 0 & 255) / 255.0F;
        IParticleData iparticledata = p_180439_2_ == 2007 ? ParticleTypes.INSTANT_EFFECT : ParticleTypes.EFFECT;

        for (int l2 = 0; l2 < 100; ++l2) {
          double d4 = random.nextDouble() * 4.0D;
          double d6 = random.nextDouble() * Math.PI * 2.0D;
          double d7 = Math.cos(d6) * d4;
          double d8 = 0.01D + random.nextDouble() * 0.5D;
          double d9 = Math.sin(d6) * d4;
          Particle particle1 = this.addParticleInternal(iparticledata, iparticledata.getType().getOverrideLimiter(), d11 + d7 * 0.1D, d14 + 0.3D, d19 + d9 * 0.1D, d7, d8, d9);
          if (particle1 != null) {
            float f3 = 0.75F + random.nextFloat() * 0.25F;
            particle1.setColor(f4 * f3, f2 * f3, f5 * f3);
            particle1.setPower((float) d4);
          }
        }

        this.level.playLocalSound(p_180439_3_, SoundEvents.SPLASH_POTION_BREAK, SoundCategory.NEUTRAL, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 2003:
        double d0 = (double) p_180439_3_.getX() + 0.5D;
        double d13 = (double) p_180439_3_.getY();
        double d18 = (double) p_180439_3_.getZ() + 0.5D;

        for (int j2 = 0; j2 < 8; ++j2) {
          this.addParticle(new ItemParticleData(ParticleTypes.ITEM, new ItemStack(Items.ENDER_EYE)), d0, d13, d18, random.nextGaussian() * 0.15D, random.nextDouble() * 0.2D, random.nextGaussian() * 0.15D);
        }

        for (double d24 = 0.0D; d24 < (Math.PI * 2D); d24 += 0.15707963267948966D) {
          this.addParticle(ParticleTypes.PORTAL, d0 + Math.cos(d24) * 5.0D, d13 - 0.4D, d18 + Math.sin(d24) * 5.0D, Math.cos(d24) * -5.0D, 0.0D, Math.sin(d24) * -5.0D);
          this.addParticle(ParticleTypes.PORTAL, d0 + Math.cos(d24) * 5.0D, d13 - 0.4D, d18 + Math.sin(d24) * 5.0D, Math.cos(d24) * -7.0D, 0.0D, Math.sin(d24) * -7.0D);
        }
        break;
      case 2004:
        for (int k = 0; k < 20; ++k) {
          double d12 = (double) p_180439_3_.getX() + 0.5D + ((double) this.level.random.nextFloat() - 0.5D) * 2.0D;
          double d17 = (double) p_180439_3_.getY() + 0.5D + ((double) this.level.random.nextFloat() - 0.5D) * 2.0D;
          double d23 = (double) p_180439_3_.getZ() + 0.5D + ((double) this.level.random.nextFloat() - 0.5D) * 2.0D;
          this.level.addParticle(ParticleTypes.SMOKE, d12, d17, d23, 0.0D, 0.0D, 0.0D);
          this.level.addParticle(ParticleTypes.FLAME, d12, d17, d23, 0.0D, 0.0D, 0.0D);
        }
        break;
      case 2005:
        BoneMealItem.addGrowthParticles(this.level, p_180439_3_, p_180439_4_);
        break;
      case 2006:
        for (int j = 0; j < 200; ++j) {
          float f = random.nextFloat() * 4.0F;
          float f1 = random.nextFloat() * ((float) Math.PI * 2F);
          double d1 = (double) (MathHelper.cos(f1) * f);
          double d2 = 0.01D + random.nextDouble() * 0.5D;
          double d3 = (double) (MathHelper.sin(f1) * f);
          Particle particle = this.addParticleInternal(ParticleTypes.DRAGON_BREATH, false, (double) p_180439_3_.getX() + d1 * 0.1D, (double) p_180439_3_.getY() + 0.3D, (double) p_180439_3_.getZ() + d3 * 0.1D, d1, d2, d3);
          if (particle != null) {
            particle.setPower(f);
          }
        }

        this.level.playLocalSound(p_180439_3_, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundCategory.HOSTILE, 1.0F, this.level.random.nextFloat() * 0.1F + 0.9F, false);
        break;
      case 2008:
        this.level.addParticle(ParticleTypes.EXPLOSION, (double) p_180439_3_.getX() + 0.5D, (double) p_180439_3_.getY() + 0.5D, (double) p_180439_3_.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
        break;
      case 2009:
        for (int i = 0; i < 8; ++i) {
          this.level.addParticle(ParticleTypes.CLOUD, (double) p_180439_3_.getX() + Math.random(), (double) p_180439_3_.getY() + 1.2D, (double) p_180439_3_.getZ() + Math.random(), 0.0D, 0.0D, 0.0D);
        }
        break;
      case 3000:
        this.level.addParticle(ParticleTypes.EXPLOSION_EMITTER, true, (double) p_180439_3_.getX() + 0.5D, (double) p_180439_3_.getY() + 0.5D, (double) p_180439_3_.getZ() + 0.5D, 0.0D, 0.0D, 0.0D);
        this.level.playLocalSound(p_180439_3_, SoundEvents.END_GATEWAY_SPAWN, SoundCategory.BLOCKS, 10.0F, (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F, false);
        break;
      case 3001:
        this.level.playLocalSound(p_180439_3_, SoundEvents.ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 64.0F, 0.8F + this.level.random.nextFloat() * 0.3F, false);
    }

  }

  public void destroyBlockProgress(int p_180441_1_, BlockPos p_180441_2_, int p_180441_3_) {
    if (p_180441_3_ >= 0 && p_180441_3_ < 10) {
      DestroyBlockProgress destroyblockprogress1 = this.destroyingBlocks.get(p_180441_1_);
      if (destroyblockprogress1 != null) {
        this.removeProgress(destroyblockprogress1);
      }

      if (destroyblockprogress1 == null || destroyblockprogress1.getPos().getX() != p_180441_2_.getX() || destroyblockprogress1.getPos().getY() != p_180441_2_.getY() || destroyblockprogress1.getPos().getZ() != p_180441_2_.getZ()) {
        destroyblockprogress1 = new DestroyBlockProgress(p_180441_1_, p_180441_2_);
        this.destroyingBlocks.put(p_180441_1_, destroyblockprogress1);
      }

      destroyblockprogress1.setProgress(p_180441_3_);
      destroyblockprogress1.updateTick(this.ticks);
      this.destructionProgress.computeIfAbsent(destroyblockprogress1.getPos().asLong(), (p_230017_0_) -> {
        return Sets.newTreeSet();
      }).add(destroyblockprogress1);
    } else {
      DestroyBlockProgress destroyblockprogress = this.destroyingBlocks.remove(p_180441_1_);
      if (destroyblockprogress != null) {
        this.removeProgress(destroyblockprogress);
      }
    }

  }

  public boolean hasRenderedAllChunks() {
    return this.chunksToCompile.isEmpty() && this.chunkRenderDispatcher.isQueueEmpty();
  }

  public void needsUpdate() {
    this.needsUpdate = true;
    this.generateClouds = true;
  }

  public void updateGlobalBlockEntities(Collection<TileEntity> p_181023_1_, Collection<TileEntity> p_181023_2_) {
    synchronized (this.globalBlockEntities) {
      this.globalBlockEntities.removeAll(p_181023_1_);
      this.globalBlockEntities.addAll(p_181023_2_);
    }
  }

  public static int getLightColor(ILightReader p_228421_0_, BlockPos p_228421_1_) {
    return getLightColor(p_228421_0_, p_228421_0_.getBlockState(p_228421_1_), p_228421_1_);
  }

  public static int getLightColor(ILightReader p_228420_0_, BlockState p_228420_1_, BlockPos p_228420_2_) {
    if (p_228420_1_.emissiveRendering()) {
      return 15728880;
    } else {
      int i = p_228420_0_.getBrightness(LightType.SKY, p_228420_2_);
      int j = p_228420_0_.getBrightness(LightType.BLOCK, p_228420_2_);
      int k = p_228420_1_.getLightEmission();
      if (j < k) {
        j = k;
      }

      return i << 20 | j << 4;
    }
  }

  public Framebuffer entityTarget() {
    return this.entityTarget;
  }

  @Override
  public net.minecraftforge.resource.IResourceType getResourceType() {
    return net.minecraftforge.resource.VanillaResourceType.MODELS;
  }

  @OnlyIn(Dist.CLIENT)
  class LocalRenderInformationContainer {
    private final ChunkRenderDispatcher.ChunkRender chunk;
    private final Direction sourceDirection;
    private byte directions;
    private final int step;

    private LocalRenderInformationContainer(ChunkRenderDispatcher.ChunkRender p_i46248_2_, @Nullable Direction p_i46248_3_, int p_i46248_4_) {
      this.chunk = p_i46248_2_;
      this.sourceDirection = p_i46248_3_;
      this.step = p_i46248_4_;
    }

    public void setDirections(byte p_189561_1_, Direction p_189561_2_) {
      this.directions = (byte) (this.directions | p_189561_1_ | 1 << p_189561_2_.ordinal());
    }

    public boolean hasDirection(Direction p_189560_1_) {
      return (this.directions & 1 << p_189560_1_.ordinal()) > 0;
    }
  }

  public static void renderLineBox(MatrixStack matrixStack, IVertexBuilder iVertexBuilder, double x1, double y1, double z1, double x2, double y2, double z2, float red1, float green1, float blue1, float alpha, float red2, float green2, float blue2) {
    Matrix4f matrix4f = matrixStack.last().pose();
    float f = (float)x1;
    float f1 = (float)y1;
    float f2 = (float)z1;
    float f3 = (float)x2;
    float f4 = (float)y2;
    float f5 = (float)z2;
    iVertexBuilder.vertex(matrix4f, f, f1, f2).color(red1, green2, blue2, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f2).color(red1, green2, blue2, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f1, f2).color(red2, green1, blue2, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f2).color(red2, green1, blue2, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f1, f2).color(red2, green2, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f1, f5).color(red2, green2, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f1, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f1, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f, f4, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f1, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f5).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f2).color(red1, green1, blue1, alpha).endVertex();
    iVertexBuilder.vertex(matrix4f, f3, f4, f5).color(red1, green1, blue1, alpha).endVertex();
  }
}
  
