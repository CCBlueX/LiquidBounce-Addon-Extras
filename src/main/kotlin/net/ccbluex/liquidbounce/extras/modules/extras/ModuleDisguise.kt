package net.ccbluex.liquidbounce.extras.modules.extras

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ccbluex.liquidbounce.config.types.list.Tagged
import net.ccbluex.liquidbounce.event.events.GameTickEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.event.waitSeconds
import net.ccbluex.liquidbounce.extras.ExtrasCategories
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.type.Color4b
import net.minecraft.ChatFormatting
import net.minecraft.data.AtlasIds
import net.minecraft.locale.Language
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.contents.objects.AtlasSprite
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.FormattedCharSink
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.PlayerSkin
import java.util.UUID

/**
 * Shows the nearest player who is not your friend as someone else: another name with a rank tag wherever the
 * game draws text, and the skin of a Mojang account. Text reaches [disguise] through `MixinFont` and
 * `MixinStringSplitter`, skins reach [skinOf] through `MixinAbstractClientPlayer` and `MixinPlayerInfo`, the
 * label above their head reaches [nameTag] through `MixinEntityRenderer`.
 */
object ModuleDisguise : ClientModule("Disguise", ExtrasCategories.EXTRAS) {

    private const val SKIN_RETRY_SECONDS = 5

    private val shownAs by text("Name", "Notch")
    private val skinUsername by text("Skin", "Notch")
    private val rank by text("Rank", "[Admin]")
    private val rankColor by color("RankColor", Color4b(255, 85, 85))
    private val tier by enumChoice("Tier", Tier.OFF)
    private val gamemode by enumChoice("Gamemode", Gamemode.SWORD)

    /** The player in disguise. */
    @Volatile
    var target: Target? = null
        private set

    /** The skin of the `Skin` account, once Mojang and the texture download delivered it. */
    @Volatile
    var skin: PlayerSkin? = null
        private set

    private var skinFetchedFor: String? = null

    private val shownName get() = if (rank.isBlank()) shownAs else "$shownAs $rank"

    @Suppress("unused")
    private val targetHandler = handler<GameTickEvent> {
        val nearest = world.players()
            .filter { it !== player && !FriendManager.isFriend(it) }
            .minByOrNull { it.distanceToSqr(player) }
        val current = target
        if (nearest?.uuid != current?.id || nearest?.gameProfile?.name != current?.name) {
            target = nearest?.let { Target(it.uuid, it.gameProfile.name) }
        }
    }

    @Suppress("unused")
    private val skinHandler = tickHandler(Dispatchers.Main) {
        val username = skinUsername
        if (username == skinFetchedFor) {
            return@tickHandler
        }
        skinFetchedFor = username
        skin = null
        skin = withContext(Dispatchers.IO) { fetchSkin(username) }
        if (skin == null) {
            // Mojang turns an address away for a while when it asks too often; ask again later
            waitSeconds(SKIN_RETRY_SECONDS)
            if (skinFetchedFor == username) {
                skinFetchedFor = null
            }
        }
    }

    override fun onDisabled() {
        target = null
        skin = null
        skinFetchedFor = null
    }

    private fun fetchSkin(username: String): PlayerSkin? {
        val services = mc.services()
        // Not profileResolver(): it keeps a refused lookup as a name without a profile, and every retry gets that
        val id = services.profileRepository().findProfileByName(username).orElse(null)?.id() ?: return null
        val profile = services.sessionService().fetchProfile(id, false)?.profile() ?: return null
        return mc.skinManager.get(profile).join().orElse(null)
    }

    fun skinOf(id: UUID): PlayerSkin? = skin?.takeIf { target?.id == id }

    /** The label above [entity]'s head, with the tier tag in front when [entity] is the target. */
    fun nameTag(entity: Entity, label: Component?): Component? {
        val tier = tier
        if (label == null || tier == Tier.OFF || entity.uuid != target?.id) {
            return label
        }
        val icon = AtlasSprite(AtlasIds.ITEMS, Identifier.withDefaultNamespace(gamemode.sprite))
        return Component.empty()
            .append(Component.`object`(icon, Component.literal(gamemode.tag)))
            .append(" ")
            .append(Component.literal(tier.tag).withColor(tier.color))
            .append(Component.literal(" | ").withStyle(ChatFormatting.GRAY))
            .append(label)
    }

    fun disguise(text: String): String {
        val target = target ?: return text
        if (target.name !in text) {
            return text
        }
        return target.pattern.replace(text, Regex.escapeReplacement(shownName))
    }

    fun disguise(text: FormattedCharSequence): FormattedCharSequence {
        val target = target ?: return text
        val plain = StringBuilder()
        text.accept { _, _, codePoint ->
            plain.appendCodePoint(codePoint)
            true
        }
        val starts = target.pattern.findAll(plain).map { it.range.first }.toList()
        if (starts.isEmpty()) {
            return text
        }
        return DisguisedText(text, starts.toIntArray(), target.name.length, shownAs, rank, rankColor.argb)
    }

    /** [text] in disguise, or null when it does not name the target. */
    fun disguise(text: FormattedText): FormattedCharSequence? {
        if (target == null) {
            return null
        }
        val sequence = Language.getInstance().getVisualOrder(text)
        return disguise(sequence).takeIf { it !== sequence }
    }

    class Target(val id: UUID, val name: String) {
        // A name inside a longer one is somebody else
        val pattern = Regex("(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])")
    }

    @Suppress("MagicNumber")
    enum class Tier(override val tag: String, val color: Int) : Tagged {
        OFF("Off", 0),
        HT1("HT1", 0xFFD24A),
        LT1("LT1", 0xD6AE55),
        HT2("HT2", 0xC9D3E0),
        LT2("LT2", 0x9AA6B5),
        HT3("HT3", 0xF0954A),
        LT3("LT3", 0xC0784A),
        HT4("HT4", 0x7FD67F),
        LT4("LT4", 0x5EA05E),
        HT5("HT5", 0x8FA8C8),
        LT5("LT5", 0x6E7E92),
    }

    /** Each kit is shown by the item sprite the game already has for it. */
    enum class Gamemode(override val tag: String, val sprite: String) : Tagged {
        SWORD("Sword", "item/diamond_sword"),
        AXE("Axe", "item/diamond_axe"),
        MACE("Mace", "item/mace"),
        CRYSTAL("Crystal", "item/end_crystal"),
        UHC("UHC", "item/lava_bucket"),
        POT("Pot", "item/splash_potion"),
        NETH_POT("NethPot", "item/netherite_helmet"),
        SMP("SMP", "item/ender_pearl"),
    }

}

/** [original] with each name starting at [starts] replaced by [name], in its style, and [rank] in [rankColor]. */
private class DisguisedText(
    private val original: FormattedCharSequence,
    private val starts: IntArray,
    private val nameLength: Int,
    private val name: String,
    private val rank: String,
    private val rankColor: Int,
) : FormattedCharSequence {

    override fun accept(sink: FormattedCharSink): Boolean {
        // Matches index the plain text in UTF-16 units, the sink sees the index of what it was given so far
        var source = 0
        var output = 0
        var next = 0
        var skipUntil = 0

        fun emit(text: String, style: Style): Boolean {
            var index = 0
            while (index < text.length) {
                val codePoint = text.codePointAt(index)
                if (!sink.accept(output, style, codePoint)) {
                    return false
                }
                output += Character.charCount(codePoint)
                index += Character.charCount(codePoint)
            }
            return true
        }

        return original.accept { _, style, codePoint ->
            val at = source
            source += Character.charCount(codePoint)
            when {
                at < skipUntil -> true
                next < starts.size && at == starts[next] -> {
                    next++
                    skipUntil = at + nameLength
                    emit(name, style) && (rank.isBlank() || emit(" ", style) && emit(rank, style.withColor(rankColor)))
                }
                else -> sink.accept(output, style, codePoint).also { output += Character.charCount(codePoint) }
            }
        }
    }

}
