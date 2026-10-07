package net.ccbluex.liquidbounce.extras.modules.grinding

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EntityTypes
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.armadillo.Armadillo
import net.minecraft.world.entity.animal.camel.Camel
import net.minecraft.world.entity.animal.equine.AbstractHorse
import net.minecraft.world.entity.animal.equine.Donkey
import net.minecraft.world.entity.animal.equine.Horse
import net.minecraft.world.entity.animal.equine.Llama
import net.minecraft.world.entity.animal.feline.Ocelot
import net.minecraft.world.entity.animal.panda.Panda
import net.minecraft.world.entity.animal.turtle.Turtle
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

internal object BreedingRules {
    val supported: Set<EntityType<*>> = linkedSetOf(
        EntityTypes.COW, EntityTypes.MOOSHROOM, EntityTypes.SHEEP, EntityTypes.PIG, EntityTypes.CHICKEN,
        EntityTypes.RABBIT, EntityTypes.WOLF, EntityTypes.CAT, EntityTypes.OCELOT, EntityTypes.HORSE,
        EntityTypes.DONKEY, EntityTypes.LLAMA, EntityTypes.TRADER_LLAMA, EntityTypes.FOX, EntityTypes.BEE,
        EntityTypes.PANDA, EntityTypes.TURTLE, EntityTypes.STRIDER, EntityTypes.HOGLIN, EntityTypes.AXOLOTL,
        EntityTypes.GOAT, EntityTypes.FROG, EntityTypes.SNIFFER, EntityTypes.ARMADILLO, EntityTypes.CAMEL,
        EntityTypes.NAUTILUS,
    )

    fun eligible(animal: Animal): Boolean {
        if (!animal.isAlive || animal.isBaby || animal.isPassenger || animal.isVehicle) return false
        return when (animal) {
            is Camel -> animal.health >= animal.maxHealth
            is AbstractHorse -> animal.isTamed && animal.health >= animal.maxHealth
            is TamableAnimal -> animal.isTame && !animal.isInSittingPose && animal.health >= animal.maxHealth
            is Ocelot -> animal.isTrusting
            is Turtle -> !animal.hasEgg()
            is Panda -> !animal.isScared && !animal.isOnBack && !animal.isSitting && !animal.isEating
            is Armadillo -> !animal.isScared
            else -> true
        }
    }

    fun food(animal: Animal, stack: ItemStack): Boolean = !stack.isEmpty && when (animal) {
        is Llama -> stack.`is`(Items.HAY_BLOCK)
        is Camel -> animal.isFood(stack)
        is AbstractHorse -> stack.`is`(Items.GOLDEN_CARROT) || stack.`is`(Items.GOLDEN_APPLE)
        else -> animal.isFood(stack)
    }

    // canMate also checks unsynchronized love/cooldown state on several species.
    fun partners(first: Animal, second: Animal): Boolean = first !== second && when {
        first is Llama && second is Llama -> true
        first is Horse && second is Donkey || first is Donkey && second is Horse -> true
        else -> first.type === second.type
    }
}
