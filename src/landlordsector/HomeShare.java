package landlordsector;

/**
 * 1.16.0 (LLS-46, from Landlordsector Renewed): what a former wife or husband is owed for their share of the home
 * after a divorce, paid with the next month's report. Saved in {@link Portfolio#homeShares}.
 */
public class HomeShare {
    public String name;
    public float amount;

    public HomeShare() {
    }

    public HomeShare(String name, float amount) {
        this.name = name;
        this.amount = amount;
    }
}
