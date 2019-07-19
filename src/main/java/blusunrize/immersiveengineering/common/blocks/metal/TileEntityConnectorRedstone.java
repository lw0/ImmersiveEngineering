/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.TargetingInfo;
import blusunrize.immersiveengineering.api.energy.wires.IImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler;
import blusunrize.immersiveengineering.api.energy.wires.ImmersiveNetHandler.Connection;
import blusunrize.immersiveengineering.api.energy.wires.TileEntityImmersiveConnectable;
import blusunrize.immersiveengineering.api.energy.wires.WireType;
import blusunrize.immersiveengineering.api.energy.wires.redstone.IRedstoneConnector;
import blusunrize.immersiveengineering.api.energy.wires.redstone.RedstoneWireNetwork;
import blusunrize.immersiveengineering.client.models.IOBJModelCallback;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.*;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;

import static blusunrize.immersiveengineering.api.energy.wires.WireType.REDSTONE_CATEGORY;

public class TileEntityConnectorRedstone extends TileEntityImmersiveConnectable implements ITickable, IDirectionalTile, IRedstoneOutput, IHammerInteraction, IBlockBounds, IBlockOverlayText, IOBJModelCallback<IBlockState>, IRedstoneConnector
{
	public EnumFacing facing = EnumFacing.DOWN;

	//bit     0: [IO] 0 - input, 1 - output always
	//bit  5..4: [InMode] 0 - analog, 1 - analog inverted, 2 - digital, 3 - digital inverted
	//bit  7..6: [OutMode] 0 - analog, 1 - analog inverted, 2 - digital, 3 - digital inverted
	//bit 11..8: [InThreshold]
	//bit 15..12:[OutThreshold]
	public int ioMode = 0;
	public int redstoneChannel = 0;
	public boolean rsDirty = false;

	protected RedstoneWireNetwork wireNetwork = new RedstoneWireNetwork().add(this);
	private boolean refreshWireNetwork = false;
	// ONLY EVER USE THIS ON THE CLIENT! I don't want to sync the entire network...
	private int outputClient = -1;


	public void changeIO()
	{
		ioMode = ioMode ^ 0x0001;
	}
	public int getIO()
	{
		return ioMode & 0x0001;
	}
	public boolean isOutput()
	{
		return getIO() != 0;
	}

	public void changeInMode()
	{
		ioMode = (ioMode & ~0x0030) | ((ioMode + 0x10) & 0x0030);
	}
	public int getInMode()
	{
		return (ioMode & 0x0030) >> 4;
	}
	public boolean isInInverted()
	{
		return (getInMode() & 0x1) != 0;
	}
	public boolean isInDigital()
	{
		return (getInMode() & 0x2) != 0;
	}

	public void changeOutMode()
	{
		ioMode = (ioMode & ~0x00C0) | ((ioMode + 0x40) & 0x00C0);
	}
	public int getOutMode()
	{
		return (ioMode & 0x00C0) >> 6;
	}
	public boolean isOutInverted()
	{
		return (getOutMode() & 0x1) != 0;
	}
	public boolean isOutDigital()
	{
		return (getOutMode() & 0x2) != 0;
	}

  	public void changeInThreshold()
	{
		ioMode = (ioMode & ~0x0f00) | ((ioMode + 0x100) & 0x0f00);
	}
	public int getInThreshold()
	{
		return (ioMode & 0x0f00) >> 8;
	}

	public void changeOutThreshold()
	{
		ioMode = (ioMode & ~0xf000) | ((ioMode + 0x1000) & 0xf000);
	}
	public int getOutThreshold()
	{
		return (ioMode & 0xf000) >> 12;
	}

	public void changeMode()
	{
		if(isOutput())
			changeOutMode();
		else
			changeInMode();
	}
	public int getMode()
	{
		if(isOutput())
			return getOutMode();
		else
			return getInMode();
	}

  	public void changeThreshold()
	{
		if(isOutput())
			changeOutThreshold();
		else
			changeInThreshold();
	}
	public int getThreshold()
	{
		if(isOutput())
			return getOutThreshold();
		else
			return getInThreshold();
	}

	public int transformInSignal(int power)
	{
		int effectivePower = isInInverted()? 15-power : power;
		if(effectivePower < getInThreshold())
			effectivePower = 0;
		if(isInDigital() && effectivePower != 0)
			effectivePower = 15;
		return effectivePower;
	}

	public int transformOutSignal(int power)
	{
		int effectivePower = isOutInverted()? 15-power : power;
		if(effectivePower < getOutThreshold())
			effectivePower = 0;
		if(isOutDigital() && effectivePower != 0)
			effectivePower = 15;
		return effectivePower;
	}

	public void changeChannel()
	{
		redstoneChannel = (redstoneChannel + 1) % 16;
	}

	@Override
	public void update()
	{
		if(hasWorld()&&!world.isRemote&&!refreshWireNetwork)
		{
			refreshWireNetwork = true;
			wireNetwork.removeFromNetwork(null);
		}
		if(hasWorld()&&!world.isRemote&&rsDirty)
			wireNetwork.updateValues();
	}

	public boolean isRSOutput()
	{
		return isOutput();
	}

	@Override
	public int getStrongRSOutput(IBlockState state, EnumFacing side)
	{
		if(!isRSOutput()||side!=this.facing.getOpposite())
			return 0;
		if(world.isRemote)
			return outputClient;
		return transformOutSignal(wireNetwork!=null? wireNetwork.getPowerOutput(redstoneChannel) : 0);
	}

	@Override
	public int getWeakRSOutput(IBlockState state, EnumFacing side)
	{
		if(!isRSOutput())
			return 0;
		if(world.isRemote)
			return outputClient;
		return transformOutSignal(wireNetwork!=null? wireNetwork.getPowerOutput(redstoneChannel) : 0);
	}

	@Override
	public boolean canConnectRedstone(IBlockState state, EnumFacing side)
	{
		return true;
	}

	@Override
	public void setNetwork(RedstoneWireNetwork net)
	{
		wireNetwork = net;
	}

	@Override
	public RedstoneWireNetwork getNetwork()
	{
		return wireNetwork;
	}

	@Override
	public void onChange()
	{
		if(!isInvalid()&&isRSOutput())
		{
			markDirty();
			IBlockState stateHere = world.getBlockState(pos);
			markContainingBlockForUpdate(stateHere);
			markBlockForUpdate(pos.offset(facing), stateHere);
		}
	}

	@Override
	public World getConnectorWorld()
	{
		return getWorld();
	}

	public boolean isRSInput()
	{
		return !isOutput();
	}

	@Override
	public void updateInput(byte[] signals)
	{
		if(isRSInput())
			signals[redstoneChannel] = (byte)Math.max(transformInSignal(getLocalRS()), signals[redstoneChannel]);
		rsDirty = false;
	}

	protected int getLocalRS()
	{
		int val = world.getRedstonePowerFromNeighbors(pos);
		if(val==0)
		{
			for(EnumFacing f : EnumFacing.HORIZONTALS)
			{
				IBlockState state = world.getBlockState(pos.offset(f));
				if(state.getBlock()==Blocks.REDSTONE_WIRE&&state.getValue(BlockRedstoneWire.POWER) > val)
					val = state.getValue(BlockRedstoneWire.POWER);
			}
		}
		return val;
	}

	private int classifyHit(float hitX, float hitY, float hitZ)
	{
		// 0 - channel area (lower ring), 1 - mode area (upper ring), 2 - threshold area (cube on top)
		float channelLimit = 0.16f;
		float modeLimit = 0.32f;
		switch(facing.getOpposite())
		{
			case UP:
				return (hitY < modeLimit)? (hitY < channelLimit)? 0 : 1 : 2;
			case DOWN:
				return (hitY > (1-modeLimit))? (hitY > (1-channelLimit))? 0 : 1 : 2;
			case SOUTH:
				return (hitZ < modeLimit)? (hitZ < channelLimit)? 0 : 1 : 2;
			case NORTH:
				return (hitZ > (1-modeLimit))? (hitZ > (1-channelLimit))? 0 : 1 : 2;
			case EAST:
				return (hitX < modeLimit)? (hitX < channelLimit)? 0 : 1 : 2;
			case WEST:
				return (hitX > (1-modeLimit))? (hitX > (1-channelLimit))? 0 : 1 : 2;
		}
		return 0;
	}

	@Override
	public boolean hammerUseSide(EnumFacing side, EntityPlayer player, float hitX, float hitY, float hitZ)
	{
		if(player.isSneaking() && classifyHit(hitX, hitY, hitZ) == 2)
			changeThreshold();
		else if(player.isSneaking() && classifyHit(hitX, hitY, hitZ) == 1)
			changeMode();
		else if(player.isSneaking() && classifyHit(hitX, hitY, hitZ) == 0)
			changeChannel();
		else
			changeIO();
		markDirty();
		wireNetwork.updateValues();
		onChange();
		this.markContainingBlockForUpdate(null);
		world.addBlockEvent(getPos(), this.getBlockType(), 254, 0);
		return true;
	}

	@Override
	public boolean canConnectCable(WireType cableType, TargetingInfo target, Vec3i offset)
	{
		if(!REDSTONE_CATEGORY.equals(cableType.getCategory()))
			return false;
		return limitType==null||limitType==cableType;
	}

	@Override
	public void connectCable(WireType cableType, TargetingInfo target, IImmersiveConnectable other)
	{
		super.connectCable(cableType, target, other);
		RedstoneWireNetwork.updateConnectors(pos, world, wireNetwork);
	}

	@Override
	public void removeCable(@Nullable ImmersiveNetHandler.Connection connection)
	{
		super.removeCable(connection);
		wireNetwork.removeFromNetwork(this);
	}

	@Override
	public EnumFacing getFacing()
	{
		return this.facing;
	}

	@Override
	public void setFacing(EnumFacing facing)
	{
		this.facing = facing;
	}

	@Override
	public int getFacingLimitation()
	{
		return 0;
	}

	@Override
	public boolean mirrorFacingOnPlacement(EntityLivingBase placer)
	{
		return true;
	}

	@Override
	public boolean canHammerRotate(EnumFacing side, float hitX, float hitY, float hitZ, EntityLivingBase entity)
	{
		return false;
	}

	@Override
	public boolean canRotate(EnumFacing axis)
	{
		return false;
	}


	@Override
	public void writeCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.writeCustomNBT(nbt, descPacket);
		nbt.setInteger("facing", facing.ordinal());
		nbt.setInteger("ioMode", ioMode);
		nbt.setInteger("redstoneChannel", redstoneChannel);
		nbt.setInteger("output", wireNetwork!=null?wireNetwork.getPowerOutput(redstoneChannel): 0);
	}

	@Override
	public void readCustomNBT(NBTTagCompound nbt, boolean descPacket)
	{
		super.readCustomNBT(nbt, descPacket);
		facing = EnumFacing.byIndex(nbt.getInteger("facing"));
		ioMode = nbt.getInteger("ioMode");
		redstoneChannel = nbt.getInteger("redstoneChannel");
		outputClient = nbt.getInteger("output");
	}

	@Override
	public Vec3d getConnectionOffset(Connection con)
	{
		EnumFacing side = facing.getOpposite();
		double conRadius = con.cableType.getRenderDiameter()/2;
		return new Vec3d(.5-conRadius*side.getXOffset(), .5-conRadius*side.getYOffset(), .5-conRadius*side.getZOffset());
	}

	@Override
	public void onConnectivityUpdate(BlockPos pos, int dimension)
	{
		refreshWireNetwork = false;
	}

	@SideOnly(Side.CLIENT)
	private AxisAlignedBB renderAABB;

	@SideOnly(Side.CLIENT)
	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		int inc = getRenderRadiusIncrease();
		return new AxisAlignedBB(this.pos.getX()-inc, this.pos.getY()-inc, this.pos.getZ()-inc, this.pos.getX()+inc+1, this.pos.getY()+inc+1, this.pos.getZ()+inc+1);
	}

	int getRenderRadiusIncrease()
	{
		return WireType.REDSTONE.getMaxLength();
	}

	@Override
	public float[] getBlockBounds()
	{
		float length = .625f;
		float wMin = .3125f;
		float wMax = .6875f;
		switch(facing.getOpposite())
		{
			case UP:
				return new float[]{wMin, 0, wMin, wMax, length, wMax};
			case DOWN:
				return new float[]{wMin, 1-length, wMin, wMax, 1, wMax};
			case SOUTH:
				return new float[]{wMin, wMin, 0, wMax, wMax, length};
			case NORTH:
				return new float[]{wMin, wMin, 1-length, wMax, wMax, 1};
			case EAST:
				return new float[]{0, wMin, wMin, length, wMax, wMax};
			case WEST:
				return new float[]{1-length, wMin, wMin, 1, wMax, wMax};
		}
		return new float[]{0, 0, 0, 1, 1, 1};
	}

	@SideOnly(Side.CLIENT)
	@Override
	public boolean shouldRenderGroup(IBlockState object, String group)
	{
		if("io_out".equals(group))
			return isOutput();
		else if("io_in".equals(group))
			return !isOutput();
		return true;
	}

	@SideOnly(Side.CLIENT)
	@Override
	public int getRenderColour(IBlockState object, String group)
	{
		if("coloured".equals(group))
			return 0xff000000|EnumDyeColor.byMetadata(this.redstoneChannel).getColorValue();
		return 0xffffffff;
	}

	@Override
	public String getCacheKey(IBlockState object)
	{
		return redstoneChannel+";"+ioMode;
	}

	@Override
	public String[] getOverlayText(EntityPlayer player, RayTraceResult mop, boolean hammer)
	{
		if(!hammer)
			return null;
		String channelString = I18n.format("item.fireworksCharge."+EnumDyeColor.byMetadata(redstoneChannel).getTranslationKey());
		String ioString = I18n.format(Lib.DESC_INFO+"blockSide.io."+getIO());
		String thresholdString = (getThreshold() > 0)? "("+getThreshold()+"+)" : "";
		return new String[]{
				I18n.format(Lib.DESC_INFO+"redstoneChannel", channelString),
				I18n.format(Lib.DESC_INFO+"redstoneMode."+getMode(), ioString, thresholdString)
		};
	}

	@Override
	public boolean useNixieFont(EntityPlayer player, RayTraceResult mop)
	{
		return false;
	}

	@Override
	public boolean moveConnectionTo(Connection c, BlockPos newEnd)
  	{
		return true;
	}
}
