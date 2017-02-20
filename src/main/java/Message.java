import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.util.BitSet;

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

        public static Type from(short s) {
            return values()[s];
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

    public static Message from_steam(InputStream in) throws java.io.IOException {
        ByteBuffer buf = ByteBuffer.allocate(HEADER_LEN);
        in.read(buf.array(), 0, HEADER_LEN);

        int len = buf.getInt();
        Type t = Type.from(buf.getShort());

        ByteBuffer payload = ByteBuffer.allocate(len);
        in.read(payload.array(), 0, len);

        return new Message(t, len, buf);
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
