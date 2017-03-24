import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.Arrays;

/**
 * Represents the non-handshake messages.
 */
class Message {

    // Header includes 4 byte message length, 1 byte message type
    private static final int HEADER_LEN = 5;

    // All possible messages, corresponding to values 0..7
    public enum Type {
        Choke, //0
        Unchoke, //1
        Interested, //2 ...
        NotInterested,
        Have,
        Bitfield,
        Request,
        Piece; //7

        // Get which type from byte value 0..7
        public static Type from(byte s) {
            return values()[s];
        }

        // Return type as byte value 0..7
        public byte asByte() {
            return (byte)Arrays.asList(values()).indexOf(this);
        }
    };

    public final Type type; // type (1 byte)
    // TODO: Is len supposed to inclurde the type bytes? Right now it doesn't
    private final int len; // length (4 bytes)
    private final ByteBuffer payload; // payload (len bytes)

    private Message(Type t, int len, ByteBuffer payload) {
        this.type = t;
        this.len = len;
        this.payload = payload;
    }

    // Get message from input stream
    public static Message from_stream(InputStream in) throws java.io.IOException {

        // Read in the header
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN);
        in.read(buf.array(), 0, HEADER_LEN);

        // Get the length and type from the header
        int len = buf.getInt();
        Type t = Type.from(buf.get());

        // Read in the payload (which should be of length len)
        ByteBuffer payload = ByteBuffer.allocate(len);
        in.read(payload.array(), 0, len);

        return new Message(t, len, payload);
    }

    // Put this message on an output stream
    public void to_stream(OutputStream out) throws java.io.IOException {
        // Allocate enough space for entire message 
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + this.len);
        // length of message
        buf.putInt(this.len);
        // type of message
        buf.put(this.type.asByte());
        // payload
        buf.put(this.payload.array());

        // write entire message
        out.write(buf.array());
    }

    // Make a message with no payload (for example, not interested)
    public static Message empty(Type t) {
        return new Message(t, 0, ByteBuffer.allocate(0));
    }

    // A message that is just the index of a piece (for example, a request)
    public static Message index(Type t, int index) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(index);
        return new Message(t, 4, buf);
    }

    // Make a bitfield message 
	public static Message bitfield(BitSet bits) {
        ByteBuffer buf = ByteBuffer.wrap(bits.toByteArray());
        return new Message(Type.Bitfield, buf.array().length, buf);
    }

    // A message transmitting a file piece
    public static Message piece(Type t, byte[] contents) {
        return new Message(t, contents.length, ByteBuffer.wrap(contents));
    }

    // TODO: Other payloads
    public Payload getPayload() {
        switch(this.type) {
            case Choke:
            case Unchoke:
            case Interested:
            case NotInterested:
                return new NoPayload();
            case Have:
            case Request:
                return new IndexPayload(this.payload);
            case Bitfield:
                return new BitfieldPayload(this.payload);
            case Piece:
                return new PiecePayload(this.payload);
        }
        return null;
    }

    public abstract class Payload {}

    public class NoPayload extends Payload {}

    // A payload that is only an index (for a piece request, for example)
    public class IndexPayload extends Payload {
        public final int index;
        private IndexPayload(ByteBuffer buf) {
            this.index = buf.getInt();
        }
    }

    // A payload that is a bitfield (for sending/receiving bitfields between peers)
    public class BitfieldPayload extends Payload {
        public final BitSet bitfield;
        private BitfieldPayload(ByteBuffer buf) {
            this.bitfield = BitSet.valueOf(buf);
        }
    }

    // a payload that is a piece of a file
    public class PiecePayload extends Payload {
        public final int index; 
        public final ByteBuffer content;
        private PiecePayload(ByteBuffer buf) {
            this.index = buf.getInt();
            this.content = buf.slice().asReadOnlyBuffer();
        }
    }
}
