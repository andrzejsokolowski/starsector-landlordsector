package landlordsector;

import com.fs.starfarer.api.util.Misc;

/**
 * 1.14.0 mortgages (LLS-44, Andrzej: "How about taking a mortgage through the property agent? Mortgages with interest
 * would be deducted from your monthly earnings." He took the proposal as it was): pay {@link Economy#mortgageDown} of
 * the price, borrow the rest for 5 or 10 cycles at {@link Economy#mortgageRate} a cycle. A fixed payment comes out of
 * the monthly income; paying off early has no penalty; a sale pays off the rest first; after
 * {@link Economy#mortgageMissLimit} missed payments in a row the bank takes the home.
 * <p>The loan lives on the {@link Property} (all 0 on older saves and on homes bought with cash).
 */
public final class Mortgage {

    /** Loan lengths you can pick on the agent's screen, in cycles. */
    public static final int[] TERMS = {5, 10};
    public static final int DEFAULT_TERM = 10;

    private Mortgage() {
    }

    private static Economy eco() {
        return TierTable.economy();
    }

    public static boolean has(Property p) {
        return p != null && p.loanLeft > 0.5f;
    }

    /** What you pay up front for a home at this price. */
    public static float down(float price) {
        return price * eco().mortgageDown;
    }

    public static float monthlyRate() {
        return eco().mortgageRate / 12f;
    }

    /** The fixed monthly payment that pays off this loan in this many months. */
    public static float payment(float loan, int months) {
        if (months <= 0) return loan;
        float r = monthlyRate();
        if (r <= 0f) return loan / months;
        return (float) (loan * r / (1.0 - Math.pow(1.0 + r, -months)));
    }

    /** Interest paid over the whole loan when every payment is made on time. */
    public static float totalInterest(float price, int cycles) {
        float loan = price - down(price);
        int months = cycles * 12;
        return payment(loan, months) * months - loan;
    }

    public static int nextTerm(int cycles) {
        for (int i = 0; i < TERMS.length; i++) if (TERMS[i] == cycles) return TERMS[(i + 1) % TERMS.length];
        return DEFAULT_TERM;
    }

    /** Starts the loan on a home just bought for this price. */
    public static void start(Property p, float price, int cycles) {
        float loan = price - down(price);
        int months = cycles * 12;
        p.loanLeft = loan;
        p.loanMonthsLeft = months;
        p.loanPayment = payment(loan, months);
        p.loanMissed = 0;
        p.loanInterest = 0f;
        p.addLog("Mortgage: " + Misc.getDGSCredits(loan) + " borrowed for " + cycles + " cycles, "
                + Misc.getDGSCredits(p.loanPayment) + " a month.");
    }

    public static void clear(Property p) {
        p.loanLeft = 0f;
        p.loanMonthsLeft = 0;
        p.loanPayment = 0f;
        p.loanMissed = 0;
    }

    /** What this month's payment is: the fixed payment, or less for the last one. */
    public static float due(Property p) {
        if (!has(p)) return 0f;
        return Math.min(p.loanPayment, p.loanLeft * (1f + monthlyRate()));
    }

    /** Makes this month's payment (the money itself goes through the income report). Returns what it paid. */
    public static float pay(Property p) {
        float due = due(p);
        if (due <= 0f) return 0f;
        float interest = p.loanLeft * monthlyRate();
        p.loanInterest += interest;
        p.loanLeft = Math.max(0f, p.loanLeft + interest - due);
        p.loanMonthsLeft = Math.max(0, p.loanMonthsLeft - 1);
        p.loanMissed = 0;
        if (p.loanLeft < 1f || p.loanMonthsLeft <= 0) {
            clear(p);
            p.addLog("The mortgage is paid off. The home is all yours.", true, "paid off");
        }
        return due;
    }

    /** A payment was missed. Returns true when the bank takes the home now. */
    public static boolean miss(Property p) {
        // Interest still runs on a missed month.
        p.loanLeft += p.loanLeft * monthlyRate();
        p.loanMissed++;
        return p.loanMissed >= eco().mortgageMissLimit;
    }

    /** "45,000 left, 1,216 a month, 87 months to go" for the home sheet. */
    public static String sheetLine(Property p) {
        if (!has(p)) return null;
        String s = Misc.getDGSCredits(p.loanLeft) + " left, " + Misc.getDGSCredits(p.loanPayment) + " a month, "
                + p.loanMonthsLeft + (p.loanMonthsLeft == 1 ? " month" : " months") + " to go";
        if (p.loanMissed > 0) {
            s += ". Missed " + p.loanMissed + " of " + eco().mortgageMissLimit + (p.loanMissed == 1 ? " payment"
                    : " payments");
        }
        return s;
    }
}
