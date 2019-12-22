package com.tfar.examplemod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

import static net.minecraftforge.common.MinecraftForge.EVENT_BUS;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(Xray.MODID)
public class Xray
{
    // Directly reference a log4j logger.

    public static final String MODID = "xray";

    private static final Logger LOGGER = LogManager.getLogger();

    public Xray() {
        // Register the doClientStuff method for modloading
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
    }

    private void doClientStuff(final FMLClientSetupEvent event) {

       // Minecraft.getInstance().levelRenderer = new WorldRendererEX(Minecraft.getInstance(),
       //         Minecraft.getInstance().levelRenderer.renderBuffers);

        EVENT_BUS.register(EventHandler.instance);
        XrayConfig.handle();
        ClientRegistry.registerKeyBinding(scan);
        ClientRegistry.registerKeyBinding(scanmob);
    }

    static KeyBinding scan = new KeyBinding(MODID+1, GLFW.GLFW_KEY_I, MODID);
    static KeyBinding scanmob = new KeyBinding(MODID+2, GLFW.GLFW_KEY_O, MODID);



}
