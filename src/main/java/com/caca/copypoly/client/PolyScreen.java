package com.caca.copypoly.client;

import com.caca.copypoly.*;
import com.caca.copypoly.geometry.Polyhedron;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public class PolyScreen extends AbstractContainerScreen<PolyMenu> {
    private final List<String[]> rows=new ArrayList<>();
    private int page, visibleRows, tableWidth, previewX, previewWidth;
    private Button apply;
    private Polyhedron preview;
    private List<BakedQuad> previewQuads=List.of();
    private BlockState material;
    private boolean dirty, valid=true, waiting, dragging;
    private long changedAt;
    private Component error=Component.empty();
    private double yaw=.65,pitch=.45,zoom=1;
    public PolyScreen(PolyMenu menu,Inventory inv,Component title) {
        super(menu,inv,title); preview=menu.initial;
        setRows(preview.points());
    }
    private void setRows(List<Polyhedron.Point> points) {
        rows.clear();for(var p:points) rows.add(new String[]{""+p.x(),""+p.y(),""+p.z()});page=0;
    }
    @Override protected void init() {
        imageWidth=Math.min(480,width-12);imageHeight=Math.min(324,height-12);
        super.init();
        tableWidth=Math.max(140,imageWidth/2-12);previewX=leftPos+tableWidth+16;previewWidth=imageWidth-tableWidth-28;
        visibleRows=Math.max(3,(imageHeight-126)/22);
        material=minecraft.level.getBlockEntity(menu.pos) instanceof PolyBlockEntity be?be.getMaterial():AllBlocks.COPYCAT_BASE.getDefaultState();
        rebuild();updatePreview();
    }
    private Component tr(String key,Object...args) {return Component.translatable("screen.copypoly."+key,args);}
    private Button button(String key,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(tr(key),b->action.run()).bounds(x,y,w,20).build());
    }
    private void rebuild() {
        clearWidgets();
        int maxPage=Math.max(0,(rows.size()-1)/visibleRows);page=Math.min(page,maxPage);
        int fieldWidth=(tableWidth-44)/3;
        for(int line=0;line<visibleRows;line++) {
            int index=page*visibleRows+line;if(index>=rows.size())break;
            for(int axis=0;axis<3;axis++) {
                final int a=axis;
                EditBox box=new EditBox(font,leftPos+26+axis*(fieldWidth+2),topPos+42+line*22,fieldWidth,18,
                    Component.literal((index+1)+" "+"XYZ".charAt(axis)));
                box.setMaxLength(3);box.setValue(rows.get(index)[axis]);
                box.setResponder(value->{rows.get(index)[a]=value;markDirty();});addRenderableWidget(box);
            }
            addRenderableWidget(Button.builder(Component.literal("×"),b->{rows.remove(index);markDirty();rebuild();})
                .bounds(leftPos+tableWidth-10,topPos+41+line*22,18,20).build());
        }
        int navY=topPos+43+visibleRows*22;
        Button add=button("add",leftPos+12,navY,tableWidth-66,()->{
            rows.add(new String[]{"8","8","8"});page=(rows.size()-1)/visibleRows;markDirty();rebuild();
        });add.active=rows.size()<Polyhedron.MAX_POINTS;
        Button prev=addRenderableWidget(Button.builder(Component.literal("<"),b->{page--;rebuild();}).bounds(leftPos+tableWidth-48,navY,24,20).build());prev.active=page>0;
        Button next=addRenderableWidget(Button.builder(Component.literal(">"),b->{page++;rebuild();}).bounds(leftPos+tableWidth-20,navY,24,20).build());next.active=page<maxPage;
        int y=topPos+imageHeight-57;
        int w=(tableWidth-6)/3;
        button("copy",leftPos+12,y,w,()->minecraft.keyboardHandler.setClipboard(text()));
        button("paste",leftPos+14+w,y,w,()->{
            try {
                String clipboard=minecraft.keyboardHandler.getClipboard();
                if(clipboard.length()>4096)throw new IllegalArgumentException("count");
                List<Polyhedron.Point> points=Polyhedron.parse(clipboard);
                Polyhedron.of(points);setRows(points);markDirty();rebuild();
            } catch(IllegalArgumentException ex){error=tr("error."+ex.getMessage());}
        });
        button("reset",leftPos+16+2*w,y,w,()->{setRows(Polyhedron.CUBE_POINTS);markDirty();rebuild();});
        apply=button("apply",leftPos+imageWidth-146,topPos+imageHeight-28,64,()->{
            validate();if(!valid || waiting)return;
            waiting=true;apply.active=false;error=tr("saving");
            PacketDistributor.sendToServer(new PolyPackets.Apply(menu.containerId,menu.revision,preview.packed()));
        });
        button("cancel",leftPos+imageWidth-76,topPos+imageHeight-28,64,this::onClose);
        apply.active=valid && !dirty && !waiting;
    }
    private String text() {return String.join("\n",rows.stream().map(r->String.join(", ",r)).toList());}
    private void markDirty() {dirty=true;changedAt=System.currentTimeMillis();if(apply!=null)apply.active=false;}
    private void validate() {
        dirty=false;
        try {preview=Polyhedron.of(Polyhedron.parse(text()));valid=true;error=Component.empty();updatePreview();}
        catch(IllegalArgumentException ex){valid=false;error=tr("error."+ex.getMessage());}
        if(apply!=null)apply.active=valid && !waiting;
    }
    private void updatePreview() {
        if(minecraft.getBlockRenderer().getBlockModel(Copypoly.POLY.get().defaultBlockState()) instanceof PolyModel model)
            previewQuads=model.preview(preview,material);
    }
    public void result(String message) {
        waiting=false;
        if(message.isEmpty()){onClose();return;}
        error=tr("error."+message);apply.active=valid;
    }
    @Override protected void containerTick() {
        if(dirty && System.currentTimeMillis()-changedAt>=160)validate();
        if(!menu.stillValid(minecraft.player))onClose();
    }
    @Override protected void renderLabels(GuiGraphics g,int mx,int my) {}
    @Override protected void renderBg(GuiGraphics g,float partial,int mx,int my) {
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff18252b);
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+25,0xff273c43);
        g.drawString(font,title,leftPos+12,topPos+9,0xffe3f2ec,false);
        int fieldWidth=(tableWidth-44)/3;
        for(int a=0;a<3;a++)g.drawString(font,""+"XYZ".charAt(a),leftPos+26+a*(fieldWidth+2),topPos+30,0xff94b5ad,false);
        for(int i=0;i<visibleRows && page*visibleRows+i<rows.size();i++)g.drawString(font,""+(page*visibleRows+i+1),leftPos+9,topPos+47+i*22,0xff91a6ac,false);
        g.fill(previewX,topPos+30,previewX+previewWidth,topPos+imageHeight-64,0xff101c22);
        drawPreview(g);
        g.drawString(font,tr("count",rows.size(),Polyhedron.MAX_POINTS),previewX+5,topPos+35,0xffa6c9bd,false);
        g.drawString(font,tr("drag"),previewX+5,topPos+imageHeight-78,0xffa6b4b9,false);
        g.drawString(font,"X → E   Y ↑   Z → S",previewX+5,topPos+imageHeight-55,0xffa6b4b9,false);
        if(!error.getString().isEmpty()) {
            g.enableScissor(leftPos+10,topPos+imageHeight-36,leftPos+imageWidth-155,topPos+imageHeight-8);
            g.drawWordWrap(font,error,leftPos+12,topPos+imageHeight-31,imageWidth-170,waiting?0xffaacfc1:0xffffa599);
            g.disableScissor();
        } else g.drawString(font,tr("range"),leftPos+12,topPos+imageHeight-24,0xff9bb0b6,false);
    }
    private Vec3 rotate(Vec3 p) {
        double x=Math.cos(yaw)*p.x+Math.sin(yaw)*p.z,z=-Math.sin(yaw)*p.x+Math.cos(yaw)*p.z;
        return new Vec3(x,Math.cos(pitch)*p.y-Math.sin(pitch)*z,Math.sin(pitch)*p.y+Math.cos(pitch)*z);
    }
    private Vec3 project(Vec3 p) {
        Vec3 r=rotate(p.subtract(.5,.5,.5));double scale=Math.min(previewWidth-30,imageHeight-125)*.73*zoom;
        return new Vec3(previewX+previewWidth*.5+r.x*scale,topPos+30+(imageHeight-94)*.5-r.y*scale,r.z);
    }
    private record PreviewQuad(BakedQuad source,Vec3[] points,double depth) {}
    private void drawPreview(GuiGraphics g) {
        g.enableScissor(previewX,topPos+47,previewX+previewWidth,topPos+imageHeight-83);
        List<PreviewQuad> sorted=new ArrayList<>();
        for(BakedQuad q:previewQuads) {
            int[] data=q.getVertices();int stride=data.length/4;Vec3[] p=new Vec3[4];double depth=0;
            for(int i=0;i<4;i++){p[i]=project(new Vec3(Float.intBitsToFloat(data[i*stride]),Float.intBitsToFloat(data[i*stride+1]),Float.intBitsToFloat(data[i*stride+2])));depth+=p[i].z;}
            // Screen Y is inverted; clockwise screen triangles face the viewer.
            if((p[1].x-p[0].x)*(p[2].y-p[0].y)-(p[1].y-p[0].y)*(p[2].x-p[0].x)>=0)continue;
            sorted.add(new PreviewQuad(q,p,depth/4));
        }
        sorted.sort(Comparator.comparingDouble(PreviewQuad::depth));
        g.flush();
        for(PreviewQuad quad:sorted) {
            int tint=quad.source.isTinted()?minecraft.getBlockColors().getColor(material,minecraft.level,menu.pos,quad.source.getTintIndex()):0xffffff;
            float shade=.68f+.32f*Math.max(0,(float)rotate(Vec3.atLowerCornerOf(quad.source.getDirection().getNormal())).z);
            int color=0xff000000 | ((int)(((tint>>16)&255)*shade)<<16) | ((int)(((tint>>8)&255)*shade)<<8) | (int)((tint&255)*shade);
            VertexConsumer v=g.bufferSource().getBuffer(RenderType.text(TextureAtlas.LOCATION_BLOCKS));
            int[] data=quad.source.getVertices();int stride=data.length/4;
            for(int i=0;i<4;i++)v.addVertex(g.pose().last().pose(),(float)quad.points[i].x,(float)quad.points[i].y,0)
                .setColor(color).setUv(Float.intBitsToFloat(data[i*stride+4]),Float.intBitsToFloat(data[i*stride+5])).setLight(LightTexture.FULL_BRIGHT);
        }
        g.flush();
        for(var face:preview.faces()) {
            Vec3 n=new Vec3(face.plane().x(),face.plane().y(),face.plane().z());if(rotate(n).z<=0)continue;
            for(int i=0;i<face.vertices().size();i++) {
                var a=face.vertices().get(i);var b=face.vertices().get((i+1)%face.vertices().size());
                line(g,project(new Vec3(a.x()/16.,a.y()/16.,a.z()/16.)),project(new Vec3(b.x()/16.,b.y()/16.,b.z()/16.)),0xff93d3bc);
            }
        }
        for(int i=0;i<rows.size();i++)try {
            String[] r=rows.get(i);Vec3 p=project(new Vec3(Integer.parseInt(r[0])/16.,Integer.parseInt(r[1])/16.,Integer.parseInt(r[2])/16.));
            g.fill((int)p.x-1,(int)p.y-1,(int)p.x+2,(int)p.y+2,0xffffdc8b);
            g.drawString(font,""+(i+1),(int)p.x+3,(int)p.y-4,0xffffdc8b,false);
        }catch(NumberFormatException ignored){}
        g.disableScissor();
    }
    private static void line(GuiGraphics g,Vec3 a,Vec3 b,int color) {
        int n=(int)Math.ceil(Math.max(Math.abs(a.x-b.x),Math.abs(a.y-b.y)));
        for(int i=0;i<=n;i++){double t=n==0?0:(double)i/n;int x=(int)Math.round(a.x+(b.x-a.x)*t),y=(int)Math.round(a.y+(b.y-a.y)*t);g.fill(x,y,x+1,y+1,color);}
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button==0 && x>=previewX && x<previewX+previewWidth && y>=topPos+47 && y<topPos+imageHeight-83){dragging=true;setFocused(null);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(dragging){yaw+=dx*.015;pitch=Math.max(-1.4,Math.min(1.4,pitch+dy*.015));return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(x>=previewX && x<previewX+previewWidth){zoom=Math.max(.5,Math.min(1.5,zoom+vertical*.08));return true;}
        page=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/visibleRows),page-(int)Math.signum(vertical)));rebuild();return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods) {
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}
        if(getFocused() instanceof EditBox box && box.keyPressed(key,scan,mods))return true;
        // Inventory hotkey must not close an editor while a text field is focused.
        if(getFocused() instanceof EditBox)return false;
        return super.keyPressed(key,scan,mods);
    }
}
