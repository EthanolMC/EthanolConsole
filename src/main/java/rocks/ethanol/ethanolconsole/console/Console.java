package rocks.ethanol.ethanolconsole.console;

import java.io.Closeable;

public interface Console extends Runnable, Closeable {

    void println(final String text);

    interface Handler {

        void handleInput(final Console console, final String input);
        void handleShutdown();
    }

}
