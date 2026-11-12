package com.caca.copypoly;

import com.caca.copypoly.client.PolyModel;
import com.caca.copypoly.geometry.Polyhedron;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in client startup/model check; never present in the release jar. */
@EventBusSubscriber(modid=Copypoly.MODID,value=Dist.CLIENT)
public final class ClientCompatibilityProbe {
    private static boolean finished;
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (finished || !Boolean.getBoolean("copypoly.clientSmoke")) return;
        var client=Minecraft.getInstance();
        if (client.screen==null || client.getOverlay()!=null) return;
        finished=true;
        var model=client.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState());
        if (!(model instanceof PolyModel poly)) throw new IllegalStateException("CopyPoly model wrapper was not registered");
        if (poly.getQuads(null,null,RandomSource.create()).isEmpty()) throw new IllegalStateException("CopyPoly item mesh is empty");
        var tetra=Polyhedron.of(Polyhedron.parse("0,0,0;16,0,0;0,16,0;0,0,16"));
        for(var block:new net.minecraft.world.level.block.Block[]{Blocks.STONE,Blocks.GLASS,Blocks.RED_STAINED_GLASS,
                Blocks.OAK_LEAVES,Blocks.GRASS_BLOCK,Blocks.OAK_LOG,com.simibubi.create.AllBlocks.ROSE_QUARTZ_TILES.get()}) {
            var quads=poly.preview(tetra,block.defaultBlockState());
            if(quads.isEmpty()) throw new IllegalStateException("CopyPoly preview mesh is empty for "+block);
            for(var quad:quads) {
                if(quad.getSprite().contents().name().getPath().equals("missingno"))
                    throw new IllegalStateException("CopyPoly preview has missing texture for "+block);
            }
        }
        com.mojang.logging.LogUtils.getLogger().info("CopyPoly client compatibility probe passed: model registration, item mesh, seven material meshes");
        client.stop();
    }
}
