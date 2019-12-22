package com.tfar.examplemod;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.security.auth.login.Configuration;

import static com.tfar.examplemod.Xray.scan;
import static com.tfar.examplemod.Xray.scanmob;
import static org.lwjgl.opengl.GL11.GL_FILL;
import static org.lwjgl.opengl.GL11C.GL_FRONT_AND_BACK;
import static org.lwjgl.opengl.GL11C.GL_LINE;

public class EventHandler {

  private EventHandler(){}

  public static final EventHandler instance = new EventHandler();

  public static MatrixStack matrixStack = new MatrixStack();

  @SubscribeEvent
  public void render(RenderWorldLastEvent e){
    if (Screen.hasAltDown()){
      WorldRenderer renderer = e.getContext();
      ActiveRenderInfo camera = TileEntityRendererDispatcher.instance.camera;
      MatrixStack matrixStack = e.getMatrixStack();

      Utils.posIntegerMap.forEach((pos, stack) -> {

        Vec3d vec3d = camera.getPosition();
        double x1 = vec3d.x;
        double y1 = vec3d.y;
        double z1 = vec3d.z;

        double x2 = pos.getX();
        double y2 = pos.getY();
        double z2 = pos.getZ();

        double x = x2 - x1;
        double y = y2 - y1;
        double z = z2 - z1;

        IRenderTypeBuffer.Impl irendertypebuffer$impl = renderer.renderBuffers.bufferSource();

        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(x,y,z,x+1,y+1,z+1);

        IVertexBuilder ivertexbuilder2 = irendertypebuffer$impl.getBuffer(RenderType.lines());
        WorldRenderer.renderLineBox(matrixStack, ivertexbuilder2,axisAlignedBB, 1,1,1,1);
        irendertypebuffer$impl.endBatch(RenderType.LINES);
      });

    }
    else if (Screen.hasControlDown()){
      //Utils.mobLocMap.forEach((vec3d, integer) -> Utils.drawMobBox(e,vec3d,integer));
    }
  }

  @SubscribeEvent
  public void render2(RenderWorldLastEvent e){
    matrixStack = e.getMatrixStack();
  }

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    if (scan.consumeClick()) {
      Utils.scan();
    } else if (scanmob.consumeClick())Utils.scanMob();
  }

  @SubscribeEvent
  public void fov(FOVUpdateEvent e){
   // e.setNewfov(1);
  }

  @SubscribeEvent
  public void fovmodify(EntityViewRenderEvent.FOVModifier e){
    //e.setFOV(70);
  }

}
