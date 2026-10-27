package de.moinmornhart.stinkendreich.paper;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Kleiner NBT-Leser und -Schreiber für den Bauplan der Villa (villa.nbt) und für Truheninhalte. Paper bietet dafür
 * keine öffentliche Schnittstelle. Werte werden als Java-Objekte abgebildet: Compound = {@link Map}, Liste =
 * {@link List}, Zahlen als Byte/Short/Integer/Long/Float/Double, Text als String, Arrays als byte[]/int[]/long[].
 * Damit beim Zurückschreiben die Listentypen stimmen, merkt sich {@link Liste} den Typ ihrer Einträge.
 */
public final class Nbt {
    /** Liste mit dem Typ ihrer Einträge (für leere Listen wichtig). */
    public static final class Liste extends ArrayList<Object> {
        public final int typ;

        public Liste(int typ) {
            this.typ = typ;
        }
    }

    private Nbt() {
    }

    /** Liest eine gzip-komprimierte NBT-Datei (Wurzel ist ein Compound). */
    public static Map<String, Object> lesenGzip(InputStream ein) throws IOException {
        try (DataInputStream d = new DataInputStream(new GZIPInputStream(ein))) {
            int typ = d.readByte();
            if (typ != 10) {
                throw new IOException("NBT: Wurzel ist kein Compound");
            }
            d.readUTF();
            return compound(d);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object wert(DataInputStream d, int typ) throws IOException {
        return switch (typ) {
            case 1 -> d.readByte();
            case 2 -> d.readShort();
            case 3 -> d.readInt();
            case 4 -> d.readLong();
            case 5 -> d.readFloat();
            case 6 -> d.readDouble();
            case 7 -> {
                byte[] b = new byte[d.readInt()];
                d.readFully(b);
                yield b;
            }
            case 8 -> d.readUTF();
            case 9 -> {
                int eintragTyp = d.readByte();
                int n = d.readInt();
                Liste l = new Liste(eintragTyp);
                for (int i = 0; i < n; i++) {
                    l.add(wert(d, eintragTyp));
                }
                yield l;
            }
            case 10 -> compound(d);
            case 11 -> {
                int[] a = new int[d.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = d.readInt();
                }
                yield a;
            }
            case 12 -> {
                long[] a = new long[d.readInt()];
                for (int i = 0; i < a.length; i++) {
                    a[i] = d.readLong();
                }
                yield a;
            }
            default -> throw new IOException("NBT: unbekannter Typ " + typ);
        };
    }

    private static Map<String, Object> compound(DataInputStream d) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        while (true) {
            int typ = d.readByte();
            if (typ == 0) {
                return m;
            }
            String name = d.readUTF();
            m.put(name, wert(d, typ));
        }
    }

    // ------------------------------------------------------------------
    // Schreiben
    // ------------------------------------------------------------------

    /** Schreibt einen Compound gzip-komprimiert (Format von {@code NbtIo.writeCompressed}). */
    public static byte[] schreibenGzip(Map<String, Object> wurzel) {
        try {
            ByteArrayOutputStream puffer = new ByteArrayOutputStream();
            try (DataOutputStream d = new DataOutputStream(new GZIPOutputStream(puffer))) {
                d.writeByte(10);
                d.writeUTF("");
                schreibeCompound(d, wurzel);
            }
            return puffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int typ(Object w) {
        if (w instanceof Byte) return 1;
        if (w instanceof Short) return 2;
        if (w instanceof Integer) return 3;
        if (w instanceof Long) return 4;
        if (w instanceof Float) return 5;
        if (w instanceof Double) return 6;
        if (w instanceof byte[]) return 7;
        if (w instanceof String) return 8;
        if (w instanceof List) return 9;
        if (w instanceof Map) return 10;
        if (w instanceof int[]) return 11;
        if (w instanceof long[]) return 12;
        throw new IllegalArgumentException("NBT: kann " + w.getClass() + " nicht schreiben");
    }

    @SuppressWarnings("unchecked")
    private static void schreibeWert(DataOutputStream d, Object w) throws IOException {
        switch (typ(w)) {
            case 1 -> d.writeByte((Byte) w);
            case 2 -> d.writeShort((Short) w);
            case 3 -> d.writeInt((Integer) w);
            case 4 -> d.writeLong((Long) w);
            case 5 -> d.writeFloat((Float) w);
            case 6 -> d.writeDouble((Double) w);
            case 7 -> {
                byte[] b = (byte[]) w;
                d.writeInt(b.length);
                d.write(b);
            }
            case 8 -> {
                byte[] b = ((String) w).getBytes(StandardCharsets.UTF_8);
                if (b.length < 65535) {
                    d.writeUTF((String) w);
                } else {
                    throw new IOException("NBT: Text zu lang");
                }
            }
            case 9 -> {
                List<Object> l = (List<Object>) w;
                int eintragTyp = l instanceof Liste li ? li.typ : (l.isEmpty() ? 0 : typ(l.get(0)));
                d.writeByte(l.isEmpty() && !(l instanceof Liste) ? 0 : eintragTyp);
                d.writeInt(l.size());
                for (Object e : l) {
                    schreibeWert(d, e);
                }
            }
            case 10 -> schreibeCompound(d, (Map<String, Object>) w);
            case 11 -> {
                int[] a = (int[]) w;
                d.writeInt(a.length);
                for (int x : a) {
                    d.writeInt(x);
                }
            }
            case 12 -> {
                long[] a = (long[]) w;
                d.writeInt(a.length);
                for (long x : a) {
                    d.writeLong(x);
                }
            }
            default -> throw new IOException("NBT: unbekannter Typ");
        }
    }

    private static void schreibeCompound(DataOutputStream d, Map<String, Object> m) throws IOException {
        for (Map.Entry<String, Object> e : m.entrySet()) {
            d.writeByte(typ(e.getValue()));
            d.writeUTF(e.getKey());
            schreibeWert(d, e.getValue());
        }
        d.writeByte(0);
    }

    // ------------------------------------------------------------------
    // Bequemes Lesen
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> compound(Map<String, Object> m, String name) {
        Object w = m.get(name);
        return w instanceof Map ? (Map<String, Object>) w : Map.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> liste(Map<String, Object> m, String name) {
        Object w = m.get(name);
        return w instanceof List ? (List<Object>) w : List.of();
    }

    public static int zahl(Map<String, Object> m, String name, int sonst) {
        return m.get(name) instanceof Number n ? n.intValue() : sonst;
    }

    public static String text(Map<String, Object> m, String name, String sonst) {
        return m.get(name) instanceof String s ? s : sonst;
    }
}
