package com.caca.copypoly.client;

import com.caca.copypoly.*;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid=Copypoly.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class PolyClient {
    /**
     * Re-renders the 3x3x3 block neighbourhood around {@code pos}.
     * <p>
     * A connected-texture face depends on the neighbours' model data, but NeoForge's
     * {@code requestModelDataUpdate()} only refreshes the block that called it. Without this an edited hull
     * leaves its neighbours on the tiles they picked while it was still a plain cuboid, which shows up as a
     * shared face where one half joins up and the other keeps its own frame.
     */
    public static void refreshAround(Level level,BlockPos pos) {
        if(!level.isClientSide) return;
        Minecraft.getInstance().levelRenderer.setBlocksDirty(pos.getX()-1,pos.getY()-1,pos.getZ()-1,
            pos.getX()+1,pos.getY()+1,pos.getZ()+1);
    }
    @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult event) {
        event.getModels().replaceAll((id,model)->id.id().equals(Copypoly.id("copycat_polyhedron"))?new PolyModel(model):model);
    }
    @SubscribeEvent public static void colors(RegisterColorHandlersEvent.Block event) {
        event.register(CopycatBlock.wrappedColor(),Copypoly.POLY.get());
    }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) {
        event.register(Copypoly.MENU.get(),PolyScreen::new);
        PolyPackets.clientResult=r->{if(Minecraft.getInstance().screen instanceof PolyScreen s && s.getMenu().containerId==r.menu())s.result(r.error());};
    }
}
