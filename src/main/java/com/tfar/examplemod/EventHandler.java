package com.tfar.examplemod;

import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.client.event.DrawHighlightEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static com.tfar.examplemod.Xray.scan;
import static com.tfar.examplemod.Xray.scanmob;

public class EventHandler {

  private EventHandler(){}

  public static final EventHandler instance = new EventHandler();

  @SubscribeEvent
  public void render(RenderWorldLastEvent e){
    if (Screen.hasAltDown()){
      Utils.posIntegerMap.forEach((renderer, stack) -> Utils.drawBoundingBox(e,renderer,stack));
    }
    else if (Screen.hasControlDown()){
      Utils.mobLocMap.forEach((vec3d, integer) -> Utils.drawMobBox(e,vec3d,integer));
    }
  }

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    if (scan.isPressed()) {
      Utils.scan();
    } else if (scanmob.isPressed())Utils.scanMob();
  }
}
