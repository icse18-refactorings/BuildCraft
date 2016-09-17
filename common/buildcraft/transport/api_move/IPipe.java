package buildcraft.transport.api_move;

import net.minecraft.item.EnumDyeColor;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import net.minecraftforge.common.capabilities.ICapabilityProvider;

public interface IPipe extends ICapabilityProvider {
    IPipeHolder getHolder();

    PipeDefinition getDefinition();

    PipeBehaviour getBehaviour();

    PipeFlow getFlow();

    EnumDyeColor getColour();

    void setColour(EnumDyeColor colour);

    TileEntity getConnectedTile(EnumFacing side);

    IPipe getConnectedPipe(EnumFacing side);

    ConnectedType getConnectedType(EnumFacing side);

    public enum ConnectedType {
        TILE,
        PIPE;
    }
}
