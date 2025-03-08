package rocks.ethanol.ethanolconsole;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class RemoteConnection {

    private final Socket socket;
    private final DataInputStream inputStream;
    private final DataOutputStream outputStream;
    private RemoteConnection.Mode mode;

    public RemoteConnection(final Socket socket) throws IOException {
        this.socket = socket;
        this.inputStream = new DataInputStream(socket.getInputStream());
        this.outputStream = new DataOutputStream(socket.getOutputStream());
        this.mode = RemoteConnection.Mode.DEFAULT;
    }

    public synchronized void send(final String input) throws IOException {
        this.outputStream.write(2);
        final byte[] data = input.getBytes(StandardCharsets.UTF_8);
        this.outputStream.writeInt(data.length);
        this.outputStream.write(data);
    }

    public synchronized void changeMode(final RemoteConnection.Mode mode) throws IOException {
        this.mode = mode;
        this.outputStream.write(3);
        this.outputStream.write(mode.ordinal());
    }

    public void run(final RemoteConnection.Listener listener) throws IOException {
        boolean mightBeTimingOut = false;

        while (this.socket.isConnected()) {
            int read;
            try {
                read = inputStream.readUnsignedByte();
                mightBeTimingOut = false;
            } catch (final SocketTimeoutException exception) {
                if (mightBeTimingOut)
                    throw exception;

                mightBeTimingOut = true;
                synchronized (this.outputStream) { // send ping
                    this.outputStream.write(0);
                    this.outputStream.flush();
                }
                continue;
            }

            switch (read) {
                case 0: { // ping
                    synchronized (this) { // send pong
                        this.outputStream.write(1);
                        this.outputStream.flush();
                    }
                    break;
                }
                case 1: { // pong
                    break;
                }
                case 2: { // message
                    final byte[] bytes = new byte[this.inputStream.readInt()];
                    this.inputStream.readFully(bytes);
                    listener.handleMessage(new String(bytes, StandardCharsets.UTF_8));
                    break;
                }
            }
        }
    }

    @FunctionalInterface
    public interface Listener {

        void handleMessage(final String message);
    }


    public enum Mode {
        DEFAULT, RCON, SHELL;
    }
}
