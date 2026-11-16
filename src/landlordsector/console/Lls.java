package landlordsector.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.lazywizard.console.BaseCommand.CommandContext;
import org.lazywizard.console.Console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Portfolio;
import landlordsector.Property;
import landlordsector.Tenant;

/**
 * Shared helpers for the console commands (1.6.0, Andrzej: "a whole suite of console commands for the mod").
 *
 * Console Commands is optional: only the classes in this package touch it, and only Console Commands loads them
 * (from data/console/commands.csv), so the mod runs the same without it.
 *
 * A home is named by its number in lls_homes, by its id, or by "here" (the first home on the market you are docked
 * at). With no home named, "here" is used.
 */
final class Lls {

    private Lls() {
    }

    /** The words of the arguments, never null. */
    static String[] words(String args) {
        String t = args == null ? "" : args.trim();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    /** Everything from word {@code from} on, joined with spaces. */
    static String rest(String[] w, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < w.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(w[i]);
        }
        return sb.toString();
    }

    static Random random() {
        return new Random();
    }

    /** The market you are docked at, or null with a message. */
    static MarketAPI docked(CommandContext context) {
        if (context.isInMarket() && context.getMarket() != null) return context.getMarket();
        Console.showMessage("Dock at a market first.");
        return null;
    }

    /** The home named by {@code token} (number, id or "here"); null with a message when there is none. */
    static Property home(String token, CommandContext context) {
        List<Property> all = Portfolio.get().properties();
        if (all.isEmpty()) {
            Console.showMessage("You own no homes. lls_givehome gives you one.");
            return null;
        }
        if (token == null || token.isEmpty() || "here".equalsIgnoreCase(token)) {
            MarketAPI m = context.isInMarket() ? context.getMarket() : null;
            if (m != null) {
                for (Property p : all) if (m.getId().equals(p.marketId)) return p;
            }
            if (all.size() == 1) return all.get(0);
            Console.showMessage("Name a home by its number from lls_homes, or dock where you own one.");
            return null;
        }
        try {
            int n = Integer.parseInt(token);
            if (n >= 1 && n <= all.size()) return all.get(n - 1);
            Console.showMessage("There is no home number " + n + ". You own " + all.size() + ".");
            return null;
        } catch (NumberFormatException ignored) {
        }
        for (Property p : all) if (token.equals(p.id)) return p;
        Console.showMessage("No home \"" + token + "\". Use its number from lls_homes.");
        return null;
    }

    /** The home's tenant, or null with a message. */
    static Tenant tenant(Property p) {
        if (p.tenant == null) Console.showMessage(p.displayName() + " has no tenant. lls_tenant moves one in.");
        return p.tenant;
    }

    /** "3. Silver Burrow (Shack) on Chicomoztoc". */
    static String line(Property p) {
        int n = Portfolio.get().properties().indexOf(p) + 1;
        return n + ". " + p.displayName() + " (" + p.tier().name + ") on " + p.marketName;
    }

    static String credits(float c) {
        return Misc.getDGSCredits(c);
    }

    /** A whole number from a word, or null with a message. */
    static Integer number(String word, int min, int max, String what) {
        try {
            int n = Integer.parseInt(word);
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {
        }
        Console.showMessage(what + " must be a whole number from " + min + " to " + max + ".");
        return null;
    }

    /** Ids that start with or contain {@code typed}, for "did you mean" lines. */
    static List<String> like(String typed, Iterable<String> ids) {
        List<String> out = new ArrayList<>();
        String t = typed.toLowerCase();
        for (String id : ids) if (id.toLowerCase().contains(t)) out.add(id);
        return out;
    }

    /** "No trait "x". Did you mean: a, b? (lls_ids traits lists them all)". */
    static void unknown(String what, String typed, Iterable<String> ids, String idsKind) {
        List<String> close = like(typed, ids);
        Console.showMessage("No " + what + " \"" + typed + "\"."
                + (close.isEmpty() ? "" : " Did you mean: " + String.join(", ", close) + "?")
                + " lls_ids " + idsKind + " lists them all.");
    }

    /** Game timestamp units in one day, measured from the clock itself. */
    static double unitsPerDay() {
        long now = Global.getSector().getClock().getTimestamp();
        long back = 1_000_000_000L;
        float days = Global.getSector().getClock().getElapsedDaysSince(now - back);
        return days > 0f ? back / (double) days : 86_400_000d;
    }

    /** The timestamp {@code days} days ago. */
    static long daysAgo(float days) {
        return Global.getSector().getClock().getTimestamp() - (long) (days * unitsPerDay());
    }
}
