package no.runsafe.worldguardbridge;

import com.google.common.collect.Sets;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.bukkit.selections.CuboidSelection;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.storage.StorageException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import no.runsafe.framework.api.ILocation;
import no.runsafe.framework.api.IServer;
import no.runsafe.framework.api.IWorld;
import no.runsafe.framework.api.event.plugin.IPluginEnabled;
import no.runsafe.framework.api.log.IConsole;
import no.runsafe.framework.api.log.IDebug;
import no.runsafe.framework.api.player.IPlayer;
import no.runsafe.framework.internal.wrapper.ObjectUnwrapper;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.geom.Rectangle2D;
import java.util.*;

public class WorldGuardInterface implements IPluginEnabled, IRegionControl
{
	public WorldGuardInterface(IDebug console, IConsole console1, IServer server)
	{
		this.debugger = console;
		this.console = console1;
		this.server = server;
	}

	@Override
	public void OnPluginEnabled()
	{
		if (!serverHasWorldGuard())
			console.logError("Could not find WorldGuard on this server!");
	}

	@Override
	public boolean serverHasWorldGuard()
	{
		if (this.worldGuard == null)
			this.worldGuard = server.getPlugin("WorldGuard");

		return this.worldGuard != null;
	}

	@Override
	public boolean playerCanBuildHere(IPlayer player, ILocation location)
	{
		return worldGuard.canBuild(ObjectUnwrapper.convert(player), (Location) ObjectUnwrapper.convert(location));
	}

	@Override
	public boolean isInPvPZone(IPlayer player)
	{
		ApplicableRegionSet set = getRegions(player);
		return set != null && set.size() != 0 && set.testState(unwrap(player), DefaultFlag.PVP);
	}

	@Override
	public String getCurrentRegion(IPlayer player)
	{
		ApplicableRegionSet set = getRegions(player);
		if (set.size() == 0)
			return null;
		StringBuilder sb = new StringBuilder();
		for (ProtectedRegion r : set)
		{
			if (sb.length() > 0)
				sb.append(";");
			sb.append(r.getId());
		}
		return sb.toString();
	}

	@Override
	public ProtectedRegion getRegion(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;
		if (world == null)
			return null;
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager == null)
			return null;
		return regionManager.getRegion(name);
	}

	@Override
	public List<IPlayer> getPlayersInRegion(IWorld world, String regionName)
	{
		List<IPlayer> worldPlayers = world.getPlayers();
		List<IPlayer> regionPlayers = new ArrayList<>();

		for (IPlayer player : worldPlayers)
		{
			List<String> playerRegions = this.getApplicableRegions(player);
			if (playerRegions != null && playerRegions.contains(regionName))
				regionPlayers.add(player);
		}

		return regionPlayers;
	}

	@Override
	public List<String> getRegionsAtLocation(ILocation location)
	{
		ApplicableRegionSet set = getRegions(location);

		if (set == null || set.size() == 0)
			return null;

		ArrayList<String> regions = new ArrayList<>();
		for (ProtectedRegion region : set)
			regions.add(region.getId());

		return regions;
	}

	@Override
	public List<String> getApplicableRegions(IPlayer player)
	{
		ApplicableRegionSet set = getRegions(player);
		if (set == null || set.size() == 0)
			return Collections.emptyList();

		ArrayList<String> regions = new ArrayList<>();
		for (ProtectedRegion r : set)
			regions.add(r.getId());

		return regions;
	}

	/**
	 * Gets a list of all regions in a world with owners.
	 * Will not return owners that haven't been converted from being stored as usernames to UUIDs.
	 * @param world The world.
	 * @return Every region name with a set of its owners.
	 */
	@Override
	public Map<String, Set<IPlayer>> getAllRegionsWithOwnersInWorld(IWorld world)
	{
		HashMap<String, Set<IPlayer>> result = new HashMap<>();
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		Map<String, ProtectedRegion> regions = regionManager.getRegions();
		for (String region : regions.keySet())
			result.put(region, getOwnerPlayers(world, region));
		return result;
	}

	@Override
	public ILocation getRegionLocation(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;

		ProtectedRegion region = worldGuard.getRegionManager(ObjectUnwrapper.convert(world)).getRegion(name);
		if (region == null)
			return null;
		BlockVector point = region.getMaximumPoint();
		return world.getLocation(point.getX(), point.getY(), point.getZ());
	}

	/**
	 * Gets a list of plot owner names world guard has stored.
	 * If the player is stored by UUID then their username will not be stored.
	 * Players who have been converted from being stored as user names to UUIDs will not be returned.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of owner names.
	 */
	@Override
	@Nullable
	public Set<String> getOwners(IWorld world, String name)
	{
		ProtectedRegion region = getRegionByName(world, name);
		return region == null ? null : region.getOwners().getPlayers();
	}

	/**
	 * Gets a list of plot owner unique IDs world guard has stored.
	 * If the player is stored by their username they will not have a UUID stored.
	 * Players who have not been converted from being stored by their user names to UUIDs will not be returned.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of owner unique IDs.
	 */
	@Override
	public Set<UUID> getOwnerUniqueIds(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;

		ProtectedRegion region = getRegionByName(world, name);
		return region == null ? new HashSet<>() : region.getOwners().getUniqueIds();
	}

	/**
	 * Gets a list of plot owners in the form of IPlayers.
	 * The player objects are created from the UUIDs from the getOwnerUniqueIds method.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of plot owners.
	 */
	@Override
	public Set<IPlayer> getOwnerPlayers(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;

		Set<UUID> owners = getOwnerUniqueIds(world, name);
		Set<IPlayer> ownerPlayers = new HashSet<>();
		for (UUID playerUUID : owners)
			ownerPlayers.add(server.getPlayer(playerUUID));

		return ownerPlayers;
	}

	/**
	 * Gets a list of plot member names world guard has stored.
	 * If the player is stored by UUID then their username will not be stored.
	 * Players who have been converted from being stored as user names to UUIDs will not be returned.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of member names.
	 */
	@Override
	public Set<String> getMembers(IWorld world, String name)
	{
		ProtectedRegion region = getRegionByName(world, name);
		return region == null ? null : Sets.newHashSet(region.getMembers().getPlayers());
	}

	/**
	 * Gets a list of plot member unique IDs world guard has stored.
	 * If the player is stored by their username they will not have a UUID stored.
	 * Players who have not been converted from being stored by their user names to UUIDs will not be returned.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of member unique IDs.
	 */
	@Override
	public Set<UUID> getMemberUniqueIds(IWorld world, String name)
	{
		ProtectedRegion region = getRegionByName(world, name);
		return region == null ? null : Sets.newHashSet(region.getMembers().getUniqueIds());
	}

	/**
	 * Gets a list of plot members in the form of IPlayers.
	 * The player objects are created from the UUIDs from the getMemberUniqueIds method.
	 * @param world The world the region is in.
	 * @param name Textual identifier of what the region is called.
	 * @return Set of plot members.
	 */
	@Override
	public Set<IPlayer> getMemberPlayers(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;

		Set<UUID> owners = getMemberUniqueIds(world, name);
		Set<IPlayer> memberPlayers = new HashSet<>();
		for (UUID playerUUID : owners)
			memberPlayers.add(server.getPlayer(playerUUID));

		return memberPlayers;
	}

	@Override
	public List<String> getOwnedRegions(IPlayer player, IWorld world)
	{
		if (world == null || player == null)
			return null;
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		ArrayList<String> regions = new ArrayList<>();
		Map<String, ProtectedRegion> regionSet = regionManager.getRegions();
		for (String region : regionSet.keySet())
			if (regionSet.get(region).getOwners().contains(player.getUniqueId()))
				regions.add(region);
		return regions;
	}

	@Override
	public List<String> getMemberRegions(IPlayer player, IWorld world)
	{
		if (world == null || player == null)
			return null;
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		ArrayList<String> regions = new ArrayList<>();
		Map<String, ProtectedRegion> regionSet = regionManager.getRegions();
		for (String region : regionSet.keySet())
			if (regionSet.get(region).getMembers().contains(player.getUniqueId()))
				regions.add(region);
		return regions;
	}

	@Override
	public List<String> getRegionsInWorld(IWorld world)
	{
		if (world == null)
			return new ArrayList<>(0);

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		return new ArrayList<>(regionManager.getRegions().keySet());
	}

	@Override
	public Map<String, Rectangle2D> getRegionRectanglesInWorld(IWorld world)
	{
		if (!serverHasWorldGuard() || world == null || worldGuard == null)
			return null;
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		Map<String, ProtectedRegion> regionSet = regionManager.getRegions();
		HashMap<String, Rectangle2D> result = new HashMap<>();
		for (String regionName : regionSet.keySet())
		{
			Rectangle2D.Double area = new Rectangle2D.Double();
			ProtectedRegion region = regionSet.get(regionName);
			BlockVector min = region.getMinimumPoint();
			BlockVector max = region.getMaximumPoint();
			area.setRect(min.getX(), min.getZ(), max.getX() - min.getX(), max.getZ() - min.getZ());
			result.put(regionName, area);
		}
		return result;
	}

	@Override
	public boolean deleteRegion(IWorld world, String name)
	{
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager.getRegion(name) == null)
			return false;
		regionManager.removeRegion(name);
		return this.saveRegionManager(regionManager);
	}

	@Override
	public boolean createRegion(IPlayer owner, IWorld world, String name, @Nonnull ILocation pos1, @Nonnull ILocation pos2)
	{
		if (world == null || worldGuard == null)
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager.hasRegion(name))
			return false;

		CuboidSelection selection = new CuboidSelection(
			ObjectUnwrapper.convert(world),
			Objects.requireNonNull(ObjectUnwrapper.convert(pos1)),
			(Location) Objects.requireNonNull(ObjectUnwrapper.convert(pos2))
		);
		BlockVector min = selection.getNativeMinimumPoint().toBlockVector();
		BlockVector max = selection.getNativeMaximumPoint().toBlockVector();
		ProtectedRegion region = new ProtectedCuboidRegion(name, min, max);
		region.getOwners().addPlayer(owner.getUniqueId());
		regionManager.addRegion(region);
		this.saveRegionManager(regionManager);
		return regionManager.hasRegion(name);
	}

	@Override
	public boolean redefineRegion(IWorld world, String name, @Nonnull ILocation pos1, @Nonnull ILocation pos2)
	{
		if (world == null || worldGuard == null)
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		ProtectedRegion existing = regionManager.getRegion(name);
		if (existing == null)
		{
			debugger.debugFine("Region manager does not know anything about the region %s in world %s!", name, world.getName());
			return false;
		}
		CuboidSelection selection = new CuboidSelection(
			ObjectUnwrapper.convert(world),
			Objects.requireNonNull(ObjectUnwrapper.convert(pos1)),
			(Location) Objects.requireNonNull(ObjectUnwrapper.convert(pos2))
		);
		BlockVector min = selection.getNativeMinimumPoint().toBlockVector();
		BlockVector max = selection.getNativeMaximumPoint().toBlockVector();
		ProtectedRegion region = new ProtectedCuboidRegion(name, min, max);


		// Copy details from the old region to the new one
		region.setMembers(existing.getMembers());
		region.setOwners(existing.getOwners());
		region.setFlags(existing.getFlags());
		region.setPriority(existing.getPriority());
		try
		{
			region.setParent(existing.getParent());
		}
		catch (ProtectedRegion.CircularInheritanceException ignore)
		{
			// This should not be thrown
		}

		regionManager.addRegion(region); // Replace region
		return this.saveRegionManager(regionManager);
	}

	@Override
	public boolean renameRegion(IWorld world, String currentName, String newName)
	{
		if (world == null || !serverHasWorldGuard())
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		ProtectedRegion existing = regionManager.getRegion(currentName);
		if (existing == null)
		{
			debugger.debugFine("Region manager does not know anything about the region %s in world %s!", currentName, world.getName());
			return false;
		}
		ProtectedRegion region = new ProtectedCuboidRegion(newName, existing.getMinimumPoint(), existing.getMaximumPoint());

		// Copy details from the old region to the new one
		region.setMembers(existing.getMembers());
		region.setOwners(existing.getOwners());
		region.setFlags(existing.getFlags());
		region.setPriority(existing.getPriority());

		// Replace region
		regionManager.removeRegion(currentName);
		regionManager.addRegion(region);
		return this.saveRegionManager(regionManager);
	}

	@Override
	public boolean addMemberToRegion(IWorld world, String name, IPlayer player)
	{
		if (!serverHasWorldGuard())
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager == null)
			return false;
		ProtectedRegion region = regionManager.getRegion(name);
		if (region == null)
			return false;

		DefaultDomain members = region.getMembers();
		if (!members.contains(player.getUniqueId()))
		{
			members.addPlayer(player.getUniqueId());
			return this.saveRegionManager(regionManager);
		}
		return false;
	}

	@Override
	public boolean removeMemberFromRegion(IWorld world, String name, IPlayer player)
	{
		if (!serverHasWorldGuard())
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager == null)
			return false;
		ProtectedRegion region = regionManager.getRegion(name);
		if (region == null)
			return false;
		DefaultDomain members = region.getMembers();
		if (members.contains(player.getUniqueId()))
		{
			members.removePlayer(player.getUniqueId());
			return this.saveRegionManager(regionManager);
		}
		return false;
	}

	@Override
	public boolean addOwnerToRegion(IWorld world, String name, IPlayer player)
	{
		if (!serverHasWorldGuard())
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		if (regionManager == null)
			return false;
		ProtectedRegion region = regionManager.getRegion(name);
		if (region == null)
			return false;
		DefaultDomain owners = region.getOwners();
		if (!owners.contains(player.getUniqueId()))
		{
			owners.addPlayer(player.getUniqueId());
			return this.saveRegionManager(regionManager);
		}
		return false;
	}

	@Override
	public boolean removeOwnerFromRegion(IWorld world, String name, IPlayer player)
	{
		if (!serverHasWorldGuard())
			return false;

		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(world));
		ProtectedRegion region = regionManager.getRegion(name);
		if (region == null)
			return false;
		DefaultDomain owners = region.getOwners();
		if (owners.contains(player.getUniqueId()))
		{
			owners.removePlayer(player.getUniqueId());
			return this.saveRegionManager(regionManager);
		}
		return false;
	}

	@Override
	public Rectangle2D getRectangle(IWorld world, String name)
	{
		if (!serverHasWorldGuard())
			return null;
		ProtectedRegion region = worldGuard.getRegionManager(ObjectUnwrapper.convert(world)).getRegion(name);
		if (region == null)
			return null;
		Rectangle2D.Double area = new Rectangle2D.Double();
		BlockVector min = region.getMinimumPoint();
		BlockVector max = region.getMaximumPoint();
		area.setRect(min.getX(), min.getZ(), max.getX() - min.getX(), max.getZ() - min.getZ());
		return area;
	}

	/**
	 * Saves the region manager.
	 * Catches any StorageExceptions it might throw and outputs them to the console.
	 * @param regionManager Thing to save.
	 * @return True if successful, false if unsuccessful.
	 */
	private boolean saveRegionManager(RegionManager regionManager)
	{
		try
		{
			regionManager.save();
			return true;
		}
		catch (StorageException e)
		{
			console.logException(e);
			return false;
		}
	}

	private LocalPlayer unwrap(IPlayer player)
	{
		return player == null ? null : worldGuard.wrapPlayer(ObjectUnwrapper.convert(player));
	}

	private ApplicableRegionSet getRegions(IPlayer player)
	{
		if (player == null || !serverHasWorldGuard())
			return null;
		ILocation location = player.getLocation();
		if (location == null)
			return null;
		return getRegions(location);
	}

	private ApplicableRegionSet getRegions(ILocation location)
	{
		Location wgLocation = ObjectUnwrapper.convert(location);
		if (wgLocation == null || !serverHasWorldGuard())
			return null;
		RegionManager regionManager = worldGuard.getRegionManager(ObjectUnwrapper.convert(location.getWorld()));
		return regionManager.getApplicableRegions(wgLocation);
	}

	private ProtectedRegion getRegionByName(IWorld world, String name)
	{
		if (!serverHasWorldGuard() || world == null || name == null)
			return null;
		return worldGuard.getRegionManager(ObjectUnwrapper.convert(world)).getRegion(name);
	}

	LocalPlayer wrapPlayer(Player rawPlayer)
	{
		return worldGuard.wrapPlayer(rawPlayer);
	}

	private WorldGuardPlugin worldGuard;
	private final IDebug debugger;
	private final IConsole console;
	private final IServer server;
}
