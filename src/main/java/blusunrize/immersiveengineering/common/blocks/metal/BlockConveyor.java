package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.tool.ConveyorHandler;
import blusunrize.immersiveengineering.api.tool.ConveyorHandler.ConveyorDirection;
import blusunrize.immersiveengineering.api.tool.ConveyorHandler.IConveyorBelt;
import blusunrize.immersiveengineering.api.tool.ConveyorHandler.IConveyorTile;
import blusunrize.immersiveengineering.common.blocks.BlockIETileProvider;
import blusunrize.immersiveengineering.common.blocks.ItemBlockIEBase;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import java.util.List;

public class BlockConveyor extends BlockIETileProvider<BlockTypes_Conveyor>
{
	public static final IUnlistedProperty<IConveyorBelt> ICONEYOR_PASSTHROUGH = new IUnlistedProperty<IConveyorBelt>()
	{
		@Override
		public String getName()
		{
			return "iconveyor_passthrough";
		}

		@Override
		public boolean isValid(IConveyorBelt value)
		{
			return true;
		}

		@Override
		public Class<IConveyorBelt> getType()
		{
			return IConveyorBelt.class;
		}

		@Override
		public String valueToString(IConveyorBelt value)
		{
			return ConveyorHandler.classRegistry.get(value.getClass()).toString();
		}
	};

	public static class ItemBlockConveyor extends ItemBlockIEBase
	{
		public ItemBlockConveyor(Block block)
		{
			super(block);
			setHasSubtypes(true);
		}

		@Override
		public int getMetadata(ItemStack stack)
		{
			String key = ItemNBTHelper.getString(stack, "conveyorType");
			if(key != null && !key.isEmpty())
				return key.hashCode();
			return 0;
		}
	}

	public BlockConveyor()
	{
		super("conveyor", Material.IRON, PropertyEnum.create("type", BlockTypes_Conveyor.class), ItemBlockConveyor.class, IEProperties.FACING_ALL, IEProperties.TILEENTITY_PASSTHROUGH, ICONEYOR_PASSTHROUGH);
		this.setHardness(3.0F);
		this.setResistance(15.0F);
		this.setBlockLayer(BlockRenderLayer.CUTOUT);
		this.setAllNotNormalBlock();
		lightOpacity = 0;
		ConveyorHandler.conveyorBlock = this;
	}

	@Override
	public boolean useCustomStateMapper()
	{
		return true;
	}

	@Override
	public boolean appendPropertiesToState()
	{
		return false;
	}

	@Override
	public void getSubBlocks(Item itemIn, CreativeTabs tab, List<ItemStack> list)
	{
		for(ResourceLocation key : ConveyorHandler.classRegistry.keySet())
		{
			ItemStack stack = new ItemStack(itemIn);
			ItemNBTHelper.setString(stack, "conveyorType", key.toString());
			list.add(stack);
		}
	}

	@Override
	public String getUnlocalizedName(ItemStack stack)
	{
		String subName = ItemNBTHelper.getString(stack, "conveyorType");
		return super.getUnlocalizedName() + "." + subName;
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		state = super.getExtendedState(state, world, pos);
		if(state instanceof IExtendedBlockState)
		{
			IExtendedBlockState ext = (IExtendedBlockState) state;
			TileEntity te = world.getTileEntity(pos);
			if(!(te instanceof TileEntityConveyorBelt))
				return state;
			state = ext.withProperty(ICONEYOR_PASSTHROUGH, ((TileEntityConveyorBelt) te).getConveyorSubtype());
		}
		return state;
	}

	@Override
	public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos)
	{
		state = super.getActualState(state, world, pos);
		TileEntity tile = world.getTileEntity(pos);
//		if(tile instanceof TileEntityConveyorBelt && !(tile instanceof TileEntityConveyorVertical))
//		{
//			for(int i=0; i<IEProperties.CONVEYORWALLS.length; i++)
//				state = state.withProperty(IEProperties.CONVEYORWALLS[i], ((TileEntityConveyorBelt)tile).renderWall(i));
//			state = state.withProperty(IEProperties.CONVEYORUPDOWN, ((TileEntityConveyorBelt)tile).transportUp?1: ((TileEntityConveyorBelt)tile).transportDown?2: 0);
//		}
		return state;
	}

	@Override
	public void onIEBlockPlacedBy(World world, BlockPos pos, IBlockState state, EnumFacing side, float hitX, float hitY, float hitZ, EntityLivingBase placer, ItemStack stack)
	{
		super.onIEBlockPlacedBy(world, pos, state, side, hitX, hitY, hitZ, placer, stack);
		TileEntity tile = world.getTileEntity(pos);
		if(tile instanceof TileEntityConveyorBelt && !(tile instanceof TileEntityConveyorVertical))
		{
			TileEntityConveyorBelt conveyor = (TileEntityConveyorBelt) tile;
			EnumFacing f = conveyor.facing;
			ResourceLocation rl = new ResourceLocation(ItemNBTHelper.getString(stack, "conveyorType"));
			IConveyorBelt subType = ConveyorHandler.getConveyor(rl, conveyor);
			conveyor.setConveyorSubtype(subType);
			tile = world.getTileEntity(pos.offset(f));
			TileEntity tileUp = world.getTileEntity(pos.offset(f).add(0, 1, 0));
			if(subType != null && (!(tile instanceof IConveyorTile) || ((IConveyorTile) tile).getFacing() == f.getOpposite()) && tileUp instanceof IConveyorTile && ((IConveyorTile) tileUp).getFacing() != f.getOpposite() && world.isAirBlock(pos.add(0, 1, 0)))
				subType.setConveyorDirection(ConveyorDirection.UP);
			tile = world.getTileEntity(pos.offset(f.getOpposite()).add(0,1,0));
//			if(tile instanceof TileEntityConveyorBelt&&!(tile instanceof TileEntityConveyorVertical) && ((TileEntityConveyorBelt)tile).facing==f)
//				conveyor.transportDown = true;
//			if(conveyor.transportUp && conveyor.transportDown)
//				conveyor.transportDown = false;

		}
	}

	@Override
	public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side)
	{
		TileEntity te = world.getTileEntity(pos);
		if(te instanceof TileEntityConveyorVertical)
			return side==((TileEntityConveyorVertical)te).facing;
		else if(te instanceof TileEntityConveyorBelt)
		{
			return side == EnumFacing.DOWN && (((TileEntityConveyorBelt) te).getConveyorSubtype() == null || ((TileEntityConveyorBelt) te).getConveyorSubtype().getConveyorDirection() == ConveyorDirection.HORIZONTAL);
		}
		return false;
	}

	@Override
	public TileEntity createNewTileEntity(World world, int meta)
	{
//		switch(BlockTypes_Conveyor.values()[meta])
//		{
//		case CONVEYOR:
//			return new TileEntityConveyorBelt();
//		case CONVEYOR_DROPPER:
//			return new TileEntityConveyorBelt(true);
//		case CONVEYOR_VERTICAL:
//			return new TileEntityConveyorVertical();
//		}
//		return null;
		return new TileEntityConveyorBelt();
	}

	@Override
	public boolean allowHammerHarvest(IBlockState blockState)
	{
		return true;
	}
	@Override
	public boolean isToolEffective(String type, IBlockState state)
	{
		return type.equals("IE_HAMMER")||super.isToolEffective(type, state);
	}
}