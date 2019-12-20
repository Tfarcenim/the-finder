package com.tfar.examplemod;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

public class Utils {

  private static final Minecraft mc = Minecraft.getInstance();

  public static Map<BlockPos,Integer> posIntegerMap = new HashMap<>();

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

  public static boolean matches(Block block){
    return XrayConfig.configs.containsKey(block.getRegistryName());
  }

  public static int getColor(Block block){
    return XrayConfig.configs.get(block.getRegistryName());
  }

  public static void drawBoundingBox(BlockPos pos,int color) {
    Vec3d viewPosition = Minecraft.getInstance().gameRenderer.getActiveRenderInfo().getProjectedView();
    double x = pos.getX() - viewPosition.x;
    double y = pos.getY() - viewPosition.y;
    double z = pos.getZ() - viewPosition.z;

    double d = .0005;

    GlStateManager.disableTexture();
    GlStateManager.disableLighting();
    GlStateManager.disableBlend();
    GlStateManager.disableDepthTest();

    WorldRenderer.drawBoundingBox(x - d, y - d, z - d,
            x + 1 + d, y + 1 + d, z + 1 + d,
            (color >> 16 & 0xff) / 256f, (color >> 8 & 0xff) / 256f, (color & 0xff) / 256f, 1);

    GlStateManager.enableDepthTest();
    GlStateManager.enableTexture();
    GlStateManager.enableLighting();
  }

}
