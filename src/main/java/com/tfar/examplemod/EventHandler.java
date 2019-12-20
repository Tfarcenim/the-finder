package com.tfar.examplemod;

import net.minecraft.client.gui.screen.Screen;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import static com.tfar.examplemod.Xray.scan;

public class EventHandler {

  private EventHandler(){}

  public static final EventHandler instance = new EventHandler();

  @SubscribeEvent
  public void render(RenderWorldLastEvent e){
    if (Screen.hasAltDown()){
      Utils.posIntegerMap.forEach(Utils::drawBoundingBox);
    }
  }

  @SubscribeEvent
  public void onKeyInput(InputEvent.KeyInputEvent event) {
    if (scan.isPressed()) {
      Utils.scan();
    }
  }
}
