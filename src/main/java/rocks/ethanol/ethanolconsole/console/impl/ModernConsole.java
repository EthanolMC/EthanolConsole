package rocks.ethanol.ethanolconsole.console.impl;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import rocks.ethanol.ethanolconsole.console.Console;

public final class ModernConsole implements Console {

    private final Terminal terminal;
    private final Console.Handler handler;
    private final LineReader reader;
    private boolean running;

    public ModernConsole(final Terminal terminal, final Handler handler) {
        this.terminal = terminal;
        this.handler = handler;
        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .history(new DefaultHistory())
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .option(LineReader.Option.INSERT_TAB, false)
                .build();
        this.running = false;
    }

    @Override
    public void println(final String text) {
        this.reader.printAbove(text);
    }

    @Override
    public void run() {
        this.running = true;
        try {
            while (this.running) {
                try {
                    final String line = this.reader.readLine("> ");
                    if (line == null) {
                        break;
                    }

                    this.handler.handleInput(this, line);
                } catch (final EndOfFileException ignored) { }
            }
        } catch (final UserInterruptException ignored) { }
        this.running = false;
        this.handler.handleShutdown();
    }

    @Override
    public void close() {
        this.running = false;
    }

    public final Terminal terminal() {
        return this.terminal;
    }

    public final LineReader reader() {
        return this.reader;
    }
}
