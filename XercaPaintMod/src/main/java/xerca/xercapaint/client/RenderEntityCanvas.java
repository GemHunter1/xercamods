package xerca.xercapaint.client;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import xerca.xercapaint.common.PaletteUtil;
import xerca.xercapaint.common.XercaPaint;
import xerca.xercapaint.common.entity.EntityCanvas;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
public class RenderEntityCanvas extends EntityRenderer<EntityCanvas> {
    static public RenderEntityCanvas theInstance;
    static private final ResourceLocation backLocation = new ResourceLocation("minecraft", "textures/block/birch_planks.png");
    private static final int[] EMPTY_PIXELS;

    static {
        EMPTY_PIXELS = new int[1024];
        for(int i=0; i<1024; i++){
            EMPTY_PIXELS[i] = PaletteUtil.Color.WHITE.rgbVal();
        }
    }

    private final TextureManager textureManager;
    private final Map<String, RenderEntityCanvas.Instance> loadedCanvases = Maps.newHashMap();

    RenderEntityCanvas(EntityRendererManager renderManager) {
        super(renderManager);
        this.textureManager = Minecraft.getInstance().textureManager;
    }

    @Nullable
    @Override
    public ResourceLocation getEntityTexture(EntityCanvas entity) {
        return getCanvasRendererInstance(entity).location;
    }

    @Override
    public void render(EntityCanvas entity, float entityYaw, float partialTicks, MatrixStack matrixStackIn, IRenderTypeBuffer bufferIn, int packedLightIn) {
        super.render(entity, entityYaw, partialTicks, matrixStackIn, bufferIn, packedLightIn);
        getCanvasRendererInstance(entity).render(entity, entityYaw, entity.rotationPitch, matrixStackIn, bufferIn, entity.getHorizontalFacing(), packedLightIn);
    }


    public static class RenderEntityCanvasFactory implements IRenderFactory<EntityCanvas> {
        @Override
        public EntityRenderer<? super EntityCanvas> createRenderFor(EntityRendererManager manager) {
            theInstance = new RenderEntityCanvas(manager);
            return theInstance;
        }
    }

    public void updateMapTexture(String name, int version) {
        Instance instance = this.getMapInstanceIfExists(name);
        if(instance != null){
            instance.updateCanvasTexture(name, version);
        }
    }

    private RenderEntityCanvas.Instance getCanvasRendererInstance(EntityCanvas canvas) {
        return getCanvasRendererInstance(canvas.getCanvasName(), canvas.getCanvasVersion(), canvas.getWidthPixels(), canvas.getHeightPixels());
    }

    RenderEntityCanvas.Instance getCanvasRendererInstance(CompoundNBT tag, int width, int height) {
        String name = tag.getString("name");
        int version = tag.getInt("v");
        if(!EntityCanvas.PICTURES.containsKey(name)){
            EntityCanvas.PICTURES.put(name, new EntityCanvas.Picture(version, tag.getIntArray("pixels")));
        }
        return getCanvasRendererInstance(name, version, width, height);
    }

    RenderEntityCanvas.Instance getCanvasRendererInstance(String name, int version, int width, int height) {
        RenderEntityCanvas.Instance instance = this.loadedCanvases.get(name);
        if (instance == null) {
            instance = new Instance(name, version, width, height);
            this.loadedCanvases.put(name, instance);
        }else{
            if(instance.version < version || !instance.loaded){
                instance.updateCanvasTexture(name, version);
            }
        }

        return instance;
    }

    @Nullable
    public RenderEntityCanvas.Instance getMapInstanceIfExists(String name) {
        return this.loadedCanvases.get(name);
    }

    /**
     * Clears the currently loaded maps and removes their corresponding textures
     */
    public void clearLoadedCanvases() {
        for(RenderEntityCanvas.Instance instance : this.loadedCanvases.values()) {
            instance.close();
        }

        this.loadedCanvases.clear();
    }

    public void close() {
        this.clearLoadedCanvases();
    }

    @OnlyIn(Dist.CLIENT)
    public class Instance implements AutoCloseable {
        int version = 0;
        int width;
        int height;
        boolean loaded;
        boolean started;
        public final DynamicTexture canvasTexture;
        public final ResourceLocation location;

        private Instance(String name, int version, int width, int height) {
            this.started = false;
            this.loaded = false;
            this.width = width;
            this.height = height;
            this.canvasTexture = new DynamicTexture(width, height, true);
            this.location = RenderEntityCanvas.this.textureManager.getDynamicTextureLocation("canvas/" + name, this.canvasTexture);

            updateCanvasTexture(name, version);
        }

        private int swapColor(int color){
            int i = (color & 16711680) >> 16;
            int j = (color & '\uff00') >> 8;
            int k = (color & 255);
            return k << 16 | j << 8 | i;
        }

        private void updateCanvasTexture(String name, int version) {
            this.version = version;
            int[] pixels = EMPTY_PIXELS;
            if(EntityCanvas.PICTURES.containsKey(name)){
                pixels = EntityCanvas.PICTURES.get(name).pixels;
                loaded = true;
            }
            if(loaded || !started){
                if(pixels.length < height*width){
                    XercaPaint.LOGGER.warn("Pixels array length (" + pixels.length + ") is smaller than canvas area (" + height*width + ")");
                    return;
                }

                for (int i = 0; i < height; ++i) {
                    for (int j = 0; j < width; ++j) {
                        int k = j + i * width;
                        canvasTexture.getTextureData().setPixelRGBA(j, i, swapColor(pixels[k]));
                    }
                }

                canvasTexture.updateDynamicTexture();
            }
            this.started = true;
        }

        public void render(@Nullable EntityCanvas canvas, float yaw, float pitch, MatrixStack ms, IRenderTypeBuffer buffer, Direction facing, int packedLight) {
            final float wScale = width/16.0f;
            final float hScale = height/16.0f;

            ms.push();
            Matrix3f mn = ms.getLast().getNormal().copy();

            float xOffset = facing.getXOffset();
            float yOffset = facing.getYOffset();
            float zOffset = facing.getZOffset();

            if(canvas != null && canvas.getRotation() > 0) {
                ms.rotate(Vector3f.XP.rotationDegrees( pitch));
                ms.rotate(Vector3f.YP.rotationDegrees( 180-yaw));
                ms.rotate(Vector3f.ZP.rotationDegrees(90*canvas.getRotation()));
                ms.rotate(Vector3f.YP.rotationDegrees( -180+yaw));
                ms.rotate(Vector3f.XP.rotationDegrees( -pitch));
            }
            ms.getLast().getNormal().set(mn);

            float f = 1.0f/32.0f;
            if(canvas != null) {
                if (facing.getAxis().isHorizontal()) {
                    ms.translate(zOffset * 0.5d * wScale, -0.5d * hScale, -xOffset * 0.5d * wScale);
                } else {
                    ms.translate(0.5 * wScale, 0 * hScale, (yOffset > 0 ? 0.5 : -0.5) * wScale);
                }
            }
            else{
                ms.translate(0.75, 0.5, 0.5);
                if(wScale > 1 || hScale > 1){
                    f /= 3.3f;
                }else{
                    f /= 2.0f;
                }
            }
            ms.rotate(Vector3f.XP.rotationDegrees( pitch));
            ms.rotate(Vector3f.YP.rotationDegrees( 180-yaw));

            ms.scale(f, f, f);

            textureManager.bindTexture(location);

            Matrix4f m = ms.getLast().getMatrix();
            mn = ms.getLast().getNormal();
            IVertexBuilder vb = buffer.getBuffer(RenderType.getEntitySolid(this.location));

            // Draw the front
            addVertex(vb, m, mn, 0.0F, 32.0F*hScale, -1.0F, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0F*wScale, 32.0F*hScale, -1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0F*wScale, 0.0F, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0F, 0.0F, -1.0F, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);

            vb = buffer.getBuffer(RenderType.getEntitySolid(backLocation));
            // Draw the back and sides
            final float sideWidth = 1.0F/16.0F;
            textureManager.bindTexture(backLocation);
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0D, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0D, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0D, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0D, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);

            // Sides
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0D, sideWidth, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0D, sideWidth, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, -1.0D, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 0.0D, -1.0D, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);

            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, 1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0F, 1.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, -1.0F, 1.0F, sideWidth, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 32.0D*hScale, -1.0F, 0.0F, sideWidth, packedLight, xOffset, yOffset, zOffset);

            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, -1.0F, 0.0F, 0.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 32.0D*hScale, 1.0F, sideWidth, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0F, sideWidth, 0.0F, packedLight, xOffset, yOffset, zOffset);

            addVertex(vb, m, mn, 0.0D, 0.0D, -1.0F, 0.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, -1.0F, 1.0F, 1.0F, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 32.0D*wScale, 0.0D, 1.0F, 1.0F, 1.0F-sideWidth, packedLight, xOffset, yOffset, zOffset);
            addVertex(vb, m, mn, 0.0D, 0.0D, 1.0F, 0.0F, 1.0F-sideWidth, packedLight, xOffset, yOffset, zOffset);

            ms.pop();
        }

        private void addVertex(IVertexBuilder vb, Matrix4f m, Matrix3f mn, double x, double y, double z, float tx, float ty, int lightmap, float xOff, float yOff, float zOff)
        {
            vb.pos(m, (float) x, (float)y, (float)z).color(255, 255, 255, 255).tex(tx, ty).overlay(OverlayTexture.NO_OVERLAY).lightmap(lightmap).normal(mn, xOff, yOff, zOff).endVertex();
        }

        private void addVertexFront(IVertexBuilder vb, Matrix4f m, Matrix3f mn, double x, double y, double z, float tx, float ty, int lightmap, float xOff, float yOff, float zOff)
        {
            vb.pos(m, (float) x, (float)y, (float)z).color(255, 255, 255, 255).tex(tx, ty).lightmap(lightmap).endVertex();
        }

        public void close() {
            this.canvasTexture.close();
        }
    }
}