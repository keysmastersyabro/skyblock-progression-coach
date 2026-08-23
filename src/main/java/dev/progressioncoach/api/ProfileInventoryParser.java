package dev.progressioncoach.api;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Bounded reader for the compressed NBT used by Hypixel profile inventory fields.
 * It only counts item compounds and never exposes item names or writes anything back.
 */
final class ProfileInventoryParser {
    private static final int MAX_BYTES = 2_000_000;
    private static final int MAX_DEPTH = 32;

    private ProfileInventoryParser() {}

    static Result countAccessories(String encoded) {
        if (encoded == null || encoded.isBlank() || encoded.length() > MAX_BYTES) return Result.unavailable();
        try {
            byte[] compressed = Base64.getDecoder().decode(encoded);
            if (compressed.length > MAX_BYTES) return Result.unavailable();
            Holder holder = new Holder();
            try (DataInputStream input = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
                int rootType = input.readUnsignedByte();
                if (rootType != 10) return Result.unavailable();
                input.readUTF();
                readCompound(input, 0, false, holder);
            }
            return new Result(holder.items, true);
        } catch (Exception ignored) {
            return Result.unavailable();
        }
    }

    private static String readCompound(DataInputStream input, int depth, boolean extraAttributes, Holder holder)
            throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("NBT is too deep");
        String itemId = null;
        while (true) {
            int type = input.readUnsignedByte();
            if (type == 0) return itemId;
            String name = input.readUTF();
            String lower = name.toLowerCase(Locale.ROOT);
            String childId = readPayload(input, type, depth + 1, lower,
                    extraAttributes || lower.equals("extraattributes"), holder);
            if (childId != null && itemId == null) itemId = childId;
        }
    }

    private static String readPayload(DataInputStream input, int type, int depth, String name,
                                      boolean extraAttributes, Holder holder) throws IOException {
        return switch (type) {
            case 1 -> { input.readByte(); yield null; }
            case 2 -> { input.readShort(); yield null; }
            case 3 -> { input.readInt(); yield null; }
            case 4 -> { input.readLong(); yield null; }
            case 5 -> { input.readFloat(); yield null; }
            case 6 -> { input.readDouble(); yield null; }
            case 7 -> { skipBytes(input, input.readInt()); yield null; }
            case 8 -> {
                String value = input.readUTF();
                yield extraAttributes && name.equals("id") ? value : null;
            }
            case 9 -> readList(input, depth, holder);
            case 10 -> readCompound(input, depth, extraAttributes, holder);
            case 11 -> { skipBytes(input, Math.multiplyExact(input.readInt(), Integer.BYTES)); yield null; }
            case 12 -> { skipBytes(input, Math.multiplyExact(input.readInt(), Long.BYTES)); yield null; }
            default -> throw new IOException("Unsupported NBT tag " + type);
        };
    }

    private static String readList(DataInputStream input, int depth, Holder holder) throws IOException {
        int elementType = input.readUnsignedByte();
        int count = input.readInt();
        if (count < 0 || count > 50_000) throw new IOException("NBT list is too large");
        String firstId = null;
        for (int index = 0; index < count; index++) {
            String id = readUnnamedPayload(input, elementType, depth + 1, holder);
            if (id != null && firstId == null) firstId = id;
        }
        return firstId;
    }

    private static String readUnnamedPayload(DataInputStream input, int type, int depth, Holder holder)
            throws IOException {
        return switch (type) {
            case 1 -> { input.readByte(); yield null; }
            case 2 -> { input.readShort(); yield null; }
            case 3 -> { input.readInt(); yield null; }
            case 4 -> { input.readLong(); yield null; }
            case 5 -> { input.readFloat(); yield null; }
            case 6 -> { input.readDouble(); yield null; }
            case 7 -> { skipBytes(input, input.readInt()); yield null; }
            case 8 -> { input.readUTF(); yield null; }
            case 9 -> readList(input, depth, holder);
            case 10 -> {
                String id = readCompound(input, depth, false, holder);
                if (id != null && !id.isBlank()) holder.items++;
                yield id;
            }
            case 11 -> { skipBytes(input, Math.multiplyExact(input.readInt(), Integer.BYTES)); yield null; }
            case 12 -> { skipBytes(input, Math.multiplyExact(input.readInt(), Long.BYTES)); yield null; }
            default -> throw new IOException("Unsupported NBT tag " + type);
        };
    }

    private static void skipBytes(DataInputStream input, int length) throws IOException {
        if (length < 0 || length > MAX_BYTES) throw new IOException("NBT payload is too large");
        input.skipNBytes(length);
    }

    record Result(int count, boolean available) {
        static Result unavailable() { return new Result(-1, false); }
    }

    private static final class Holder {
        private int items;
    }
}
