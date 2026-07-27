package main;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

import objs.PlacedBlocks;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import lang.LocalizedString;
import objs.Configuration;
import org.jspecify.annotations.NonNull;

public class TreeCapitator extends JavaPlugin implements Listener {

	public File pluginFolder = new File("plugins/TreeCapitator/");

	// Colors
	public final TextColor mainColor = NamedTextColor.BLUE;
	public final TextColor textColor = NamedTextColor.WHITE;
	public final TextColor accentColor = NamedTextColor.GOLD;
	public final TextColor errorColor = NamedTextColor.DARK_RED;
	public final Component header = Component.text("[" + getName() + "] ", mainColor);

	// Files
	private Configuration config;
	private File fExtraLogs;
	private File fExtraLeaves;

	// Options
	private static final String STRG_MAX_BLOCKS = "destroy limit";
	private int maxBlocks = -1;
	private static final String DESC_MAX_BLOCKS = "Sets the maximum number of logs and leaves that can be destroyed at once. -1 to unlimit.";

	private static final String STRG_VIP_MODE = "vip mode";
	private boolean vipMode = false;
	private static final String DESC_VIP_MODE = "Sets vip mode. If enabled, a permission node (treecapitator.vip) is required to take down trees at once.";

	private static final String STRG_AXE_NEEDED = "axe needed";
	private boolean axeNeeded = true;
	private static final String DESC_AXE_NEEDED = "Sets if an axe is required to Cut down trees at once.";

	private static final String STRG_DAMAGE_AXE = "damage axe";
	private boolean damageAxe = true;
	private static final String DESC_DAMAGE_AXE = "If \"" + STRG_AXE_NEEDED
			+ "\" is set to true, sets if axes used are damaged or not. If \"" + STRG_AXE_NEEDED
			+ "\" is false, this option is ignored.";

	private static final String STRG_BREAK_AXE = "break axe";
	private boolean breakAxe = false;
	private static final String DESC_BREAK_AXE = "If \"" + STRG_AXE_NEEDED + "\" and \"" + STRG_DAMAGE_AXE
			+ "\" are set to true, sets if the axe should not be broken. Otherwise this option is ignored.";

	private static final String STRG_REPLANT = "replant";
	private boolean replant = true;
	private static final String DESC_REPLANT = "Sets if trees should be replanted automatically.";

	private static final String STRG_INVINCIBLE_REPLANT = "invincible replant";
	private boolean invincibleReplant = false;
	private static final String DESC_INVINCIBLE_REPLANT = "Sets if saplings replanted by this plugin should be unbreakable by regular players (including the block beneath).";

	private static final String STRG_ADMIT_NETHER_TREES = "cut nether \"trees\"";
	private boolean admitNetherTrees = false;
	private static final String DESC_ADMIT_NETHER_TREES = "Sets if the new 1.16 nether trees should be treated as regular trees, and therefore cut down entirely as well.";

	private static final String STRG_START_ACTIVATED = "start activated";
	private boolean startActivated = true;
	private static final String DESC_START_ACTIVATED = "Sets if this plugin starts activated for players when they enter the server. If false, players will need to use /tc toggle to activate it for themselves.";

	private static final String STRG_JOIN_MSG = "initial message";
	private boolean joinMsg = true;
	private static final String DESC_JOIN_MSG = "If true, it sends each player a message about /tc toggle when they join the server. The message changes depending on the value of \""
			+ STRG_START_ACTIVATED + "\".";

	private static final String STRG_IGNORE_LEAVES = "ignore leaves";
	private boolean ignoreLeaves = false;
	private static final String DESC_IGNORE_LEAVES = "If true, leaves will not be destroyed and will not connect logs. In vanilla terrain forests this will prevent several trees to be cut down at once, but it will leave most big oak trees floating.";

	private static final String STRG_LANGUAGE = "language";
	private String language = "english";

	private JSONParser parser = new JSONParser();
	private Material[] extraLogs = new Material[0], extraLeaves = new Material[0];

	// Per-player session state (replaces the deprecated Bukkit metadata API)
	private final Map<UUID, Boolean> pluginEnabled = new HashMap<>();
	private final Map<UUID, Long> lastProtectedMsgTime = new HashMap<>();
	// Blocks currently flagged as an invincible replant (the sapling/propagule/fungus + the block beneath it)
	private final Set<String> invincibleReplantBlocks = new HashSet<>();

	@Override
	public void onEnable() {
		getServer().getPluginManager().registerEvents(this, this);

		config = new Configuration("plugins/TreeCapitator/config.yml", "TreeCapitator");
		loadConfiguration();
		saveConfiguration();

		// Taken from vittorassi/TreeFella
		PlacedBlocks.setup();
		PlacedBlocks.get().options().copyDefaults(true);
		PlacedBlocks.save();

		loadExtraJSONs();

		getLogger().info("Enabled");
	}

	private void loadConfiguration() {
		config.reloadConfig();

		maxBlocks = config.getInt(STRG_MAX_BLOCKS, maxBlocks);
		config.setInfo(STRG_MAX_BLOCKS, DESC_MAX_BLOCKS);

		vipMode = config.getBoolean(STRG_VIP_MODE, vipMode);
		config.setInfo(STRG_VIP_MODE, DESC_VIP_MODE);

		axeNeeded = config.getBoolean(STRG_AXE_NEEDED, axeNeeded);
		config.setInfo(STRG_AXE_NEEDED, DESC_AXE_NEEDED);

		damageAxe = config.getBoolean(STRG_DAMAGE_AXE, damageAxe);
		config.setInfo(STRG_DAMAGE_AXE, DESC_DAMAGE_AXE);

		breakAxe = config.getBoolean(STRG_BREAK_AXE, breakAxe);
		config.setInfo(STRG_BREAK_AXE, DESC_BREAK_AXE);

		replant = config.getBoolean(STRG_REPLANT, replant);
		config.setInfo(STRG_REPLANT, DESC_REPLANT);

		invincibleReplant = config.getBoolean(STRG_INVINCIBLE_REPLANT, invincibleReplant);
		config.setInfo(STRG_INVINCIBLE_REPLANT, DESC_INVINCIBLE_REPLANT);

		admitNetherTrees = config.getBoolean(STRG_ADMIT_NETHER_TREES, admitNetherTrees);
		config.setInfo(STRG_ADMIT_NETHER_TREES, DESC_ADMIT_NETHER_TREES);

		startActivated = config.getBoolean(STRG_START_ACTIVATED, startActivated);
		config.setInfo(STRG_START_ACTIVATED, DESC_START_ACTIVATED);

		joinMsg = config.getBoolean(STRG_JOIN_MSG, joinMsg);
		config.setInfo(STRG_JOIN_MSG, DESC_JOIN_MSG);

		ignoreLeaves = config.getBoolean(STRG_IGNORE_LEAVES, ignoreLeaves);
		config.setInfo(STRG_IGNORE_LEAVES, DESC_IGNORE_LEAVES);

		language = config.getString(STRG_LANGUAGE, language);
	}

	private void loadExtraJSONs() {
		fExtraLogs = new File(pluginFolder, "extra_logs.json");
		if (fExtraLogs.exists()) {
			try (FileReader reader = new FileReader(fExtraLogs)) {
				JSONObject jsonObject = (JSONObject) parser.parse(reader);
				JSONArray JArrayLogs = (JSONArray) jsonObject.get("logs");
				Object[] strExtraLogs = JArrayLogs.toArray();
				extraLogs = new Material[strExtraLogs.length];
				for (int i = 0; i < strExtraLogs.length; i++) {
					extraLogs[i] = Material.getMaterial(strExtraLogs[i].toString());
					if (extraLogs[i] == null) {
						getLogger().warning("Material \"" + strExtraLogs[i]
								+ "\" in extra_logs.json could not be recognized as any in-game Material.");
					}
				}
				extraLogs = Arrays.stream(extraLogs).filter(m -> m != null).toArray(Material[]::new);
				getLogger().log(Level.INFO, "Extra logs from JSON: " + Arrays.toString(extraLogs));
			} catch (IOException e) {
				getLogger().warning(
						"extra_logs.json could not be read. Only the default logs (+ nether) will be detected.");
				extraLogs = new Material[0];
			} catch (ParseException e) {
				getLogger().warning(
						"extra_logs.json is an invalid JSON. Please make sure the contents of the file are a valid JSON format. Only the default logs (+ nether) will be detected.");
				extraLogs = new Material[0];
			}
		} else {
			try {
				fExtraLogs.createNewFile();
				JSONObject jsonData = new JSONObject();
				JSONArray jsonArrayLogs = new JSONArray();
				jsonArrayLogs.add("OAK_LOG");
				jsonArrayLogs.add("OAK_LOG");
				jsonArrayLogs.add("OAK_LOG");
				jsonArrayLogs.add("OAK_LOG");
				jsonData.put("logs", jsonArrayLogs);
				FileWriter fw = new FileWriter(fExtraLogs);
				fw.write(jsonData.toJSONString());
				fw.close();
			} catch (IOException e) {
				getLogger().warning("extra_logs.json could not be created. The default log will be used.");
				extraLogs = new Material[0];
			}
		}

		fExtraLeaves = new File(pluginFolder, "extra_leaves.json");
		if (fExtraLeaves.exists()) {
			try (FileReader reader = new FileReader(fExtraLeaves)) {
				JSONObject jsonObject = (JSONObject) parser.parse(reader);
				JSONArray JArrayLeaves = (JSONArray) jsonObject.get("leaves");
				Object[] strExtraLeaves = JArrayLeaves.toArray();
				extraLeaves = new Material[strExtraLeaves.length];
				for (int i = 0; i < strExtraLeaves.length; i++) {
					extraLeaves[i] = Material.getMaterial(strExtraLeaves[i].toString());
					if (extraLeaves[i] == null) {
						getLogger().warning("Material \"" + strExtraLeaves[i]
								+ "\" in extra_leaves.json could not be recognized as any in-game Material.");
					}
				}
				extraLeaves = Arrays.stream(extraLeaves).filter(m -> m != null).toArray(Material[]::new);
				getLogger().log(Level.INFO, "Extra leaves from JSON: " + Arrays.toString(extraLeaves));
			} catch (IOException e) {
				getLogger().warning(
						"extra_leaves.json could not be read. Only the default leaves (+ nether) will be detected.");
				extraLeaves = new Material[0];
			} catch (ParseException e) {
				getLogger().warning(
						"extra_leaves.json is an invalid JSON. Please make sure the contents of the file are a valid JSON format. Only the default leaves (+ nether) will be detected.");
				extraLeaves = new Material[0];
			}
		} else {
			try {
				fExtraLeaves.createNewFile();
				JSONObject jsonData = new JSONObject();
				JSONArray jsonArrayLeaves = new JSONArray();
				jsonArrayLeaves.add("OAK_LEAVES");
				jsonArrayLeaves.add("OAK_LEAVES");
				jsonArrayLeaves.add("OAK_LEAVES");
				jsonArrayLeaves.add("OAK_LEAVES");
				jsonData.put("leaves", jsonArrayLeaves);
				FileWriter fw = new FileWriter(fExtraLeaves);
				fw.write(jsonData.toJSONString());
				fw.close();
			} catch (IOException e) {
				getLogger().warning("extra_leaves.json could not be created. The default leaves will be used.");
				extraLeaves = new Material[0];
			}
		}
	}

	private void saveConfiguration() {
		try {
			config.setValue(STRG_MAX_BLOCKS, maxBlocks);
			config.setValue(STRG_VIP_MODE, vipMode);
			config.setValue(STRG_AXE_NEEDED, axeNeeded);
			config.setValue(STRG_DAMAGE_AXE, damageAxe);
			config.setValue(STRG_BREAK_AXE, breakAxe);
			config.setValue(STRG_REPLANT, replant);
			config.setValue(STRG_INVINCIBLE_REPLANT, invincibleReplant);
			config.setValue(STRG_ADMIT_NETHER_TREES, admitNetherTrees);
			config.setValue(STRG_START_ACTIVATED, startActivated);
			config.setValue(STRG_JOIN_MSG, joinMsg);
			config.setValue(STRG_IGNORE_LEAVES, ignoreLeaves);
			config.setValue(STRG_LANGUAGE, language);
			config.saveConfig();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDisable() {
		saveConfig();
		getLogger().info("Disabled");
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if (joinMsg) {
			Player p = e.getPlayer();
			boolean enabled = pluginEnabled.getOrDefault(p.getUniqueId(), startActivated);
			if (enabled) {
				send(p, LocalizedString.JOIN_MSG_ENABLED.get(language));
			} else {
				send(p, LocalizedString.JOIN_MSG_DISABLED.get(language));
			}
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		UUID id = e.getPlayer().getUniqueId();
		pluginEnabled.remove(id);
		lastProtectedMsgTime.remove(id);
	}

	private String blockKey(Block block) {
		return block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
	}

	private void send(CommandSender sender, Component message) {
		sender.sendMessage(header.append(message.colorIfAbsent(textColor)));
	}

	// Taken from vittorasi/TreeFella
	@EventHandler
	public void onBlockPlaceEvent(org.bukkit.event.block.BlockPlaceEvent e) {
		if (e.isCancelled()) {
			return;
		}
		Block block = e.getBlock();

		if (isLog(block.getType())) {
			PlacedBlocks.get().set(blockKey(block), 1);
			PlacedBlocks.save();
		}
	}

	@EventHandler
	private void onBlockBreak(BlockBreakEvent event) {
		final Block firstBrokenB = event.getBlock();
		final Material material = firstBrokenB.getBlockData().getMaterial();
		final Player player = event.getPlayer();
		ItemStack tool = player.getInventory().getItemInMainHand();

		String path = blockKey(firstBrokenB);

		boolean isPlayerPlacedBlock = PlacedBlocks.get().isInt(path);

		Block below = firstBrokenB.getWorld().getBlockAt(firstBrokenB.getX(), firstBrokenB.getY() - 1,
				firstBrokenB.getZ());
		Block above = firstBrokenB.getWorld().getBlockAt(firstBrokenB.getX(), firstBrokenB.getY() + 1,
				firstBrokenB.getZ());

		if (invincibleReplant && !(canPlant(below, material) || canPlant(firstBrokenB, above.getType()))) {
			if (invincibleReplantBlocks.contains(path)) {
				long currentTime = System.currentTimeMillis();
				if (!isSappling(material)) {
					invincibleReplantBlocks.remove(path);
					invincibleReplantBlocks.remove(blockKey(below));
					invincibleReplantBlocks.remove(blockKey(above));
				} else if (player.hasPermission("treecapitator.admin")) {
					send(player, LocalizedString.BROKE_PROTECTED_REPLANT.get(language));
					invincibleReplantBlocks.remove(path);
					invincibleReplantBlocks.remove(blockKey(below));
					invincibleReplantBlocks.remove(blockKey(above));
				} else {
					UUID id = player.getUniqueId();
					Long lastMsg = lastProtectedMsgTime.get(id);
					if (lastMsg == null || currentTime - 5000 > lastMsg) {
						send(player, LocalizedString.ATTEMPT_BREAK_PROTECTED_REPLANT.get(language));
						lastProtectedMsgTime.put(id, currentTime);
					}
					event.setCancelled(true);
				}
				return;
			}
		}
		invincibleReplantBlocks.remove(path);
		invincibleReplantBlocks.remove(blockKey(below));
		invincibleReplantBlocks.remove(blockKey(above));

		if (isPlayerPlacedBlock) {
			PlacedBlocks.get().set(path, null);
			PlacedBlocks.save();
			return;
		}

		if (!player.isSneaking() || (!player.getGameMode().equals(GameMode.SURVIVAL))) {
			return;
		}

		boolean enabled = pluginEnabled.getOrDefault(player.getUniqueId(), startActivated);

		if (enabled && !event.isCancelled() && isLog(material) && player.hasPermission("treecapitator.user")
				&& (vipMode && player.hasPermission("treecapitator.vip") || !vipMode)) {
			try {
				// Yes it could use some tuning
				if (!tool.getType().name().contains("_AXE")) {
					tool = null;
				}

				boolean cutDown = true;
				if (axeNeeded && (tool == null || !tool.getType().name().endsWith("_AXE"))) {
					cutDown = false;
				}
				if (cutDown && axeNeeded && !breakAxe && (tool.hasItemMeta() && tool.getItemMeta() instanceof Damageable
						&& ((Damageable) tool.getItemMeta()).getDamage() >= tool.getType().getMaxDurability())) {
					cutDown = false;
				}
				if (cutDown) {
					if (replant) {
						breakRecReplant(player, tool, firstBrokenB, material, 0, false);
					} else {
						breakRecNoReplant(player, tool, firstBrokenB, material, 0, false);
					}
					event.setCancelled(true);
				}
			} catch (StackOverflowError e) {
			}
		}

	}

	private int breakRecNoReplant(Player player, ItemStack tool, Block lego, Material type, int destroyed,
			boolean stop) {
		if (stop)
			return destroyed;
		Material material = lego.getBlockData().getMaterial();
		if ((isLog(material) || isLeaves(material)) && !PlacedBlocks.get().isInt(blockKey(lego))) {
			if (destroyed > maxBlocks && maxBlocks > 0) {
				return destroyed;
			}
			World mundo = lego.getWorld();
			if (damageItem(player, tool, material)) {
				stop = true;
			} else {
				if (lego.breakNaturally()) {
					destroyed++;
				} else {
					return destroyed;
				}
			}

			int x = lego.getX(), y = lego.getY(), z = lego.getZ();

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x, y - 1, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x, y + 1, z), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x + 1, y, z + 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x + 1, y, z - 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x - 1, y, z + 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x - 1, y, z - 1), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x + 1, y, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x, y, z + 1), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x - 1, y, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecNoReplant(player, tool, mundo.getBlockAt(x, y, z - 1), type, destroyed, stop);
		}

		return destroyed;
	}

	private int breakRecReplant(Player player, ItemStack tool, Block lego, Material type, int destroyed, boolean stop) {
		if (stop || (maxBlocks > 0 && destroyed > maxBlocks))
			return destroyed;
		Material material = lego.getBlockData().getMaterial();
		if ((isLog(material) || isLeaves(material)) && !PlacedBlocks.get().isInt(blockKey(lego))) {
			World mundo = lego.getWorld();
			int x = lego.getX(), y = lego.getY(), z = lego.getZ();
			Block below = mundo.getBlockAt(x, y - 1, z);

			if (canPlant(below, lego.getType())) {
				Material saplingType = null;
				String logType = lego.getType().toString();
				String[] logTypeTokens = logType.split("_");
				String plantSuffix = "_SAPLING";
				if (logType.startsWith("MANGROVE")) {
					plantSuffix = "_PROPAGULE";
				} else if (logType.endsWith("_STEM")) {
					plantSuffix = "_FUNGUS";
				}

				saplingType = Material.matchMaterial(Arrays.stream(logTypeTokens).limit(logTypeTokens.length - 1).collect(Collectors.joining("_")) + plantSuffix);

				if (damageItem(player, tool, material)) {
					return destroyed;
				} else {
					if (lego.breakNaturally()) {
						if (saplingType != null) {
							lego.setType(saplingType);
							invincibleReplantBlocks.add(blockKey(lego));
							invincibleReplantBlocks.add(blockKey(below));
						}
						destroyed++;
					} else {
						return destroyed;
					}
				}

			} else {
				if (damageItem(player, tool, material)) {
					return destroyed;
				} else {
					if (lego.breakNaturally()) {
						destroyed++;
					} else {
						return destroyed;
					}
				}
			}

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x, y - 1, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x, y + 1, z), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x + 1, y, z + 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x + 1, y, z - 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x - 1, y, z + 1), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x - 1, y, z - 1), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x + 1, y, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x, y, z + 1), type, destroyed, stop);

			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x - 1, y, z), type, destroyed, stop);
			if (destroyed < maxBlocks || maxBlocks < 0)
				destroyed = breakRecReplant(player, tool, mundo.getBlockAt(x, y, z - 1), type, destroyed, stop);
		}

		return destroyed;
	}

	private void sendUsage(CommandSender sender, String label, String subcommand, String hint) {
		send(sender, Component.text("Use: ")
				.append(Component.text("/" + label + " " + subcommand + " " + hint, accentColor))
				.append(Component.text(".")));
	}

	private void sendInvalidArg(CommandSender sender, String label, String subcommand, String hint, String badArg) {
		send(sender, Component.text("Use: ")
				.append(Component.text("/" + label + " " + subcommand + " " + hint, accentColor))
				.append(Component.text(". ("))
				.append(Component.text(badArg, accentColor))
				.append(Component.text(" is not a valid argument)")));
	}

	private void sendConfigSaveError(CommandSender sender, IOException e) {
		send(sender, Component.text("Error trying to save the value in the configuration file.", errorColor));
		e.printStackTrace();
	}

	private Component helpLine(String label, String subcommand, String hint, Component description) {
		return Component.text("/" + label + " " + subcommand + (hint.isEmpty() ? "" : " " + hint) + ": ", accentColor)
				.append(description.colorIfAbsent(textColor));
	}

	private Component settingLine(String name, Component value) {
		return Component.text(name + ": ", accentColor).append(value.colorIfAbsent(textColor));
	}

	@Override
	public boolean onCommand(@NonNull CommandSender sender, Command command, @NonNull String label, String[] args) {
		label = label.toLowerCase();
		boolean bueno = label.equals(command.getLabel());
		String[] cmds = command.getAliases().toArray(new String[] {});
		for (int i = 0; i < cmds.length && !bueno; i++) {
			cmds[i] = cmds[i].toLowerCase();
			if (label.equals(cmds[i])) {
				bueno = true;
			}
		}

		boolean noPermission = false;
		if (bueno) {
			if (args.length > 0) {
				switch (args[0].toLowerCase()) {

				// TODO: Add the missing localized strings. Also, decide what to do with the command labels, should they be translated? It would mean a lot of extra effort
				case "help":
					send(sender, LocalizedString.HELP_CMD_COMMANDS.get(language));
					sender.sendMessage(helpLine(label, "help", "", LocalizedString.HELP_CMD_HELP.get(language)));
					sender.sendMessage(helpLine(label, "version", "", LocalizedString.HELP_CMD_VERSION.get(language)));
					sender.sendMessage(helpLine(label, "reload", "", LocalizedString.HELP_CMD_RELOAD.get(language)));
					sender.sendMessage(
							helpLine(label, "toggle", "<true/false>", LocalizedString.HELP_CMD_TOGGLE.get(language)));
					sender.sendMessage(helpLine(label, "settings", "", LocalizedString.HELP_CMD_SETTINGS.get(language)));
					sender.sendMessage(
							helpLine(label, "setLimit", "<number>", LocalizedString.HELP_CMD_SET_LIMIT.get(language)));
					sender.sendMessage(helpLine(label, "setVipMode", "<true/false>",
							LocalizedString.HELP_CMD_SET_VIP_MODE.get(language)));
					sender.sendMessage(
							helpLine(label, "setReplant", "<true/false>", LocalizedString.HELP_CMD_SET_REPLANT.get(language)));
					sender.sendMessage(helpLine(label, "setInvincibleReplanting", "<true/false>",
							LocalizedString.HELP_CMD_SET_INV_REPL.get(language)));
					sender.sendMessage(helpLine(label, "setAxeNeeded", "<true/false>",
							LocalizedString.HELP_CMD_SET_AXE_NEEDED.get(language)));
					sender.sendMessage(helpLine(label, "setDamageAxe", "<true/false>",
							LocalizedString.HELP_CMD_SET_DMG_AXE.get(language)));
					sender.sendMessage(helpLine(label, "setBreakAxes", "<true/false>",
							LocalizedString.HELP_CMD_SET_BREAK_AXE.get(language)));
					sender.sendMessage(helpLine(label, "setNetherTrees", "<true/false>",
							LocalizedString.HELP_CMD_SET_NETHER_TREES.get(language)));
					sender.sendMessage(helpLine(label, "setStartActivated", "<true/false>",
							LocalizedString.HELP_CMD_SET_START_ENABLED.get(language)));
					sender.sendMessage(helpLine(label, "setJoinMsg", "<true/false>",
							LocalizedString.HELP_CMD_SET_SEND_JOIN_MSG.get(language)));
					sender.sendMessage(helpLine(label, "setIgnoreLeaves", "<true/false>",
							LocalizedString.HELP_CMD_SET_IGNORE_LEAVES.get(language)));
					break;

				case "version":
					send(sender,
							LocalizedString.VERSION_CMD.get(language, Placeholder.unparsed("name", getName()),
									Placeholder.unparsed("version", getPluginMeta().getVersion())));
					break;

				case "config":
				case "values":
				case "settings":
					send(sender, LocalizedString.SETTINGS_CMD.get(language));
					sender.sendMessage(settingLine("Join Message",
							joinMsg ? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Starts Activated", startActivated
							? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Limit",
							maxBlocks < 0 ? LocalizedString.SETTINGS_UNLIMITED.get(language) : Component.text(maxBlocks)));
					sender.sendMessage(settingLine("Vip Mode", vipMode ? LocalizedString.SETTINGS_ENABLED.get(language)
							: LocalizedString.SETTINGS_DISABLED.get(language)));
					sender.sendMessage(settingLine("Replant", replant ? LocalizedString.SETTINGS_ENABLED.get(language)
							: LocalizedString.SETTINGS_DISABLED.get(language)));
					sender.sendMessage(settingLine("Invincible replant", invincibleReplant
							? LocalizedString.SETTINGS_ENABLED.get(language) : LocalizedString.SETTINGS_DISABLED.get(language)));
					sender.sendMessage(settingLine("Axe Needed",
							axeNeeded ? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Damage Axe",
							damageAxe ? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Break Axe",
							breakAxe ? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Ignore Leaves", ignoreLeaves
							? LocalizedString.SETTINGS_YES.get(language) : LocalizedString.SETTINGS_NO.get(language)));
					sender.sendMessage(settingLine("Language", Component.text(language.toLowerCase())));
					break;

				case "toggle":
					if (sender instanceof Player playerSender) {
						boolean enabled = !pluginEnabled.getOrDefault(playerSender.getUniqueId(), startActivated);
						pluginEnabled.put(playerSender.getUniqueId(), enabled);
						send(sender, Component.text("You " + (enabled ? "enabled" : "disabled") + " quick log destroy."));
					} else {
						send(sender, Component.text("This command can only be used by players"));
					}
					break;

				case "limit":
				case "setlimit":
				case "blocklimit":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							send(sender, Component.text("Blocks destroyed at once limit is currently ")
									.append(Component.text(maxBlocks, accentColor)).append(Component.text(".")));
						} else {
							try {
								int nuevoMax = Integer.parseInt(args[1]);
								maxBlocks = nuevoMax < 0 ? -1 : nuevoMax;
								config.setValue(STRG_MAX_BLOCKS, maxBlocks);
								try {
									config.saveConfig();
									send(sender, Component.text("Limit set to " + (nuevoMax < 0 ? "unbounded" : nuevoMax) + "."));
								} catch (IOException e) {
									sendConfigSaveError(sender, e);
								}
							} catch (NumberFormatException e) {
								sendInvalidArg(sender, label, args[0], "<number>", args[1]);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setvipmode":
				case "vipmode":
				case "vipneeded":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								vipMode = true;
								break;
							case "false":
							case "no":
								vipMode = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_VIP_MODE, vipMode);
							try {
								config.saveConfig();
								send(sender, Component.text("Vip mode ")
										.append(Component.text(vipMode ? "enabled" : "disabled", accentColor))
										.append(Component.text(".")));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setreplant":
				case "replant":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								replant = true;
								break;
							case "false":
							case "no":
								replant = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_REPLANT, replant);
							try {
								config.saveConfig();
								send(sender, Component.text("Replanting ")
										.append(Component.text(replant ? "enabled" : "disabled", accentColor))
										.append(Component.text(".")));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setinvinciblereplant":
				case "invinciblereplant":
				case "invinciblereplants":
				case "invinciblereplanting":
				case "invinciblereplantings":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								invincibleReplant = true;
								break;
							case "false":
							case "no":
								invincibleReplant = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_INVINCIBLE_REPLANT, invincibleReplant);
							try {
								config.saveConfig();
								send(sender, Component.text("Invincible replanted saplings ")
										.append(Component.text(invincibleReplant ? "enabled" : "disabled", accentColor))
										.append(Component.text(".")));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "axeneeded":
				case "setaxeneeded":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								axeNeeded = true;
								break;
							case "false":
							case "no":
								axeNeeded = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_AXE_NEEDED, axeNeeded);
							try {
								config.saveConfig();
								send(sender, Component.text("Axe ")
										.append(Component.text(axeNeeded ? "needed" : "not needed", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setdamage":
				case "setdamageaxe":
				case "setaxedamage":
				case "damageaxe":
				case "axedamage":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								damageAxe = true;
								break;
							case "false":
							case "no":
								damageAxe = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_DAMAGE_AXE, damageAxe);
							try {
								config.saveConfig();
								send(sender, Component.text("Axes ")
										.append(Component.text(damageAxe ? "can be damaged" : "can't be damaged", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setbreak":
				case "setbreakaxe":
				case "setaxebreak":
				case "breakaxe":
				case "axebreak":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								breakAxe = true;
								break;
							case "false":
							case "no":
								breakAxe = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_BREAK_AXE, breakAxe);
							try {
								config.saveConfig();
								send(sender, Component.text("Axes ")
										.append(Component.text(breakAxe ? "can be broken" : "can't be broken", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setcutnethertrees":
				case "setcutdownnethertrees":
				case "setnethertrees":
				case "nethertrees":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								admitNetherTrees = true;
								break;
							case "false":
							case "no":
								admitNetherTrees = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_ADMIT_NETHER_TREES, admitNetherTrees);
							try {
								config.saveConfig();
								send(sender, Component.text("Cut down nether trees ")
										.append(Component.text(admitNetherTrees ? "true" : "false", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setstartactivated":
				case "startactivated":
				case "preactivated":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								startActivated = true;
								break;
							case "false":
							case "no":
								startActivated = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_START_ACTIVATED, startActivated);
							try {
								config.saveConfig();
								send(sender, Component.text("Plugin activated by default ")
										.append(Component.text(startActivated ? "true" : "false", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setjoinmsg":
				case "setjoinmessage":
				case "joinmsg":
				case "joinmessage":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								joinMsg = true;
								break;
							case "false":
							case "no":
								joinMsg = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_JOIN_MSG, joinMsg);
							try {
								config.saveConfig();
								send(sender, Component.text("Message reminding /tc toggle on join set to ")
										.append(Component.text(joinMsg ? "true" : "false", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "setignoreleaves":
				case "ignoreleaves":
					if (sender.hasPermission("treecapitator.admin")) {
						if (args.length != 2) {
							sendUsage(sender, label, args[0], "<true/false/yes/no>");
						} else {
							switch (args[1]) {
							case "true":
							case "yes":
								ignoreLeaves = true;
								break;
							case "false":
							case "no":
								ignoreLeaves = false;
								break;

							default:
								sendInvalidArg(sender, label, args[0], "<true/false/yes/no>", args[1]);
								break;
							}
							config.setValue(STRG_IGNORE_LEAVES, ignoreLeaves);
							try {
								config.saveConfig();
								send(sender, Component.text("Leaves will be ")
										.append(Component.text(ignoreLeaves ? "left untouched" : "removed", accentColor)));
							} catch (IOException e) {
								sendConfigSaveError(sender, e);
							}
						}
					} else {
						noPermission = true;
					}

					break;

				case "reload":
					if (sender.hasPermission("treecapitator.admin")) {
						loadConfiguration();
						loadExtraJSONs();
						send(sender, Component.text("Configuration loaded from file."));
					} else {
						noPermission = true;
					}

					break;

				default:
					send(sender, Component.text("Command not found, please check \"/" + label + " help\".", errorColor));
					break;
				}
			} else {
				return false;
			}
		}

		if (noPermission) {
			send(sender, Component.text("You don't have permission to use this command.", errorColor));
		}
		return bueno;
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> list = new ArrayList<>();
		switch (args.length) {
		case 0:
			list.add("help");
			if (sender.hasPermission("treecapitator.admin")) {
				list.add("reload");
			}
			list.add("toggle");
			if (sender.hasPermission("treecapitator.admin")) {
				list.add("settings");
				list.add("setLimit");
				list.add("setVipMode");
				list.add("setReplant");
				list.add("setInvincibleReplant");
				list.add("setAxeNeeded");
				list.add("setDamageAxe");
				list.add("setBreakAxes");
				list.add("setNetherTrees");
				list.add("setStartActivated");
				list.add("setJoinMsg");
				list.add("setIgnoreLeaves");
			}
			break;
		case 1:
			args[0] = args[0].toLowerCase();
			switch (args[0]) {
			case "help":
			case "reload":
			case "toggle":
				break;

			default:
				if ("help".contains(args[0]))
					list.add("help");
				if (sender.hasPermission("treecapitator.admin")) {
					if ("reload".contains(args[0]))
						list.add("reload");
				}
				if ("toggle".contains(args[0]))
					list.add("toggle");
				if (sender.hasPermission("treecapitator.admin")) {
					if ("settings".contains(args[0]))
						list.add("settings");
					if ("setlimit".contains(args[0]))
						list.add("setLimit");
					if ("setvipmode".contains(args[0]))
						list.add("setVipMode");
					if ("setreplant".contains(args[0]))
						list.add("setReplant");
					if ("setinvinciblereplant".contains(args[0]))
						list.add("setInvincibleReplant");
					if ("setaxeneeded".contains(args[0]))
						list.add("setAxeNeeded");
					if ("setdamageaxe".contains(args[0]))
						list.add("setDamageAxe");
					if ("setbreakaxe".contains(args[0]))
						list.add("setBreakAxe");
					if ("setnethertrees".contains(args[0]))
						list.add("setNetherTrees");
					if ("setstartactivated".contains(args[0]))
						list.add("setStartActivated");
					if ("setjoinmsg".contains(args[0]))
						list.add("setJoinMsg");
					if ("setignoreleaves".contains(args[0]))
						list.add("setIgnoreLeaves");
				}
				break;
			}
			break;

		case 2:
			args[0] = args[0].toLowerCase();
			switch (args[0]) {
			case "setlimit":
				break;
			case "setvipmode":
			case "setreplant":
			case "setinvinciblereplant":
			case "setaxeneeded":
			case "setdamageaxe":
			case "setbreakaxe":
			case "setnethertrees":
			case "setstartactivated":
			case "setjoinmsg":
			case "setignoreleaves":
				list.add("true");
				list.add("false");
				break;

			default:
				break;
			}
			break;
		default:
			break;
		}
		return list;
	}

	/**
	 * Deals 1 damage to an item, if possible
	 *
	 * @param  player
	 * @param  tool
	 * @return        true if item is destroyed or should not be damaged anymore, false if
	 *                not damageable or damaged but not destroyed
	 */
	private boolean damageItem(Player player, ItemStack tool, Material material) {
		if (axeNeeded && damageAxe && tool != null && isLog(material)) {
			ItemMeta meta = tool.getItemMeta();
			if (meta instanceof Damageable damageable) {
				short maxDmg = tool.getType().getMaxDurability();
                int dmg = damageable.getDamage();

				// damageable.setDamage(++dmg);
				// Substituted for the following code by exwundee (https://github.com/exwundee)
				// This adds support for any level of the Durability enchantment
				{
					Random rand = new Random();

					int unbLevel = tool.getEnchantmentLevel(Enchantment.UNBREAKING);

					if (rand.nextInt(unbLevel + 1) == 0) {
						damageable.setDamage(++dmg);
					}
				}
				tool.setItemMeta((ItemMeta) damageable);

				if (dmg >= maxDmg) {
					if (breakAxe) {
						tool.setAmount(0);
						player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
					} else {
						damageable.setDamage(maxDmg - 1);
						tool.setItemMeta((ItemMeta) damageable);
					}
					return true;
				}
			}
		}
		return false;
	}

	private boolean isLog(Material mat) {
		for (int i = 0; i < extraLogs.length; i++) {
			if (extraLogs[i].equals(mat)) {
				return true;
			}
		}
		boolean ret = !mat.name().contains("STRIPPED_") && mat.name().contains("_LOG");
		if (!ret && admitNetherTrees)
			return mat.name().equals("CRIMSON_STEM") || mat.name().equals("WARPED_STEM");
		return ret;
	}

	private boolean isLeaves(Material mat) {
		if (ignoreLeaves)
			return false;
		for (int i = 0; i < extraLeaves.length; i++) {
			if (extraLeaves[i].equals(mat)) {
				return true;
			}
		}
		boolean ret = mat.name().contains("LEAVES");
		if (!ret && admitNetherTrees)
			return ret || mat.name().equals("NETHER_WART_BLOCK") || mat.name().equals("WARPED_WART_BLOCK")
					|| mat.name().equals("SHROOMLIGHT");
		return ret;
	}

	private boolean isSappling(Material mat) {
		String name = mat.name();
		return name.contains("_SAPLING") || name.contains("_PROPAGULE") || name.contains("_FUNGUS");
	}

	/**
	 * <Block below, Log material>
	 */
	private HashMap<Material, List<Material>> treeMap;

	private boolean canPlant(Block below, Material woodType) {
		if (treeMap == null) {
			treeMap = new HashMap<>(10);

			// Elegance is my passion /s
			ArrayList<Material> woods = new ArrayList<>(9);
			try {
				woods.add(Material.OAK_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.DARK_OAK_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.SPRUCE_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.ACACIA_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.AZALEA);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.BIRCH_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.JUNGLE_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.MANGROVE_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			try {
				woods.add(Material.CHERRY_LOG);
			} catch (Exception | NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
			for (Material wood : woods) {
				List<Material> plantSurfaces = new ArrayList<>(Arrays.asList(Material.DIRT, Material.GRASS_BLOCK,
						Material.MYCELIUM, Material.FARMLAND));
				try {
					plantSurfaces.add(Material.PODZOL);
				} catch (NoSuchFieldError e) {
					// Material doesn't exist in this version
				}
				try {
					plantSurfaces.add(Material.MOSS_BLOCK);
				} catch (NoSuchFieldError e) {
					// Material doesn't exist in this version
				}
				try {
					plantSurfaces.add(Material.ROOTED_DIRT);
				} catch (NoSuchFieldError e) {
					// Material doesn't exist in this version
				}
				try {
					plantSurfaces.add(Material.COARSE_DIRT);
				} catch (NoSuchFieldError e) {
					// Material doesn't exist in this version
				}
				try {
					plantSurfaces.add(Material.MUD);
				} catch (NoSuchFieldError e) {
					// Material doesn't exist in this version
				}
				treeMap.put(wood, plantSurfaces);
			}

			try {
				treeMap.get(Material.MANGROVE_LOG).add(Material.CLAY);
			} catch (NoSuchFieldError e) {
				// Material doesn't exist in this version
			}
		}

		try {
			if (admitNetherTrees && !treeMap.containsKey(Material.WARPED_STEM)) {
				treeMap.put(Material.WARPED_STEM, Arrays.asList(Material.WARPED_NYLIUM));
				treeMap.put(Material.CRIMSON_STEM, Arrays.asList(Material.CRIMSON_NYLIUM));
			} else if (!admitNetherTrees && treeMap.containsKey(Material.WARPED_STEM)) {
				treeMap.remove(Material.WARPED_STEM);
				treeMap.remove(Material.CRIMSON_STEM);
			}
		} catch (NoSuchFieldError e) {
			// Material doesn't exist in this version
		}

		// Unknown/custom log types (e.g. from extra_logs.json) fall back to the standard surfaces.
		List<Material> surfaces = treeMap.getOrDefault(woodType, treeMap.get(Material.OAK_LOG));
		return surfaces != null && surfaces.contains(below.getType());
	}
}
