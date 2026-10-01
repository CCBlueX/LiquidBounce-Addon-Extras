package net.ccbluex.liquidbounce.extras.modules.grinding;

import net.ccbluex.liquidbounce.config.types.list.ChoiceListValue;
import net.ccbluex.liquidbounce.config.types.list.Tagged;
import net.ccbluex.liquidbounce.event.events.MovementInputEvent;
import net.ccbluex.liquidbounce.extras.ExtrasCategories;
import net.ccbluex.liquidbounce.features.module.ClientModule;

/**
 * Presses jump for you. The input event carries this tick's keys, so the jump is as legitimate as a
 * real key press.
 */
public final class ModuleAutoJump extends ClientModule {

    public static final ModuleAutoJump INSTANCE = new ModuleAutoJump();

    private final ChoiceListValue<When> when = enumChoice("When", When.MOVING);

    @SuppressWarnings("unused")
    private final AutoCloseable inputHandler = on(MovementInputEvent.class, this::onMovementInput);

    private ModuleAutoJump() {
        super("AutoJump", ExtrasCategories.GRINDING);
    }

    private void onMovementInput(MovementInputEvent event) {
        var player = getPlayer();
        if (!player.onGround() || event.getSneak()) {
            return;
        }

        var moving = event.getDirectionalInput().isMoving();
        var jump = switch (when.get()) {
            case ALWAYS -> true;
            case MOVING -> moving;
            case SPRINTING -> moving && player.isSprinting();
        };
        event.setJump(event.getJump() || jump);
    }

    private enum When implements Tagged {
        ALWAYS("Always"),
        MOVING("Moving"),
        SPRINTING("Sprinting");

        private final String tag;

        When(String tag) {
            this.tag = tag;
        }

        @Override
        public String getTag() {
            return tag;
        }
    }

}
