package org.isaachhk02.playerbot.ai.tasks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.isaachhk02.playerbot.ai.AIPlayer;
import org.isaachhk02.playerbot.ai.AIState;
import org.isaachhk02.playerbot.ai.AITask;
import org.isaachhk02.playerbot.util.BotUtils;

import java.util.Comparator;

/**
 * Tarea de caza: cuando el bot tiene hambre (nivel de comida bajo y sin
 * provisiones) ataca animales de granja — vacas (incluidas las mooshroom),
 * cerdos, ovejas, gallinas y conejos — para conseguir carne cruda que luego
 * come (HealTask) o guarda.
 *
 * <p>NUNCA caza si no tiene hambre, ni ataca a otros animales, jugadores o
 * bots aliados. La carne que suelta la presa se recoge automáticamente al
 * inventario.</p>
 */
public class HuntTask extends AITask {
    private LivingEntity huntTarget;
    private int attackCooldown = 0;

    private static final int ATTACK_COOLDOWN_TICKS = 10;    // 0,5 s entre golpes
    private static final double ATTACK_RANGE = 3.0;
    private static final double CHASE_RANGE = 20.0;
    private static final double HUNT_RANGE = 24.0;
    private static final double PICKUP_RADIUS = 4.5;

    public HuntTask(AIPlayer bot) {
        super(bot);
        this.maxTimeoutTicks = 400; // 20 segundos cazando por pasada
    }

    @Override
    public void start() {
        super.start();
        attackCooldown = 0;
        huntTarget = findHuntTarget();
        if (huntTarget == null || !huntTarget.isAlive()) {
            fail();
            return;
        }
        equipWeapon();
    }

    @Override
    public void tick() {
        checkTimeout();

        // Recoger la carne que cae al suelo durante el combate.
        collectNearbyDrops();

        if (huntTarget == null || !huntTarget.isAlive()) {
            // La presa murió: recoger lo que soltó y buscar otra mientras siga
            // teniendo hambre.
            collectNearbyDrops();
            huntTarget = findHuntTarget();
            if (huntTarget == null) {
                complete();
                return;
            }
        }

        double distance = bot.distanceTo(huntTarget);

        // Si se aleja demasiado, dejar de perseguir.
        if (distance > CHASE_RANGE * 2) {
            complete();
            return;
        }

        BotUtils.lookAtEntity(bot, huntTarget);

        if (distance > ATTACK_RANGE) {
            bot.getNavigation().moveTo(huntTarget, 1.2);
        } else {
            if (--attackCooldown <= 0) {
                if (performAttack()) {
                    attackCooldown = ATTACK_COOLDOWN_TICKS;
                } else {
                    // La barra de ataque aún no está cargada: reintentar pronto.
                    attackCooldown = 1;
                }
            }
            bot.getNavigation().stop();
        }
    }

    /**
     * Golpe cuerpo a cuerpo REAL contra la presa (misma mecánica que FightTask:
     * espera la barra de ataque y usa {@link net.minecraft.world.entity.player.Player#attack}).
     */
    private boolean performAttack() {
        if (!(huntTarget instanceof LivingEntity)) {
            return false;
        }
        if (bot.getAttackStrengthScale(0.5f) < 1.0f) {
            return false;
        }

        bot.swing(InteractionHand.MAIN_HAND);
        bot.attack(huntTarget);
        bot.level().playSound(null, bot.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    /**
     * Busca el animal "de granja" más cercano que valga la pena cazar.
     * Solo vacas, cerdos, ovejas, gallinas y conejos (todas dan carne).
     */
    private LivingEntity findHuntTarget() {
        AABB box = AABB.ofSize(bot.position(), HUNT_RANGE * 2, HUNT_RANGE * 2, HUNT_RANGE * 2);
        return bot.level().getEntitiesOfClass(Animal.class, box,
                        e -> e.isAlive() && isHuntable(e) && bot.hasLineOfSight(e))
                .stream()
                .min(Comparator.comparingDouble(e -> bot.distanceToSqr(e)))
                .orElse(null);
    }

    /**
     * ¿Es un animal comestible (y no un bebé, que apenas suelta carne)?
     */
    private boolean isHuntable(Animal animal) {
        if (animal.isBaby()) {
            return false;
        }
        // Mooshroom extiende Cow, así que ya está incluida.
        return animal instanceof Cow
                || animal instanceof Pig
                || animal instanceof Sheep
                || animal instanceof Chicken
                || animal instanceof Rabbit;
    }

    /**
     * El bot tiene hambre: nivel de comida bajo, o sin comida que llevarse a la
     * boca y empezando a pasar hambre.
     */
    private boolean isHungry() {
        int foodLevel = bot.getFoodData().getFoodLevel();
        if (foodLevel < 10) {
            return true;
        }
        return foodLevel < 16 && countEdible() == 0;
    }

    private int countEdible() {
        int count = 0;
        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (BotUtils.isFood(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /**
     * Recoge los ItemEntity cercanos (carne, piel...) al inventario. Si no cabe
     * todo, el resto se queda en el suelo.
     */
    private void collectNearbyDrops() {
        AABB searchBox = bot.getBoundingBox().inflate(PICKUP_RADIUS);
        for (ItemEntity drop : bot.level().getEntitiesOfClass(ItemEntity.class, searchBox,
                item -> item.isAlive() && !item.getItem().isEmpty())) {
            ItemStack ground = drop.getItem();
            ItemStack toAdd = ground.copy();
            bot.getInventory().add(toAdd);

            if (toAdd.isEmpty()) {
                drop.discard();
            } else if (toAdd.getCount() < ground.getCount()) {
                ground.setCount(toAdd.getCount());
            }
        }
    }

    private void equipWeapon() {
        int bestSlot = -1;
        float bestDamage = 0.0f;

        for (int i = 0; i < bot.getInventory().getContainerSize(); i++) {
            ItemStack stack = bot.getInventory().getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.SwordItem) {
                float damage = 3.0f;
                String itemName = stack.getItem().toString().toLowerCase();
                if (itemName.contains("diamond")) damage = 7.0f;
                else if (itemName.contains("iron")) damage = 6.0f;
                else if (itemName.contains("stone")) damage = 5.0f;
                else if (itemName.contains("golden")) damage = 4.0f;
                else if (itemName.contains("wooden")) damage = 4.0f;

                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }

        // Si no tiene espada, pelea con la mano (solo se equipa si existe).
        BotUtils.equipToMainHand(bot, bestSlot);
    }

    @Override
    public int getPriority() {
        return 75;
    }

    @Override
    public String getName() {
        return "Hunting " + (huntTarget != null ? huntTarget.getName().getString() : "unknown");
    }

    @Override
    public AIState getState() {
        return AIState.HUNTING;
    }

    @Override
    public boolean canExecute() {
        // No cazar mientras pelea contra monstruos ni si no tiene hambre.
        if (bot.getTarget() != null || bot.isInCombat()) {
            return false;
        }
        if (!isHungry()) {
            return false;
        }
        return findHuntTarget() != null;
    }

    @Override
    public void stop() {
        bot.getNavigation().stop();
    }
}