package com.tfar.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.monster.CreeperEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawHighlightEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.StreamSupport;

public class Utils {

  private static final Minecraft mc = Minecraft.getInstance();

  public static Map<BlockPos,Integer> posIntegerMap = new HashMap<>();
  public static Map<Vec3d,Integer> mobLocMap = new HashMap<>();

  public static void scan(){
    int r = 64;
    posIntegerMap.clear();
    BlockPos playerPos = mc.player.getCommandSenderBlockPosition();
    World world = mc.level;
    BlockPos
            .betweenClosedStream(playerPos.offset(-r,-r,-r),playerPos.offset(r,r,r))
            .forEach(pos -> {
              if (matches(world.getBlockState(pos).getBlock()))posIntegerMap.put(pos.immutable(),getColor(world.getBlockState(pos).getBlock()));
            });
  }


  public static void scanMob(){
    mobLocMap.clear();
    ClientWorld world = mc.level;
    StreamSupport.stream(world.entitiesForRendering().spliterator(),false)
            .filter(CreeperEntity.class::isInstance).forEach(entity -> mobLocMap.put(entity.position(),0x00ff00));
  }

  public static boolean matches(Block block){
    return XrayConfig.configs.containsKey(block.getRegistryName());
  }

  public static int getColor(Block block){
    return XrayConfig.configs.get(block.getRegistryName());
  }

  public static CreeperEntity creeperEntity;

  public static void drawMobBox(RenderWorldLastEvent e, Vec3d pos, int color) {

    if (creeperEntity == null){
      creeperEntity = EntityType.CREEPER.create(Minecraft.getInstance().level);
    }

    AxisAlignedBB axisAlignedBB = creeperEntity.getBoundingBox();

    WorldRenderer renderer = e.getContext();

    MatrixStack matrixStack = e.getMatrixStack();

    ActiveRenderInfo camera = Minecraft.getInstance().gameRenderer.getMainCamera();

    Vec3d vec3d = camera.getPosition();
    double x = -(vec3d.x - pos.x);
    double y = -(vec3d.y- pos.y);
    double z = -(vec3d.z- pos.z);


    IRenderTypeBuffer.Impl irendertypebuffer$impl = renderer.renderBuffers.bufferSource();
    IVertexBuilder ivertexbuilder2 = irendertypebuffer$impl.getBuffer(RenderType.lines());

    matrixStack.pushPose();
    matrixStack.translate(x,y,z);
    WorldRenderer.renderLineBox(matrixStack, ivertexbuilder2,axisAlignedBB,(color >> 16 & 0xff) / 256f, (color >> 8 & 0xff) / 256f, (color & 0xff) / 256f, 1);
    matrixStack.popPose();

  }




  static void renderHitOutline(MatrixStack p_228429_1_, IVertexBuilder p_228429_2_, double p_228429_4_, double p_228429_6_, double p_228429_8_, BlockPos p_228429_10_) {
    renderShape(p_228429_1_, p_228429_2_, VoxelShapes.block(), (double)p_228429_10_.getX() - p_228429_4_, (double)p_228429_10_.getY() - p_228429_6_, (double)p_228429_10_.getZ() - p_228429_8_, 0.0F, 0.0F, 0.0F, 0.4F);
  }
  private static void renderShape(MatrixStack matrixStack, IVertexBuilder iVertexBuilder, VoxelShape voxelShape, double x, double p_228445_5_, double p_228445_7_, float red, float green, float blue, float alpha) {
    Matrix4f matrix4f = matrixStack.last().pose();
    voxelShape.forAllEdges((x1, p_230013_14_, p_230013_16_, x2, p_230013_20_, p_230013_22_) -> {
      iVertexBuilder.vertex(matrix4f, (float)(x1 + x), (float)(p_230013_14_ + p_228445_5_), (float)(p_230013_16_ + p_228445_7_)).color(red, green, blue, alpha).endVertex();
      iVertexBuilder.vertex(matrix4f, (float)(x2 + x), (float)(p_230013_20_ + p_228445_5_), (float)(p_230013_22_ + p_228445_7_)).color(red, green, blue, alpha).endVertex();
    });
  }

}
