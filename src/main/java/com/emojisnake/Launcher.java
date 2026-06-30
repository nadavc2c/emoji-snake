package com.emojisnake;

/**
 * Plain (non-{@link javafx.application.Application}) entry point for the packaged build.
 *
 * <p>When JavaFX rides on the <em>classpath</em> (as it does in the jpackage app-image - not the
 * module path), launching a class that {@code extends Application} directly trips the JVM's
 * "JavaFX runtime components are missing" guard. Bouncing through this tiny launcher sidesteps that.
 * The Gradle {@code run} task still uses {@link EmojiSnakeApp} as the main class (the OpenJFX plugin
 * sets up the module path there); jpackage points at this one.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        EmojiSnakeApp.main(args);
    }
}
