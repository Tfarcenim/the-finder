package com.tfar.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;

public class Render {
  private static final int GL_FRONT_AND_BACK = 1032;
  private static final int GL_LINE = 6913;
  private static final int GL_FILL = 6914;
  private static final int GL_LINES = 1;

  public static void renderBlocks(RenderWorldLastEvent e) {

    Vec3d vec3d = TileEntityRendererDispatcher.instance.camera.getPosition();

    MatrixStack stack = e.getMatrixStack();
    stack.translate(-vec3d.x, -vec3d.y, -vec3d.z);

    RenderSystem.pushMatrix();
    RenderSystem.multMatrix(stack.last().pose());

    Tessellator tessellator = Tessellator.getInstance();
    BufferBuilder buffer = tessellator.getBuilder();
    Profile.BLOCKS.apply(); // Sets GL state for block drawing

    Utils.posIntegerMap.forEach((pos, integer) -> {
      buffer.begin(GL_LINES, DefaultVertexFormats.POSITION_COLOR);
      renderBlockBounding(buffer, pos, integer, 1);
      tessellator.end();
    });

    Profile.BLOCKS.clean();
    RenderSystem.popMatrix();
  }

  private static void renderBlockBounding(BufferBuilder buffer, BlockPos pos, int color, int opacity) {

    final float size = 1.0f;

    float red = (color >> 16 & 0xff) / 255f;
    float green = (color >> 8 & 0xff) / 255f;
    float blue = (color & 0xff) / 255f;


    int x = pos.getX();
    int y = pos.getY();
    int z = pos.getZ();

    // TOP
    // func_225582_a_ = POS
    // func_227885_a_ = COLOR
    buffer.vertex(x, y + size, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y + size, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y + size, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y + size, z).color(red, green, blue, opacity).endVertex();

    // BOTTOM
    buffer.vertex(x + size, y, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y, z).color(red, green, blue, opacity).endVertex();

    // Edge 1
    buffer.vertex(x + size, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z + size).color(red, green, blue, opacity).endVertex();

    // Edge 2
    buffer.vertex(x + size, y, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x + size, y + size, z).color(red, green, blue, opacity).endVertex();

    // Edge 3
    buffer.vertex(x, y, z + size).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y + size, z + size).color(red, green, blue, opacity).endVertex();

    // Edge 4
    buffer.vertex(x, y, z).color(red, green, blue, opacity).endVertex();
    buffer.vertex(x, y + size, z).color(red, green, blue, opacity).endVertex();
  }

  /**
   * OpenGL Profiles used for rendering blocks and entities
   */
  private enum Profile {
    BLOCKS {
      @Override
      public void apply() {
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonMode(GL_FRONT_AND_BACK, GL_LINE);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableBlend();
        RenderSystem.lineWidth(1);
      }

      @Override
      public void clean() {
        RenderSystem.polygonMode(GL_FRONT_AND_BACK, GL_FILL);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
      }
    };

    Profile() {
    }

    public abstract void apply();

    public abstract void clean();
  }
}

