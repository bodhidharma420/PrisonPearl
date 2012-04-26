package com.untamedears.PrisonPearl;

import java.util.Map.Entry;
import java.util.concurrent.Callable;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public class PrisonPearlManager implements Listener {
	private PrisonPearlPlugin plugin;
	private PrisonPearlStorage pearls;
	
	public PrisonPearlManager(PrisonPearlPlugin plugin, PrisonPearlStorage pearls) {
		this.plugin = plugin;
		this.pearls = pearls;
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	public boolean imprisonPlayer(Player imprisoned, Player imprisoner) {
		World respawnworld = Bukkit.getWorld(getConfig().getString("respawn_world"));
		
		// set up the imprisoner's inventory
		Inventory inv = imprisoner.getInventory();
		ItemStack stack = null;
		int stacknum = -1;
		
		// scan for the smallest stack of normal ender pearls
		for (Entry<Integer, ? extends ItemStack> entry : inv.all(Material.ENDER_PEARL).entrySet()) {
			ItemStack newstack = entry.getValue();
			int newstacknum = entry.getKey();
			if (newstack.getDurability() == 0) {
				if (stack != null) {
					// don't keep a stack bigger than the previous one
					if (newstack.getAmount() > stack.getAmount())
						continue;
					// don't keep an identical sized stack in a higher slot
					if (newstack.getAmount() == stack.getAmount() && newstacknum > stacknum)
						continue;
				}
				
				stack = newstack;	
				stacknum = entry.getKey();
			}
		}
		
		if (stacknum == -1)
			return false; // imprisoner doesn't have normal pearl any more, so no go
		
		int pearlnum;
		if (stack.getAmount() == 1) { // if he's just got one pearl
			pearlnum = stacknum; // put the prison pearl there
		} else {
			pearlnum = inv.firstEmpty(); // otherwise, put the prison pearl in the first empty slot
			if (pearlnum > 0) {
				stack.setAmount(stack.getAmount()-1); // and reduce his stack of pearls by one
				inv.setItem(stacknum, stack);
			} else { // no empty slot?
				pearlnum = stacknum; // then overwrite his stack of pearls
			}
		}
	
		PrisonPearl pp = pearls.newPearl(imprisoned, imprisoner); // create the prison pearl		
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.NEW, imprisoner)) { // set off an event
			pearls.deletePearl(pp);
			return false;
		}

		inv.setItem(pearlnum, new ItemStack(Material.ENDER_PEARL, 1, pp.getID())); // give it to the imprisoner
		
		if (getConfig().getBoolean("resetbed_enabled"))
			imprisoned.setBedSpawnLocation(respawnworld.getSpawnLocation()); // clear out the players bed
		return true;
	}
	
	public boolean freePlayer(Player player) {
		PrisonPearl pp = pearls.getByImprisoned(player);
		if (pp == null)
			return false;
		
		return freePearl(pp);
	}
	
	public boolean freePearl(PrisonPearl pp) {
		pearls.deletePearl(pp);
		if (!prisonPearlEvent(pp, PrisonPearlEvent.Type.FREED)) { // set off an event
			pearls.addPearl(pp);
			return false;
		}
			
		return true;
	}

	// Announce the person in a pearl when a player holds it
	@EventHandler(priority=EventPriority.MONITOR)
	public void onItemHeldChange(PlayerItemHeldEvent event) {
		Inventory inv = event.getPlayer().getInventory();
		ItemStack item = inv.getItem(event.getNewSlot());
		ItemStack newitem = announcePearl(event.getPlayer(), item);
		if (newitem != null)
			inv.setItem(event.getNewSlot(), newitem);
	}
	
	private ItemStack announcePearl(Player player, ItemStack item) {
		if (item == null)
			return null;

		if (item.getType() == Material.ENDER_PEARL && item.getDurability() != 0) {
			PrisonPearl pp = pearls.getByID(item.getDurability());

			if (pp != null) {
				player.sendMessage("Prison Pearl - " + pp.getImprisonedName());
			} else {
				item.setDurability((short)0);
				return item;
			}
		}
		
		return null;
	}
	
	// Free pearls when right clicked
	@EventHandler(priority=EventPriority.LOW)
	public void onPlayerInteract(PlayerInteractEvent event) {
		PrisonPearl pp = pearls.getByItemStack(event.getItem());
		if (pp == null)
			return;

		if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			Material m = event.getClickedBlock().getType();
			if (m == Material.CHEST || m == Material.WORKBENCH || m == Material.FURNACE || m == Material.DISPENSER || m == Material.BREWING_STAND)
				return;
		} else if (event.getAction() != Action.RIGHT_CLICK_AIR) {
			return;
		}

		Player player = event.getPlayer();
		player.getInventory().setItemInHand(null);
		event.setCancelled(true);
		
		freePearl(pp);
		player.sendMessage("You've freed " + pp.getImprisonedName());
	}
	
	// Free pearls when a player leaves
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		Inventory inv = event.getPlayer().getInventory();
		for (Entry<Integer, ? extends ItemStack> entry : inv.all(Material.ENDER_PEARL).entrySet()) {
			int slot = entry.getKey();
			PrisonPearl pp = pearls.getByItemStack(entry.getValue());
			if (pp == null)
				continue;

			if (freePearl(pp))
				inv.setItem(slot, null);
		}
	}
	
	// Free the pearl if it despawns
	@EventHandler(priority=EventPriority.MONITOR)
	public void onItemDespawn(ItemDespawnEvent event) {
		PrisonPearl pp = pearls.getByItemStack(event.getEntity().getItemStack());
		if (pp == null)
			return;

		freePearl(pp);
	}

	// Free the pearl if its on a chunk that unloads
	@EventHandler(priority=EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent event) {
		for (Entity e : event.getChunk().getEntities()) {
			if (!(e instanceof Item))
				continue;
			
			final PrisonPearl pp = pearls.getByItemStack(((Item)e).getItemStack());
			if (pp == null)
				continue;	

			final Entity entity = e;
			Bukkit.getScheduler().callSyncMethod(plugin, new Callable<Void>() { // doing this in onChunkUnload causes weird things to happen
				public Void call() throws Exception {
					if (freePearl(pp))
						entity.remove();
					return null;
				}	
			});

			event.setCancelled(true);
		}
	}

	// Free the pearl if it combusts in lava/fire
	@EventHandler(priority=EventPriority.MONITOR)
	public void onEntityCombustEvent(EntityCombustEvent event) {
		if (!(event.getEntity() instanceof Item))
			return;

		PrisonPearl pp = pearls.getByItemStack(((Item)event.getEntity()).getItemStack());
		if (pp == null)
			return;

		freePearl(pp);
	}
	
	// Track the location of a pearl
	// Forbid pearls from being put in storage minecarts
	@EventHandler(priority=EventPriority.NORMAL)
	public void onInventoryClick(InventoryClickEvent event) {
		// announce an prisonpearl if it is clicked
		ItemStack newitem = announcePearl((Player)event.getWhoClicked(), event.getCurrentItem());
		if (newitem != null)
			event.setCurrentItem(newitem);
		
		PrisonPearl pp = pearls.getByItemStack(event.getCursor());
		if (pp == null)
			return;

		InventoryView view = event.getView();
		int rawslot = event.getRawSlot();
		InventoryHolder holder;
		if (view.convertSlot(rawslot) == rawslot) { // this means in the top inventory
			holder = view.getTopInventory().getHolder();
		} else {
			holder = view.getBottomInventory().getHolder();
		}
		
		if (holder instanceof StorageMinecart) {
			event.setCancelled(true);
		} else {
			updatePearl(pp, holder);
		}
	}
	
	// Track the location of a pearl if it spawns as an item for any reason
	@EventHandler(priority=EventPriority.MONITOR)
	public void onItemSpawn(ItemSpawnEvent event) {
		Item item = event.getEntity();
		PrisonPearl pp = pearls.getByItemStack(item.getItemStack());
		if (pp == null)
			return;
		
		updatePearl(pp, item);
	}
	
	// Track the location of a pearl if a player picks it up
	@EventHandler(priority=EventPriority.MONITOR)
	public void onPlayerPickupItem(PlayerPickupItemEvent event) {
		PrisonPearl pp = pearls.getByItemStack(event.getItem().getItemStack());
		if (pp == null)
			return;
		
		updatePearl(pp, event.getPlayer());
	}
	
	private void updatePearl(PrisonPearl pp, Item item) {
		pp.setItem(item);
		Bukkit.getPluginManager().callEvent(new PrisonPearlEvent(pp, PrisonPearlEvent.Type.DROPPED));
	}
	
	private void updatePearl(PrisonPearl pp, InventoryHolder holder) {
		pp.setHolder(holder);
		Bukkit.getPluginManager().callEvent(new PrisonPearlEvent(pp, PrisonPearlEvent.Type.HELD));
	}
	
	private boolean prisonPearlEvent(PrisonPearl pp, PrisonPearlEvent.Type type) {
		return prisonPearlEvent(pp, type, null);
	}
	
	private boolean prisonPearlEvent(PrisonPearl pp, PrisonPearlEvent.Type type, Player imprisoner) {
		PrisonPearlEvent event = new PrisonPearlEvent(pp, type, imprisoner);
		Bukkit.getPluginManager().callEvent(event);
		return !event.isCancelled();
	}
	
	private Configuration getConfig() {
		return plugin.getConfig();
	}
}