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
    private static final int HEADER_LEN = 5;

    public enum Type {
        Choke,
        Unchoke,
        Interested,
        NotInterested,
        Have,
        Bitfield,
        Request,
        Piece;

        public static Type from(byte s) {
            return values()[s];
        }

        public byte asByte() {
            return (byte)Arrays.asList(values()).indexOf(this);
        }
    };

    public final Type type;
    private final int len;
    private final ByteBuffer buf;

    private Message(Type t, int len, ByteBuffer buf) {
        this.type = t;
        this.len = len;
        this.buf = buf;
    }

    public static Message from_stream(InputStream in) throws java.io.IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN);
        in.read(buf.array(), 0, HEADER_LEN);

        int len = buf.getInt();
        Type t = Type.from(buf.get());

        ByteBuffer payload = ByteBuffer.allocate(len);
        in.read(payload.array(), 0, len);

        return new Message(t, len, buf);
    }

    public void to_stream(OutputStream out) throws java.io.IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN + this.len);
        buf.putInt(this.len);
        buf.put(this.type.asByte());
        buf.put(this.buf.array());

        out.write(buf.array());
    }

    public static Message empty(Type t) {
        return new Message(t, 0, ByteBuffer.allocate(0));
    }

    public static Message index(Type t, int index) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(index);
        return new Message(t, 4, buf);
    }

    public static Message bitfield(BitSet bits) {
        ByteBuffer buf = ByteBuffer.wrap(bits.toByteArray());
        return new Message(Type.Bitfield, buf.array().length, buf);
    }

    public static Message piece(Type t, byte[] contents) {
        return new Message(t, contents.length, ByteBuffer.wrap(contents));
    }

    public Payload getPayload() {
        switch(this.type) {
            case Choke:
            case Unchoke:
            case Interested:
            case NotInterested:
                return new NoPayload();
            case Have:
            case Request:
                return new IndexPayload(this.buf);
            case Bitfield:
                return new BitfieldPayload(this.buf);
            case Piece:
                return new PiecePayload(this.buf);
        }
        return null;
    }

    public abstract class Payload {}

    public class NoPayload extends Payload {}
    public class IndexPayload extends Payload {
        public final int index;
        private IndexPayload(ByteBuffer buf) {
            this.index = buf.getInt();
        }
    }

    public class BitfieldPayload extends Payload {
        public final BitSet bitfield;
        private BitfieldPayload(ByteBuffer buf) {
            this.bitfield = BitSet.valueOf(buf);
        }
    }

    public class PiecePayload extends Payload {
        public final int index; 
        public final ByteBuffer content;
        private PiecePayload(ByteBuffer buf) {
            this.index = buf.getInt();
            this.content = buf.slice().asReadOnlyBuffer();
        }
    }
}
