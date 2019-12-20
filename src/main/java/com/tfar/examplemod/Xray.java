package com.tfar.examplemod;

import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
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

        EVENT_BUS.register(EventHandler.instance);
        XrayConfig.handle();
    }

    static KeyBinding scan = new KeyBinding(MODID, GLFW.GLFW_KEY_X, MODID);



}
