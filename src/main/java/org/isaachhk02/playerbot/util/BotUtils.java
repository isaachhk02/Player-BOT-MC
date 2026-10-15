package org.isaachhk02.playerbot.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.isaachhk02.playerbot.ai.AIPlayer;

/**
 * Utilidades para bots - métodos simples que funcionan con Mojang mappings 1.21.1
 */
public class BotUtils {

    /**
     * Hace que el bot mire hacia una posición
     */
    public static void lookAt(AIPlayer bot, Vec3 target) {
        Vec3 botPos = bot.getEyePosition();
        Vec3 direction = target.subtract(botPos);

        double horizontalDistance = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(direction.y, horizontalDistance));
        bot.setXRot(pitch);

        // Si el objetivo está casi justo encima o debajo del bot, el yaw es
        // ambiguo (atan2 con valores ~0) y podía provocar giros bruscos de 180°
        // que parecían "la cabeza moviéndose sola". En ese caso conservamos el
        // yaw actual.
        if (horizontalDistance < 0.6) {
            return;
        }

        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90.0F;
        bot.setYRot(yaw);
        bot.setYHeadRot(yaw);
    }

    /**
     * Hace que el bot mire hacia una entidad
     */
    public static void lookAtEntity(AIPlayer bot, Entity target) {
        lookAt(bot, target.getEyePosition());
    }

    /**
     * Equipa el item del slot indicado en la mano principal SIN duplicarlo ni
     * perder lo que hubiera en la mano: intercambia los contenidos de ambos
     * slots. (En un jugador, Player.setItemSlot / setItemInHand sobrescribe la
     * ranura seleccionada y deja el item original en su sitio, por eso aquí se
     * hace el swap manual.)
     */
    public static void equipToMainHand(AIPlayer bot, int slot) {
        if (slot < 0) {
            return;
        }
        var inv = bot.getInventory();
        int handSlot = inv.selected;
        if (handSlot == slot) {
            return;
        }
        ItemStack desired = inv.getItem(slot);
        ItemStack oldHand = inv.getItem(handSlot);
        inv.setItem(slot, oldHand);
        inv.setItem(handSlot, desired);
    }

    /**
     * Verifica si un item es comida
     */
    public static boolean isFood(ItemStack stack) {
        if (stack.isEmpty()) return false;

        // Verificar items de comida conocidos
        return stack.is(Items.APPLE) || stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP) ||
               stack.is(Items.COOKED_CHICKEN) || stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON) ||
               stack.is(Items.COOKED_MUTTON) || stack.is(Items.COOKED_RABBIT) || stack.is(Items.BREAD) ||
               stack.is(Items.CARROT) || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) ||
               stack.is(Items.DRIED_KELP) || stack.is(Items.HONEY_BOTTLE) || stack.is(Items.MELON_SLICE) ||
               stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES) || stack.is(Items.CHORUS_FRUIT) ||
               stack.is(Items.BAKED_POTATO) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.RABBIT_STEW) ||
               stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP) || stack.is(Items.SUSPICIOUS_STEW) ||
               stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE) || stack.is(Items.ROTTEN_FLESH) ||
               stack.is(Items.SPIDER_EYE) || stack.is(Items.PUFFERFISH) || stack.is(Items.SALMON) ||
               stack.is(Items.COD) || stack.is(Items.TROPICAL_FISH) || stack.is(Items.PORKCHOP) ||
               stack.is(Items.BEEF) || stack.is(Items.CHICKEN) || stack.is(Items.MUTTON) ||
               stack.is(Items.RABBIT) || stack.is(Items.POISONOUS_POTATO);
    }

    /**
     * Obtiene el valor nutricional estimado de un item
     */
    public static float getNutritionValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        // Valores nutricionales aproximados
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) return 20.0f;
        if (stack.is(Items.GOLDEN_APPLE)) return 16.0f;
        if (stack.is(Items.COOKED_BEEF) || stack.is(Items.COOKED_PORKCHOP)) return 8.0f;
        if (stack.is(Items.COOKED_MUTTON)) return 6.0f;
        if (stack.is(Items.COOKED_CHICKEN)) return 6.0f;
        if (stack.is(Items.BREAD)) return 5.0f;
        if (stack.is(Items.COOKED_RABBIT)) return 5.0f;
        if (stack.is(Items.COOKED_COD) || stack.is(Items.COOKED_SALMON)) return 5.0f;
        if (stack.is(Items.APPLE)) return 4.0f;
        if (stack.is(Items.CARROT)) return 4.0f;
        if (stack.is(Items.POTATO) || stack.is(Items.BAKED_POTATO)) return 5.0f;
        if (stack.is(Items.BEETROOT)) return 1.0f;
        if (stack.is(Items.DRIED_KELP)) return 1.0f;
        if (stack.is(Items.MELON_SLICE)) return 2.0f;
        if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)) return 2.0f;
        if (stack.is(Items.PUMPKIN_PIE)) return 8.0f;
        if (stack.is(Items.RABBIT_STEW) || stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP)) return 10.0f;
        if (stack.is(Items.HONEY_BOTTLE)) return 6.0f;
        if (stack.is(Items.CHORUS_FRUIT)) return 4.0f;
        if (stack.is(Items.ROTTEN_FLESH)) return 4.0f;
        if (stack.is(Items.SPIDER_EYE)) return 4.0f;

        return 1.0f; // Valor por defecto para otros items
    }

    /**
     * Verifica si una entidad está viva
     */
    public static boolean isAlive(Entity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }
        // Verificar salud si es LivingEntity
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getHealth() > 0;
        }
        return true;
    }

    /**
     * Calcula la distancia entre el bot y una posición
     */
    public static double distanceTo(AIPlayer bot, Vec3 pos) {
        return bot.position().distanceTo(pos);
    }

    /**
     * Calcula la distancia entre el bot y una entidad
     */
    public static double distanceTo(AIPlayer bot, Entity entity) {
        return bot.distanceTo(entity);
    }
}
