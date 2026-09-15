package net.ccbluex.liquidbounce.extras.modules;

import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;
import net.ccbluex.liquidbounce.config.types.list.Tagged;
import net.ccbluex.liquidbounce.event.events.MovementInputEvent;
import net.ccbluex.liquidbounce.features.module.ClientModule;

/**
 * Jumps for you. Input events carry the player's keys as mutable fields; setting one changes what the
 * game sees this tick, no key press involved.
 */
public class ModuleAutoJump extends ClientModule {

    enum When implements Tagged {
        ALWAYS("Always"),
        MOVING("Moving"),
        SPRINTING("Sprinting");

        final String tag;

        When(String tag) {
            this.tag = tag;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }

    private final ChoiceListValue<When> when = enumChoice("When", When.MOVING);

    public ModuleAutoJump() {
        super("AutoJump", ExtrasCategories.EXTRAS);
        on(MovementInputEvent.class, this::onInput);
    }

    private void onInput(MovementInputEvent event) {
        if (!getPlayer().onGround() || event.getSneak()) {
            return;
        }

        boolean moving = event.getDirectionalInput().isMoving();
        boolean jump = switch (when.get()) {
            case ALWAYS -> true;
            case MOVING -> moving;
            case SPRINTING -> moving && getPlayer().isSprinting();
        };
        if (jump) {
            event.setJump(true);
        }
    }

}
