package com.tfar.examplemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.util.ResourceLocation;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class XrayConfig {

  public static Gson g = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  public static final String location = "config/xray_client.json";

  public static void handle(){
    File file = new File(location);
    if (!file.exists()) writeDefaultConfig();
    load();
  }

  public static void writeDefaultConfig() {
    try {
      FileWriter writer = new FileWriter(location);
      writer.write(ExampleConfig.s);
      writer.flush();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public static void load() {
    try {
      configs.clear();
      Reader reader = new FileReader(location);
      JsonObject json = g.fromJson(reader,JsonObject.class);
      json.entrySet().forEach(stringJsonElementEntry ->
              configs.put(new ResourceLocation(stringJsonElementEntry.getKey()),
                      Integer.decode(stringJsonElementEntry.getValue().getAsString())));
    } catch (IOException ofcourse) {
      throw new RuntimeException(ofcourse);
    }
  }

  public static final Map<ResourceLocation,Integer> configs = new HashMap<>();

}
