package com.tfar.examplemod;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static com.tfar.examplemod.Utils.mc;
import static com.tfar.examplemod.Xray.scan;
import static com.tfar.examplemod.Xray.scanmob;

public class EventHandler {

  private static float renderPartialTicksPaused = 0f;
  private static boolean gameWasPauseFlag = false;

  private EventHandler() {
  }
  public static final EventHandler instance = new EventHandler();


  @SubscribeEvent
  public void render(RenderWorldLastEvent e) {
    if (Screen.hasAltDown()) {
      Render.renderBlocks(e);
    } else if (Screen.hasControlDown()) {
      //Utils.mobLocMap.forEach((vec3d, integer) -> Utils.drawMobBox(e,vec3d,integer));
    }
  }

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    if (scan.consumeClick()) {
      Utils.scan();
    } else if (scanmob.consumeClick()) Utils.scanMob();
  }

}
