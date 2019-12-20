package com.tfar.examplemod;

import net.minecraft.util.math.BlockPos;

public class BlockData {

    public final BlockPos pos;
    public boolean success = false;

    public BlockData(BlockPos pos) {
        this.pos = pos;
    }

    public void setSuccessful(){
        success=true;
    }
}
