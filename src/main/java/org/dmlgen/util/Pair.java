package org.dmlgen.util;


public class Pair<C1,C2> {

    private final C1 fst;
    private final C2 snd;

    public Pair(C1 fst, C2 snd)
    {
        this.fst = fst;
        this.snd = snd;
    }

    public C1 fst() { return fst; }

    public C2 snd() { return snd; }

    public C1 getFst() { return fst; }
    public C2 getSnd() { return snd; }

    public static <C1,C2> Pair<C1,C2> make(C1 fst, C2 snd)
    {
        return new Pair<>(fst, snd);
    }

    public boolean equals(Object other)
    {
        if ( other == null )
            return false;

        @SuppressWarnings("rawtypes")
        Pair p = (Pair)other;

        return (fst == null && p.fst == null || fst != null && p.fst != null && fst.equals(p.fst)) &&
               (snd == null && p.snd == null || snd != null && p.snd != null && snd.equals(p.snd));
    }

    public int hashCode()
    {
        return (fst != null ? fst.hashCode() : 0 ) + 17 * (snd != null ? snd.hashCode() : 0);
    }

    public String toString()
    {
        return "(" + fst + "," + snd + ")";
    }
}
