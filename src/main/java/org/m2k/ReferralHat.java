package org.m2k;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.model.user.UserManager;
import net.luckperms.api.node.types.PermissionNode;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class ReferralHat extends JavaPlugin implements Listener, CommandExecutor {

    private final int TARGET_TIME = getConfig().getInt("time_to_award", 7200);
    private File referralsFile;
    private FileConfiguration referralsConfig;
    private File playtimeFile;
    private FileConfiguration playtimeConfig;
    private final HashMap<UUID, UUID> referrals = new HashMap<>();
    private final HashMap<UUID, Integer> playtime = new HashMap<>();
    private final HashMap<UUID, UUID> invites = new HashMap<>(); // Inviter -> Invitee
    private final HashMap<UUID, Long> inviteTimestamps = new HashMap<>(); // Inviter -> timestamp

    @Override
    public void onEnable() {

        // Initialize Configuration and YAML files
        loadYamls();

        // Initialize playtime and referrals from the YAML files
        initPlaytime();
        initReferrals();

        // Register events and commands
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("hat")).setExecutor(this);
        Objects.requireNonNull(getCommand("invite")).setExecutor(this);
        Objects.requireNonNull(getCommand("accept")).setExecutor(this);

        // Schedule task to check playtime every 10 seconds (200 ticks)
        Bukkit.getScheduler().runTaskTimer(this, this::checkPlaytime, 200L, 200L);
    }

    private void loadYamls() {
        saveDefaultConfig();

        // Initialize referrals YAML
        referralsFile = new File(getDataFolder(), "referrals.yml");
        if (!referralsFile.exists()) {
            referralsFile.getParentFile().mkdirs();
            saveResource("referrals.yml", false);
        }
        referralsConfig = YamlConfiguration.loadConfiguration(referralsFile);

        // Initialize playtime YAML
        playtimeFile = new File(getDataFolder(), "playtimes.yml");
        if (!playtimeFile.exists()) {
            playtimeFile.getParentFile().mkdirs();
            saveResource("playtimes.yml", false);
        }
        playtimeConfig = YamlConfiguration.loadConfiguration(playtimeFile);
    }

    private void initPlaytime() {
        // Load playtimes for existing players
        for (String playerStr : playtimeConfig.getKeys(false)) {
            UUID playerUUID = UUID.fromString(playerStr);
            int time = playtimeConfig.getInt(playerStr);
            playtime.put(playerUUID, time);
        }
    }

    private void initReferrals() {
        // Load existing referrals
        for (String inviteeStr : referralsConfig.getKeys(false)) {
            UUID invitee = UUID.fromString(inviteeStr);
            UUID referrer = UUID.fromString(Objects.requireNonNull(referralsConfig.getString(inviteeStr)));
            referrals.put(invitee, referrer);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem != null && clickedItem.getType() == Material.DIAMOND_BLOCK) {
            if (event.getSlotType().toString().equalsIgnoreCase("ARMOR") && player.hasPermission("custom.hat")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "You can't manually remove the special hat. Use /hat to unequip it.");
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        ItemStack helmet = player.getInventory().getHelmet();

        if (helmet != null && helmet.getType() == Material.DIAMOND_BLOCK && player.hasPermission("custom.hat")) {
            event.getDrops().removeIf(item -> item.getType() == Material.DIAMOND_BLOCK);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (command.getName().equalsIgnoreCase("hat")) {
            handleHatCommand(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("invite")) {
            handleInviteCommand(player, args);
            return true;
        }

        if (command.getName().equalsIgnoreCase("accept")) {
            handleAcceptCommand(player, args);
            return true;
        }

        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID playerUUID = event.getPlayer().getUniqueId();
        if (!playtime.containsKey(playerUUID)) {
            playtime.put(playerUUID, 0);
        }
    }

    private void checkPlaytime() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUUID = player.getUniqueId();
            updatePlaytime(playerUUID);
            grantHatAndPermission(playerUUID);
        }
    }

    private void updatePlaytime(UUID playerUUID) {
        int newTime = playtime.get(playerUUID) + 10;
        playtime.put(playerUUID, newTime);
        savePlaytime(playerUUID, newTime); // Save to file
    }

    private void grantHatAndPermission(UUID playerUUID) {
        if (playtime.get(playerUUID) >= TARGET_TIME) {
            UUID referrer = referrals.get(playerUUID);
            if (referrer != null) {
                Player referrerPlayer = Bukkit.getPlayer(referrer);
                if (referrerPlayer != null) {
                    grantPermissionToPlayer(referrer);
                    referrerPlayer.sendMessage(ChatColor.GREEN + "You've earned a hat!");
                    referrerPlayer.getInventory().setHelmet(new ItemStack(Material.DIAMOND_BLOCK));
                    referrals.remove(playerUUID);
                }
            }
        }
    }

    private void grantPermissionToPlayer(UUID uuid) {
        LuckPerms luckPerms = LuckPermsProvider.get();
        UserManager userManager = luckPerms.getUserManager();
        User user = userManager.getUser(uuid);
        if (user != null) {
            PermissionNode node = PermissionNode.builder("custom.hat").build();
            user.data().add(node);
            userManager.saveUser(user);
            Bukkit.getLogger().info("Permission node 'custom.hat' has been granted to player with UUID: " + uuid);
        }
    }

    private void handleHatCommand(Player player) {
        ItemStack currentHelmet = player.getInventory().getHelmet();
        if (player.hasPermission("custom.hat")) {
            if (currentHelmet == null) {
                player.getInventory().setHelmet(new ItemStack(Material.DIAMOND_BLOCK));
                player.sendMessage(ChatColor.GREEN + "You've equipped your special hat!");
            } else if (currentHelmet.getType() == Material.DIAMOND_BLOCK) {
                player.getInventory().setHelmet(null);
                player.sendMessage(ChatColor.GREEN + "You've unequipped your special hat!");
            } else {
                player.sendMessage(ChatColor.RED + "Please remove your current helmet before equipping the special hat!");
            }
        } else {
            player.sendMessage(ChatColor.RED + "You haven't earned the special hat yet!");
        }
    }

    private void handleInviteCommand(Player player, String[] args) {
        if (args.length > 0) {
            Player invitee = Bukkit.getPlayer(args[0]);

            // Check if the player is trying to invite themselves
            if (player.getUniqueId().equals(invitee != null ? invitee.getUniqueId() : null)) {
                player.sendMessage(ChatColor.RED + "You can't invite yourself!");
                return;
            }

            // Check if the invitee is null or not online
            if (invitee == null || !invitee.isOnline()) {
                player.sendMessage(ChatColor.RED + "The player you are trying to invite is either not online or doesn't exist.");
                return;
            }

            if (invitee != null) {
                UUID inviter = player.getUniqueId();
                UUID inviteeUUID = invitee.getUniqueId();

                // Check if the player has already played passed the time limit.
                if (playtime.getOrDefault(inviteeUUID, 0) >= TARGET_TIME) {
                    player.sendMessage(ChatColor.RED + "This player has already exceeded the playtime threshold and cannot be invited again.");
                    return;
                }

                // Check if the invitee has already accepted another invite
                if (referrals.containsKey(inviteeUUID)) {
                    player.sendMessage(ChatColor.RED + "This player has already accepted an invite from someone else.");
                    return;
                }

                // Check if the player has already sent an invite recently
                if (inviteTimestamps.containsKey(inviter) && System.currentTimeMillis() - inviteTimestamps.get(inviter) < 60000) {
                    player.sendMessage(ChatColor.RED + "You can't send another invite so soon!");
                    return;
                }

                // Notify both players
                invites.put(inviter, inviteeUUID);
                inviteTimestamps.put(inviter, System.currentTimeMillis());
                player.sendMessage(ChatColor.GREEN + "You have invited " + args[0] + ".");
                invitee.sendMessage(ChatColor.YELLOW + player.getName() + " has invited you! Use /accept " + player.getName() + " to join.");

                // Schedule invite expiration
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (invites.containsKey(inviter) && invites.get(inviter).equals(inviteeUUID)) {
                        invites.remove(inviter);
                        inviteTimestamps.remove(inviter); // Clear the timestamp to allow a new invite
                        player.sendMessage(ChatColor.YELLOW + "Your invite to " + invitee.getName() + " has expired.");
                        invitee.sendMessage(ChatColor.YELLOW + "The invite from " + player.getName() + " has expired.");
                    }
                }, 1200L); // 60 seconds
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /invite <player>");
        }
    }

    private void handleAcceptCommand(Player player, String[] args) {
        if (args.length > 0) {
            Player referrer = Bukkit.getPlayer(args[0]);
            if (referrer != null) {
                UUID referrerUUID = referrer.getUniqueId();
                UUID inviteeUUID = player.getUniqueId();

                // Check if this player was actually invited by the referrer
                if (invites.containsKey(referrerUUID) && invites.get(referrerUUID).equals(inviteeUUID)) {
                    referrals.put(inviteeUUID, referrerUUID);
                    saveReferral(inviteeUUID, referrerUUID); // <-- save to YAML
                    player.sendMessage(ChatColor.GREEN + "You have accepted the invite from " + referrer.getName() + ".");
                    referrer.sendMessage(ChatColor.GREEN + player.getName() + " has accepted your invite.");

                    // Remove the invite now that it has been accepted
                    invites.remove(referrerUUID);
                } else {
                    player.sendMessage(ChatColor.RED + "You have not received an invite from this player!");
                }
            } else {
                player.sendMessage(ChatColor.RED + "The player you are trying to accept an invite from is not online.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Usage: /accept <player>");
        }
    }

    private void saveReferral(UUID invitee, UUID referrer) {
        referralsConfig.set(invitee.toString(), referrer.toString());
        try {
            referralsConfig.save(referralsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void savePlaytime(UUID playerUUID, int time) {
        playtimeConfig.set(playerUUID.toString(), time);
        try {
            playtimeConfig.save(playtimeFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}