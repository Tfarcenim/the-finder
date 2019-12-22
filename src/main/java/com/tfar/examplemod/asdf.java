package com.tfar.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.Matrix4f;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.math.shapes.VoxelShapes;

public class asdf {

  public static void renderBoxes(MatrixStack matrixStack,
                                 IVertexBuilder builder,double x, double y, double z){
    Utils.posIntegerMap.forEach((pos, color) -> {
      renderShape(matrixStack,builder, VoxelShapes.block(),pos.getX() - x, pos.getY() - y,pos.getZ() - z,(color >> 16 & 0xff) / 256f, (color >> 8 & 0xff) / 256f, (color & 0xff) / 256f, 1);
    });
    RenderSystem.depthMask(true);
  }

  private static void renderShape(MatrixStack matrixStack, IVertexBuilder iVertexBuilder, VoxelShape shape, double p_228445_3_, double p_228445_5_, double p_228445_7_, float red, float p_228445_10_, float p_228445_11_, float p_228445_12_) {
    Matrix4f matrix4f = matrixStack.last().pose();
    shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
      iVertexBuilder.vertex(matrix4f, (float) (x1 + p_228445_3_), (float) (y1 + p_228445_5_), (float) (z1 + p_228445_7_)).color(red, p_228445_10_, p_228445_11_, p_228445_12_).endVertex();
      iVertexBuilder.vertex(matrix4f, (float) (x2 + p_228445_3_), (float) (y2 + p_228445_5_), (float) (z2 + p_228445_7_)).color(red, p_228445_10_, p_228445_11_, p_228445_12_).endVertex();
    });
  }
}
