package no.runsafe.worldguardbridge.event;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import no.runsafe.framework.api.ILocation;
import no.runsafe.framework.api.IServer;
import no.runsafe.framework.api.IWorld;
import no.runsafe.framework.api.event.IAsyncEvent;
import no.runsafe.framework.api.event.IServerReady;
import no.runsafe.framework.api.event.player.IPlayerMove;
import no.runsafe.framework.api.event.player.IPlayerTeleport;
import no.runsafe.framework.api.event.world.IWorldLoad;
import no.runsafe.framework.api.event.world.IWorldUnload;
import no.runsafe.framework.api.log.IConsole;
import no.runsafe.framework.api.log.IDebug;
import no.runsafe.framework.api.player.IPlayer;
import no.runsafe.framework.internal.wrapper.ObjectUnwrapper;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegionBorderPatrol implements IPlayerMove, IAsyncEvent, IServerReady, IPlayerTeleport, IWorldLoad, IWorldUnload
{
	public RegionBorderPatrol(IDebug output, IConsole console, IServer server)
	{
		this.debugger = output;
		this.console = console;
		this.server = server;
	}

	@Override
	public boolean OnPlayerTeleport(IPlayer player, ILocation from, ILocation to)
	{
		return OnPlayerMove(player, from, to);
	}

	@Override
	public boolean OnPlayerMove(IPlayer player, ILocation from, ILocation to)
	{
		if (serverHasWorldGuard())
		{
			CheckIfLeavingRegion(player, from, to);
			CheckIfEnteringRegion(player, from, to);
		}
		return true;
	}

	@Override
	public void OnServerReady()
	{
		ready = true;
		flushRegions();
	}

	@Override
	public void OnWorldLoad(IWorld world)
	{
		if (ready)
			flushRegions();
	}

	@Override
	public void OnWorldUnload(IWorld world)
	{
		if (ready)
			flushRegions();
	}

	private void flushRegions()
	{
		int regionAmount = 0;
		regions.clear();
		if (!serverHasWorldGuard())
			return;
		for (IWorld world : server.getWorlds())
		{
			if (!regions.containsKey(world.getName()))
				regions.putIfAbsent(world.getName(), new ConcurrentHashMap<String, ProtectedRegion>());

			ConcurrentHashMap<String, ProtectedRegion> worldRegions = regions.get(world.getName());
			RegionManager manager = worldGuard.getRegionManager((World) ObjectUnwrapper.convert(world));
			Map<String, ProtectedRegion> regions = manager.getRegions();

			for (String region : regions.keySet())
			{
				regionAmount += 1;
				worldRegions.putIfAbsent(region, regions.get(region));
			}
			console.logInformation("&2Loaded &a%d&2 regions in world &a%s&2.&r", worldRegions.size(), world.getName());
		}
		console.logInformation("&2Loaded &a%d&2 regions across &a%d&2 worlds.&r", regionAmount, regions.size());
	}

	private boolean serverHasWorldGuard()
	{
		if (this.worldGuard == null)
			this.worldGuard = server.getPlugin("WorldGuard");

		return this.worldGuard != null;
	}

	private void CheckIfEnteringRegion(IPlayer player, ILocation from, ILocation to)
	{
		if (!regions.containsKey(to.getWorld().getName()))
			return;
		Map<String, ProtectedRegion> worldRegions = regions.get(to.getWorld().getName());
		for (String region : worldRegions.keySet())
		{
			ProtectedRegion area = worldRegions.get(region);
			if (isInside(area, to.getWorld(), to) && !isInside(area, to.getWorld(), from))
			{
				debugger.debugFine("Player is entering the region %s, sending notification!", region);
				new RegionEnterEvent(player, to.getWorld(), region).Fire();
			}
		}
	}

	private void CheckIfLeavingRegion(IPlayer player, ILocation from, ILocation to)
	{
		if (!regions.containsKey(from.getWorld().getName()))
			return;
		Map<String, ProtectedRegion> worldRegions = regions.get(from.getWorld().getName());
		for (String region : worldRegions.keySet())
		{
			ProtectedRegion area = worldRegions.get(region);
			if (!isInside(area, from.getWorld(), to) && isInside(area, from.getWorld(), from))
			{
				debugger.debugFine("Player is leaving the region %s, sending notification!", region);
				new RegionLeaveEvent(player, from.getWorld(), region).Fire();
			}
		}
	}

	private boolean isInside(ProtectedRegion area, IWorld world, ILocation location)
	{
		return world.equals(location.getWorld())
			&& area.contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
	}

	private boolean ready = false;
	private WorldGuardPlugin worldGuard;
	private final ConcurrentHashMap<String, ConcurrentHashMap<String, ProtectedRegion>> regions =
		new ConcurrentHashMap<String, ConcurrentHashMap<String, ProtectedRegion>>();
	private final IDebug debugger;
	private final IConsole console;
	private final IServer server;
}
