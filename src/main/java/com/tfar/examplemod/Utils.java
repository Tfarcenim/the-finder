package com.tfar.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
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
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

public class Utils {

  private static final Minecraft mc = Minecraft.getInstance();

  public static Map<BlockPos,Integer> posIntegerMap = new HashMap<>();
  public static Map<Vec3d,Integer> mobLocMap = new HashMap<>();

  public static void scan(){
    int r = 64;
    posIntegerMap.clear();
    BlockPos playerPos = mc.player.getPosition();
    World world = mc.world;
    BlockPos
            .getAllInBox(playerPos.add(-r,-r,-r),playerPos.add(r,r,r))
            .forEach(pos -> {
              if (matches(world.getBlockState(pos).getBlock()))posIntegerMap.put(pos.toImmutable(),getColor(world.getBlockState(pos).getBlock()));
            });
  }


  public static void scanMob(){
    mobLocMap.clear();
    ClientWorld world = mc.world;
    StreamSupport.stream(world.getAllEntities().spliterator(),false)
            .filter(CreeperEntity.class::isInstance).forEach(entity -> mobLocMap.put(entity.getPositionVec(),0x00ff00));
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
      creeperEntity = EntityType.CREEPER.create(Minecraft.getInstance().world);
    }

    AxisAlignedBB axisAlignedBB = creeperEntity.getBoundingBox();

    WorldRenderer renderer = e.getContext();

    MatrixStack matrixStack = e.getMatrixStack();

    ActiveRenderInfo camera = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();

    Vec3d vec3d = camera.getProjectedView();
    double x = -(vec3d.getX() - pos.x);
    double y = -(vec3d.getY()- pos.y);
    double z = -(vec3d.getZ()- pos.z);

    IRenderTypeBuffer.Impl irendertypebuffer$impl = renderer.field_228415_m_.func_228487_b_();
    IVertexBuilder ivertexbuilder2 = irendertypebuffer$impl.getBuffer(RenderType.func_228659_m_());

    matrixStack.func_227860_a_();
    matrixStack.func_227861_a_(x,y,z);
    WorldRenderer.func_228430_a_(matrixStack, ivertexbuilder2,axisAlignedBB,(color >> 16 & 0xff) / 256f, (color >> 8 & 0xff) / 256f, (color & 0xff) / 256f, 1);
    matrixStack.func_227865_b_();

  }


  public static void drawBoundingBox(RenderWorldLastEvent e, BlockPos pos, int color) {

    WorldRenderer renderer = e.getContext();

    MatrixStack matrixStack = e.getMatrixStack();

    ActiveRenderInfo camera = Minecraft.getInstance().gameRenderer.getActiveRenderInfo();

    Vec3d vec3d = camera.getProjectedView();
    double x = vec3d.getX();
    double y = vec3d.getY();
    double z = vec3d.getZ();

    IRenderTypeBuffer.Impl irendertypebuffer$impl = renderer.field_228415_m_.func_228487_b_();
    IVertexBuilder ivertexbuilder2 = irendertypebuffer$impl.getBuffer(RenderType.func_228659_m_());

    matrixStack.func_227860_a_();
    drawBlockOutline(matrixStack, ivertexbuilder2, x, y, z, pos,color);
    matrixStack.func_227865_b_();
  }
  private static void drawBlockOutline(MatrixStack stack, IVertexBuilder iVertexBuilder, double x, double y, double z, BlockPos p_228429_10_, int color) {
    drawBox(stack, iVertexBuilder, VoxelShapes.fullCube(), (double)p_228429_10_.getX() - x, (double)p_228429_10_.getY() - y, (double)p_228429_10_.getZ() - z,(color >> 16 & 0xff) / 256f, (color >> 8 & 0xff) / 256f, (color & 0xff) / 256f, 1);
  }

  private static void drawBox(MatrixStack stack, IVertexBuilder iVertexBuilder, VoxelShape p_228445_2_, double p_228445_3_, double p_228445_5_, double p_228445_7_, float red, float green, float blue, float alpha) {
    Matrix4f matrix4f = stack.func_227866_c_().func_227870_a_();
    p_228445_2_.forEachEdge((p_230013_12_, p_230013_14_, p_230013_16_, p_230013_18_, p_230013_20_, p_230013_22_) -> {
      iVertexBuilder.func_227888_a_(matrix4f, (float)(p_230013_12_ + p_228445_3_), (float)(p_230013_14_ + p_228445_5_), (float)(p_230013_16_ + p_228445_7_)).func_227885_a_(red, green, blue, alpha).endVertex();
      iVertexBuilder.func_227888_a_(matrix4f, (float)(p_230013_18_ + p_228445_3_), (float)(p_230013_20_ + p_228445_5_), (float)(p_230013_22_ + p_228445_7_)).func_227885_a_(red, green, blue, alpha).endVertex();
    });
  }

}
