package xyz.aadev.aalib.common.tileentities;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import xyz.aadev.aalib.api.common.integrations.waila.IWailaHeadMessage;
import xyz.aadev.aalib.api.common.util.IOrientable;
import xyz.aadev.aalib.api.common.util.IRotatable;
import xyz.aadev.aalib.common.util.TileHelper;
import xyz.aadev.aalib.common.util.WorldHelper;

import javax.annotation.Nullable;
import java.util.List;

public abstract class TileEntityBase extends TileEntity implements IWailaHeadMessage, IOrientable, IRotatable {
    private String customName;
    private int renderedFragment = 0;
    private EnumFacing forward = EnumFacing.NORTH;

    /**
     * Check if the player can interact with the tile entity
     *
     * @return true if player can interact, false otherwise
     */
    public boolean isUseableByPlayer(EntityPlayer entityplayer) {

        BlockPos position = this.getPos();

        if (worldObj.getTileEntity(position) != this)
            return false;

        return entityplayer.getDistanceSq((double) position.getX() + 0.5D, (double) position.getY() + 0.5D,
                (double) position.getZ() + 0.5D) <= 64D;
    }

    /*
    GUI management
     */

    /**
     * Check if the tile entity has a GUI or not
     * Override in derived classes to return true if your tile entity got a GUI
     *
     * @return true if gui can be opened, false otherwise
     */
    public boolean canOpenGui(World world, BlockPos posistion, IBlockState state) {
        return false;
    }

    /**
     * Open the specified GUI
     *
     * @param player the player currently interacting with your block/tile entity
     * @param guiId  the GUI to open
     * @return true if the GUI was opened, false otherwise
     */
    public boolean openGui(Object mod, EntityPlayer player, int guiId) {

        player.openGui(mod, guiId, this.worldObj, this.pos.getX(), this.pos.getY(), this.pos.getZ());
        return true;
    }

    /**
     * Returns a Server side Container to be displayed to the user.
     *
     * @param guiId  the GUI ID mumber
     * @param player the player currently interacting with your block/tile entity
     * @return A GuiScreen/Container to be displayed to the user, null if none.
     */
    public Object getServerGuiElement(int guiId, EntityPlayer player) {
        return null;
    }

    /**
     * Returns a Container to be displayed to the user. On the client side, this
     * needs to return a instance of GuiScreen On the server side, this needs to
     * return a instance of Container
     *
     * @param guiId  the GUI ID mumber
     * @param player the player currently interacting with your block/tile entity
     * @return A GuiScreen/Container to be displayed to the user, null if none.
     */
    public Object getClientGuiElement(int guiId, EntityPlayer player) {
        return null;
    }

    /*
    TileEntity synchronization
     */

    @Override
    public void readFromNBT(NBTTagCompound nbtTagCompound) {

        super.readFromNBT(nbtTagCompound);
        this.syncDataFrom(nbtTagCompound, SyncReason.FullSync);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTagCompound) {

        this.syncDataTo(super.writeToNBT(nbtTagCompound), SyncReason.FullSync);
        return nbtTagCompound;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound nbtTagCompound) {

        super.readFromNBT(nbtTagCompound);
        this.syncDataFrom(nbtTagCompound, SyncReason.NetworkUpdate);
    }

    @Override
    public NBTTagCompound getUpdateTag() {

        NBTTagCompound data = super.getUpdateTag();

        this.syncDataTo(data, SyncReason.NetworkUpdate);
        return data;
    }

    @Override
    public final void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        this.syncDataFrom(packet.getNbtCompound(), SyncReason.NetworkUpdate);
        worldObj.markBlockRangeForRenderUpdate(this.pos, this.pos);
        markForUpdate();
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {

        NBTTagCompound nbtTagCompound = new NBTTagCompound();

        this.syncDataTo(nbtTagCompound, SyncReason.NetworkUpdate);
        return new SPacketUpdateTileEntity(this.getPos(), 0, nbtTagCompound);
    }

    /**
     * Sync tile entity data from the given NBT compound
     *
     * @param nbtTagCompound the data
     * @param syncReason     the reason why the synchronization is necessary
     */
    protected void syncDataFrom(NBTTagCompound nbtTagCompound, SyncReason syncReason) {
        this.customName = nbtTagCompound.hasKey("CustomName") ? nbtTagCompound.getString("CustomName") : null;

        if (canBeRotated()) {
            this.forward = EnumFacing.values()[nbtTagCompound.getInteger("forward")];
        }
    }

    /**
     * Sync tile entity data to the given NBT compound
     *
     * @param nbtTagCompound the data
     * @param syncReason     the reason why the synchronization is necessary
     */
    protected void syncDataTo(NBTTagCompound nbtTagCompound, SyncReason syncReason) {
        if (this.customName != null)
            nbtTagCompound.setString("CustomName", this.customName);

        if (canBeRotated()) {
            nbtTagCompound.setInteger("forward", this.forward.ordinal());
        }
    }

    @Override
    public void onChunkUnload() {
        if (!tileEntityInvalid)
            this.invalidate();
    }

    /*
     Chunk and block updates
     */

    public void markChunkDirty() {
        this.worldObj.markChunkDirty(this.getPos(), this);
    }

    /**
     * Notify neighbouring blocks of update
     */
    public void callNeighborBlockChange() {
        this.worldObj.notifyNeighborsOfStateChange(this.getPos(), this.getBlockType());
    }

    /**
     * Notify block of update, keeping current state
     */
    public void notifyBlockUpdate() {
        WorldHelper.notifyBlockUpdate(this.worldObj, this.getPos(), null, null);
    }

    /**
     * Notify block of update, changing states
     *
     * @param oldState the block state to change from
     * @param newState the block state to change to
     */
    public void notifyBlockUpdate(IBlockState oldState, IBlockState newState) {
        WorldHelper.notifyBlockUpdate(this.worldObj, this.getPos(), oldState, newState);
    }

    public void nofityTileEntityUpdate() {
        this.markDirty();
        WorldHelper.notifyBlockUpdate(this.worldObj, this.getPos(), null, null);
    }

    public void markForUpdate() {
        if (this.renderedFragment > 0) {
            this.renderedFragment |= 0x1;
        } else if (this.worldObj != null) {
            notifyBlockUpdate();
            callNeighborBlockChange();
        }
    }

    public void markForLightUpdate() {
        if (this.worldObj.isRemote) {
            notifyBlockUpdate();
        }

        this.worldObj.checkLightFor(EnumSkyBlock.BLOCK, this.pos);
    }

    /*
    Orientation handling
     */
    @Override
    public boolean canBeRotated() {
        return false;
    }

    @Override
    public EnumFacing getForward() {
        return forward;
    }

    @Override
    public void setOrientation(EnumFacing forward) {
        this.forward = forward;
        markDirty();
        markForUpdate();
    }

    @Override
    public EnumFacing getDirection() {
        return getForward();
    }

    /**
     * Get the custom name for the tile entity
     *
     * @return string
     */
    public String getCustomName() {
        return this.customName;
    }

    /*
    Name handling
     */

    /**
     * Set the custom name for the tile entity
     *
     * @param customName the custom name
     */
    public void setCustomName(String customName) {
        this.customName = customName;
    }

    /**
     * Returns whether the tile entity has a custom name set
     *
     * @return boolean
     */
    public boolean hasCustomName() {
        return (this.customName != null) && (this.customName.length() > 0);
    }

    /**
     * Get the unlocalized name for the tile entity
     *
     * @return string
     */
    public String getUnlocalizedName() {
        Item item = Item.getItemFromBlock(worldObj.getBlockState(this.pos).getBlock());
        ItemStack itemStack = new ItemStack(item, 1, getBlockMetadata());

        return itemStack.getUnlocalizedName() + ".name";
    }

    public void setName(String name) {
        this.customName = name;
    }

    /*
    Waila integration
     */
    @Override
    public List<String> getWailaHeadToolTip(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        if (customName != null)
            currentTip.add(String.format("%s%s%s", TextFormatting.BLUE, TextFormatting.ITALIC, customName));

        return currentTip;
    }

    /*
    Other
     */
    public void dropItems() {
        TileHelper.DropItems(this);
    }

    public enum SyncReason {
        FullSync,       // full sync from storage
        NetworkUpdate   // update from the other side
    }
}
