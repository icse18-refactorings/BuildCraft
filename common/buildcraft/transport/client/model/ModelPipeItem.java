package buildcraft.transport.client.model;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Point2f;
import javax.vecmath.Point3f;
import javax.vecmath.Tuple3f;
import javax.vecmath.Vector3f;

import com.google.common.collect.ImmutableList;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.world.World;

import buildcraft.core.lib.client.model.BCModelHelper;
import buildcraft.lib.client.model.ModelItemSimple;
import buildcraft.lib.client.model.MutableQuad;
import buildcraft.lib.client.model.MutableVertex;
import buildcraft.transport.api_move.IPipeItem;
import buildcraft.transport.api_move.PipeDefinition;

public enum ModelPipeItem implements IBakedModel {
    INSTANCE;

    private static final MutableQuad[] QUADS_SAME;
    // private static final MutableQuad[][] QUADS_DIFFERENT;
    // private static final MutableQuad[] QUADS_COLOUR;

    static {
        // Same sprite for all 3 sections
        QUADS_SAME = new MutableQuad[6];
        Tuple3f center = new Point3f(0.5f, 0.5f, 0.5f);
        Tuple3f radius = new Vector3f(0.25f, 0.5f, 0.25f);
        float[] uvsY = { 4 / 16f, 12 / 16f, 4 / 16f, 12 / 16f };
        float[] uvsXZ = { 4 / 16f, 12 / 16f, 0, 1 };
        for (EnumFacing face : EnumFacing.VALUES) {
            float[] uvs = face.getAxis() == Axis.Y ? uvsY : uvsXZ;
            MutableQuad quad = BCModelHelper.createFace(face, center, radius, uvs);
            quad.normalf(face.getFrontOffsetX(), face.getFrontOffsetY(), face.getFrontOffsetZ());
            quad.setDiffuse(quad.getVertex(0).normal());
            QUADS_SAME[face.ordinal()] = quad;
        }

        // Different sprite for any of the 3 sections
        // QUADS[0] = new MutableQuad[14];
    }

    @Override
    public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand) {
        return ImmutableList.of();
    }

    private static List<BakedQuad> getQuads(TextureAtlasSprite center, TextureAtlasSprite top, TextureAtlasSprite bottom, int colour, VertexFormat vf) {
        // TEMP!
        top = center;
        bottom = center;

        List<BakedQuad> quads = new ArrayList<>();

        // if (center == top && center == bottom) {
        addQuads(QUADS_SAME, quads, center, vf);
        // } else {
        // TODO: Differing sprite quads
        // }

        if (colour >= 0 && colour < 16) {
            // TODO: colours!
        }

        return quads;
    }

    private static void addQuads(MutableQuad[] from, List<BakedQuad> to, TextureAtlasSprite sprite, VertexFormat vf) {
        for (MutableQuad f : from) {
            if (f == null) {
                continue;
            }
            MutableQuad copy = new MutableQuad(f);
            for (MutableVertex v : copy.verticies()) {
                Point2f tex = v.tex();
                v.texf(sprite.getInterpolatedU(tex.x * 16), sprite.getInterpolatedV(tex.y * 16));
            }
            to.add(copy.toUnpacked(vf));
        }
    }

    @Override
    public boolean isAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return null;
    }

    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ItemCameraTransforms.DEFAULT;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return PipeItemOverride.PIPE_OVERRIDE;
    }

    private static class PipeItemOverride extends ItemOverrideList {
        public static final PipeItemOverride PIPE_OVERRIDE = new PipeItemOverride();

        public PipeItemOverride() {
            super(ImmutableList.of());
        }

        @Override
        public IBakedModel handleItemState(IBakedModel originalModel, ItemStack stack, World world, EntityLivingBase entity) {
            Item item = stack.getItem();
            TextureAtlasSprite center = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
            TextureAtlasSprite top = center;
            TextureAtlasSprite bottom = center;

            if (item instanceof IPipeItem) {
                PipeDefinition def = ((IPipeItem) item).getDefiniton();
                // TODO: pipe texture indexes for items!
                center = def.getSprite(0);
                top = center;
                bottom = center;
            }
            List<BakedQuad> quads = getQuads(center, top, bottom, stack.getMetadata(), DefaultVertexFormats.ITEM);
            return new ModelItemSimple(quads, ModelItemSimple.TRANSFORM_BLOCK);
        }
    }
}
