import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import rocks.ethanol.ethanolconsole.RemoteConnection;
import rocks.ethanol.ethanolconsole.console.Console;
import rocks.ethanol.ethanolconsole.console.impl.ModernConsole;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Bootstrap {

    private static final InetSocketAddress ADDRESS = new InetSocketAddress("_ftp._tcp.ethanol.rocks", 39996);

    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("§(?:([0-9a-fA-F])|([k-oK-OrR]))");

    private static final int[] COLORS = new int[] {
            0x000000, /* 0 */
            0x0000AA, /* 1 */
            0x00AA00, /* 2 */
            0x00AAAA, /* 3 */
            0xAA0000, /* 4 */
            0xAA00AA, /* 5 */
            0xFFAA00, /* 6 */
            0xAAAAAA, /* 7 */
            0x555555, /* 8 */
            0x5555FF, /* 9 */
            0x55FF55, /* a */
            0x55FFFF, /* b */
            0xFF5555, /* c */
            0xFF55FF, /* d */
            0xFFFF55, /* e */
            0xFFFFFF  /* f */
    };

    private static final String PREFIX = "/";

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: <ID:Key>");
            return;
        }

        final String[] split = args[0].split(":");
        if (split.length != 2) {
            System.err.println("Invalid format!");
            return;
        }

        final UUID id = UUID.fromString(split[0]);
        final UUID key = UUID.fromString(split[1]);

        final InetSocketAddress local;
        final InetSocketAddress target;

        {
            final Socket socket = new Socket();
            socket.setReuseAddress(true);
            socket.connect(Bootstrap.ADDRESS);

            local = (InetSocketAddress) socket.getLocalSocketAddress();

            final DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            final DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            {
                outputStream.writeLong(id.getMostSignificantBits());
                outputStream.writeLong(id.getLeastSignificantBits());
            }
            {
                outputStream.writeLong(key.getMostSignificantBits());
                outputStream.writeLong(key.getLeastSignificantBits());
            }
            final int status = inputStream.read();
            if (status != 0) {
                System.err.printf("Failed with status code '%s'!%n", status);
                return;
            }

            target = Bootstrap.readAddress(inputStream);

            socket.close();
        }

        final Terminal terminal = TerminalBuilder.builder().jansi(true).build();

        AnsiConsole.systemInstall();

        terminal.writer().printf("Connecting to '%s'...%n", target);
        final Socket socket = new Socket();
        socket.setSoTimeout(60_000);
        socket.setReuseAddress(true);
        socket.bind(local);
        socket.connect(target, 10_000);
        terminal.writer().println("Successfully connected!");

        final RemoteConnection connection = new RemoteConnection(socket);

        final Console.Handler handler = new Console.Handler() {

            @Override
            public void handleInput(final Console console, final String input) {
                if (input.startsWith(Bootstrap.PREFIX)) {
                    final String command = input.substring(Bootstrap.PREFIX.length());
                    if (command.equalsIgnoreCase("help")) {
                        console.println("Modes: ".concat(Arrays.stream(RemoteConnection.Mode.values()).map(RemoteConnection.Mode::name).collect(Collectors.joining(", "))));
                        return;
                    }

                    final RemoteConnection.Mode mode = Arrays.stream(RemoteConnection.Mode.values()).filter(value -> value.name().equalsIgnoreCase(command)).findFirst().orElse(null);
                    if (mode == null) {
                        console.println("Mode not found!");
                        return;
                    }

                    try {
                        connection.changeMode(mode);
                    } catch (final IOException exception) {
                        throw new RuntimeException(exception);
                    }

                    console.println(String.format("Mode set to '%s'.", mode.name()));

                    return;
                }
                try { // send message
                    connection.send(input);
                } catch (final IOException exception) {
                    throw new RuntimeException(exception);
                }
            }

            @Override
            public void handleShutdown() {
                System.exit(0);
            }
        };

        final ModernConsole console = new ModernConsole(terminal, handler);

        new Thread(console).start();

        try {
            connection.run(message -> console.println(Bootstrap.toAnsi(message)));
        } catch (final IOException ignored) {
            console.println("Connection closed");
            console.close();
        }
    }

    public static String toAnsi(String input) {
        final Ansi ansi = Ansi.ansi(input.length());

        Matcher matcher;
        while ((matcher = COLOR_CODE_PATTERN.matcher(input)).find()) {
            final String colorGroup = matcher.group(1);
            ansi.a(input.substring(0, matcher.start()));
            if (colorGroup != null) {
                ansi.reset().fgRgb(COLORS[Integer.parseInt(colorGroup, 16)]);
            } else {
                ansi.a(switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                    case 'k' -> Ansi.Attribute.CONCEAL_ON;
                    case 'l' -> Ansi.Attribute.INTENSITY_BOLD;
                    case 'm' -> Ansi.Attribute.STRIKETHROUGH_ON;
                    case 'n' -> Ansi.Attribute.UNDERLINE;
                    case 'o' -> Ansi.Attribute.ITALIC;
                    case 'r' -> Ansi.Attribute.RESET;
                    default -> throw new IllegalStateException();
                });
            }
            input = input.substring(matcher.end());
        }

        ansi.a(input);

        return ansi.reset().toString();
    }

    public static InetSocketAddress readAddress(final DataInputStream dataInputStream) throws IOException {
        final byte[] address = new byte[dataInputStream.readUnsignedByte()];
        if (address.length != 4 && address.length != 16)
            throw new IllegalStateException("Invalid IP-Address");

        dataInputStream.readFully(address);

        final int port = dataInputStream.readUnsignedShort();
        return new InetSocketAddress(InetAddress.getByAddress(address), port);
    }
}
