package ru.optisolo.listener;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.projectiles.ProjectileSource;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;
import ru.optisolo.config.Settings;
import ru.optisolo.data.SoloWorldData;
import ru.optisolo.world.SoloWorldManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Защита соло-миров от гостей. Работает ТОЛЬКО внутри соло-миров,
 * обычную игру не затрагивает никак.
 */
public class ProtectionListener implements Listener {

    private final SoloWorldManager worlds;
    private final Settings settings;
    private final Messages msg;
    private final Map<UUID, Long> denyCooldown = new HashMap<>();

    public ProtectionListener(OptiSoloPlugin plugin) {
        this.worlds = plugin.getWorldManager();
        this.settings = plugin.getSettings();
        this.msg = plugin.getMessages();
    }

    private SoloWorldData dataOf(World world) {
        SoloWorldData data = worlds.getData(world);
        return data;
    }

    private boolean canBuild(Player player, World world) {
        SoloWorldData data = dataOf(world);
        if (data == null) return true;
        return worlds.hasBuildAccess(player, data);
    }

    private boolean isVisitor(Player player, World world) {
        SoloWorldData data = dataOf(world);
        if (data == null) return false;
        return !worlds.hasBuildAccess(player, data);
    }

    private void deny(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = settings.getDenyCooldownSeconds() * 1000L;
        Long last = denyCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldown) return;
        denyCooldown.put(player.getUniqueId(), now);
        msg.send(player, "visitor-denied");
    }

    // ---------- блоки ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!canBuild(e.getPlayer(), e.getBlock().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---------- взаимодействие ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (!e.hasBlock() || e.getClickedBlock() == null) return;
        Player player = e.getPlayer();
        World world = e.getClickedBlock().getWorld();
        if (!isVisitor(player, world)) return;
        Material mat = e.getClickedBlock().getType();
        if (isAllowedInteract(mat)) return;
        e.setCancelled(true);
        deny(player);
    }

    private boolean isAllowedInteract(Material mat) {
        String name = mat.name();
        for (String part : settings.getAllowedInteractContains()) {
            if (name.contains(part)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (isVisitor(e.getPlayer(), e.getRightClicked().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (isVisitor(e.getPlayer(), e.getRightClicked().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent e) {
        if (isVisitor(e.getPlayer(), e.getLectern().getWorld())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---------- урон ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Player attacker = resolveAttacker(e.getDamager());
        if (attacker == null) return;
        World world = e.getEntity().getWorld();
        SoloWorldData data = dataOf(world);
        if (data == null) return;

        if (settings.isAdminBypass() && worlds.isAdmin(attacker)) return;

        Entity victim = e.getEntity();
        if (victim instanceof Player) {
            // PvP в мире выключен — никто никого не бьёт.
            if (!data.isPvp()) {
                e.setCancelled(true);
                return;
            }
            // Гость атакует — только если разрешено гостям.
            if (!worlds.hasBuildAccess(attacker, data) && !settings.isVisitorsCanPvp()) {
                e.setCancelled(true);
                deny(attacker);
            }
            return;
        }
        // Рамки, стойки, транспорт — гостям трогать нельзя.
        if ((victim instanceof Hanging || victim instanceof ArmorStand || victim instanceof Vehicle)
                && !worlds.hasBuildAccess(attacker, data)) {
            e.setCancelled(true);
            deny(attacker);
            return;
        }
        // Мобы: гостям — по настройке can-pve.
        if (victim instanceof LivingEntity
                && !worlds.hasBuildAccess(attacker, data)
                && !settings.isVisitorsCanPve()) {
            e.setCancelled(true);
            deny(attacker);
        }
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) return (Player) shooter;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        if (e.getRemover() instanceof Player) {
            Player remover = (Player) e.getRemover();
            if (isVisitor(remover, e.getEntity().getWorld())) {
                e.setCancelled(true);
                deny(remover);
            }
        }
    }

    // ---------- предметы и транспорт ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!settings.isVisitorsCanDrop() && isVisitor(e.getPlayer(), e.getPlayer().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player) {
            Player player = (Player) e.getEntity();
            if (!settings.isVisitorsCanPickup() && isVisitor(player, player.getWorld())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player) {
            Player player = (Player) e.getEntered();
            if (!settings.isVisitorsCanRide() && isVisitor(player, e.getVehicle().getWorld())) {
                e.setCancelled(true);
                deny(player);
            }
        }
    }

    // ---------- порталы (оптимизация: не плодим ад/энд на каждого) ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) {
        if (!settings.isAllowNetherEnd() && dataOf(e.getWorld()) != null) {
            e.setCancelled(true);
        }
    }
}
