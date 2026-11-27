package dev.specbuilder.capabilities;

import java.util.*;

/**
 * How alike two capability names (or descriptions) are. Names are compared after normalising: lower case,
 * "&amp;" as "and", punctuation dropped, filler words ("management", "services", "process"…) ignored, and
 * plurals made singular. The score is the larger of the word overlap and the edit-distance similarity.
 */
final class Similarity {

    private static final Set<String> FILLER = Set.of("and", "of", "the", "for", "to", "a", "an", "in", "on", "with", "by",
            "management", "mgmt", "manage", "managing", "service", "services", "process", "processing", "handling", "capability",
            "capabilities", "function", "functions");

    private Similarity() {
    }

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace("&", " and ").replaceAll("[^\\p{L}\\p{N}]+", " ").strip().replaceAll("\\s+", " ");
    }

    /** Meaningful words, singular. */
    static Set<String> words(String s) {
        Set<String> out = new LinkedHashSet<>();
        for (String w : normalize(s).split(" ")) {
            if (w.isEmpty() || FILLER.contains(w)) continue;
            out.add(singular(w));
        }
        return out;
    }

    static String singular(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && (w.endsWith("ses") || w.endsWith("xes") || w.endsWith("ches") || w.endsWith("shes"))) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) return w.substring(0, w.length() - 1);
        return w;
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> both = new HashSet<>(a);
        both.retainAll(b);
        Set<String> either = new HashSet<>(a);
        either.addAll(b);
        return (double) both.size() / either.size();
    }

    /** 1 for the same text, falling towards 0 as more edits are needed. */
    static double editSimilarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1;
        if (a.isEmpty() || b.isEmpty()) return 0;
        int longest = Math.max(a.length(), b.length());
        if (Math.abs(a.length() - b.length()) > longest / 2) return 0;
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return 1.0 - (double) prev[b.length()] / longest;
    }

    /** The name score: words first (order and filler don't matter), spelling second. */
    static double names(String a, String b) {
        Set<String> wa = words(a);
        Set<String> wb = words(b);
        String ja = String.join(" ", new TreeSet<>(wa));
        String jb = String.join(" ", new TreeSet<>(wb));
        if (!ja.isEmpty() && ja.equals(jb)) return 1;
        return Math.max(jaccard(wa, wb), editSimilarity(ja, jb));
    }

    /** One name's meaningful words all appear in the other's (e.g. "Refunds" and "Card refunds"). */
    static boolean contains(String a, String b) {
        Set<String> wa = words(a);
        Set<String> wb = words(b);
        if (wa.isEmpty() || wb.isEmpty() || wa.equals(wb)) return false;
        return wa.containsAll(wb) || wb.containsAll(wa);
    }
}
