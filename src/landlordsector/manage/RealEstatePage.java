package landlordsector.manage;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.util.Misc;

import landlordsector.Actions;
import landlordsector.Finders;
import landlordsector.MarketRules;
import landlordsector.Portfolio;
import landlordsector.Property;
import landlordsector.PropertyStats;
import landlordsector.RentSetting;
import landlordsector.Tenant;
import landlordsector.TenantRules;
import landlordsector.Tenants;
import landlordsector.data.FinderDefs;
import landlordsector.data.TenantTraits;
import landlordsector.ui.PropertyView;
import oddiszcommons.manage.ManagePage;

/** The "Real estate" tab of the OddiszCommons Manage screen: homes, the tenant pool and finders. */
public class RealEstatePage implements ManagePage {

    private enum Mode { HOMES, POOL, FINDERS }

    private enum Sub { DETAILS, TENANT, LOG }

    /** Kept while the game runs, so reopening the tab shows the same place. */
    private Mode mode = Mode.HOMES;
    private Sub sub = Sub.DETAILS;
    private String selectedHome;
    private String selectedPool;
    private String selectedFinder;

    @Override
    public CustomPanelAPI createPanel(float width, float height) {
        Portfolio.get().news = false;
        View view = new View(width, height);
        CustomPanelAPI panel = Global.getSettings().createCustom(width, height, view);
        view.attach(panel);
        return panel;
    }

    @Override
    public boolean hasNews() {
        return Global.getSector() != null && Portfolio.get().news;
    }

    final class View extends PropertyView {

        private static final float LIST_W = 260f;
        private static final float ROW_H = 50f;
        private static final float TOP = 96f;

        private int page;
        private String pendingSell;
        private String message = "";
        private Color messageColor = Misc.getTextColor();

        View(float width, float height) {
            super(width, height);
        }

        @Override
        protected void build() {
            Portfolio portfolio = Portfolio.get();
            List<Property> all = portfolio.properties();
            float worth = 0f;
            for (Property pr : all) worth += pr.value();

            title("Real estate", 10f, 8f, 300f);
            float bx = width - 8f - 3 * 160f;
            modeButton(Mode.HOMES, "Homes (" + all.size() + ")", bx);
            modeButton(Mode.POOL, "Tenant pool (" + portfolio.tenantPool().size() + ")", bx + 160f);
            modeButton(Mode.FINDERS, "Finders (" + Finders.hired().size() + ")", bx + 320f);
            text(all.size() + (all.size() == 1 ? " property" : " properties") + " worth "
                    + Misc.getDGSCredits(worth) + ". Credits: " + Misc.getDGSCredits(Actions.credits()) + ".",
                    10f, 38f, width - 20f);
            text("Last month: rent " + Misc.getDGSCredits(portfolio.lastRent) + ", home sales on your colonies "
                    + Misc.getDGSCredits(portfolio.lastSales) + ", property tax "
                    + Misc.getDGSCredits(portfolio.lastTax) + ".", Misc.getGrayColor(), 10f, 56f, width - 20f);
            text(message, messageColor, 10f, 74f, width - 20f);

            switch (mode) {
                case POOL:
                    buildPool();
                    break;
                case FINDERS:
                    buildFinders();
                    break;
                default:
                    buildHomes(all);
            }
        }

        private void modeButton(Mode m, String caption, float x) {
            Btn b = button("mode:" + m.name(), caption, x, 8f, 152f, 26f, true);
            b.selected = mode == m;
        }

        private float rightX() {
            return 8f + LIST_W + 16f;
        }

        private float rightW() {
            return width - rightX() - 8f;
        }

        /** A list on the left, paged. Returns the first and last index shown. */
        private int[] pageRange(int count) {
            int perPage = Math.max(1, (int) ((height - TOP - 44f) / (ROW_H + 4f)));
            int pages = Math.max(1, (count + perPage - 1) / perPage);
            page = Math.max(0, Math.min(page, pages - 1));
            if (pages > 1) {
                float fy = height - 36f;
                button("prev", "<", 8f, fy, 40f, 28f, page > 0);
                label("Page " + (page + 1) + " of " + pages, Misc.getTextColor(), 52f, fy, LIST_W - 88f, 28f,
                        Alignment.MID, Fonts.DEFAULT_SMALL);
                button("next", ">", 8f + LIST_W - 40f, fy, 40f, 28f, page < pages - 1);
            }
            return new int[] {page * perPage, Math.min(count, (page + 1) * perPage)};
        }

        private float rowY(int i, int first) {
            return TOP + (i - first) * (ROW_H + 4f);
        }

        // ---- Homes ----

        private void buildHomes(List<Property> all) {
            if (all.isEmpty()) {
                text("You own no homes yet. Property agents sell them: look for one in a market's comm"
                        + " directory.", 10f, TOP + 10f, width - 20f);
                return;
            }
            Property chosen = findHome(selectedHome);
            if (chosen == null) chosen = all.get(0);
            selectedHome = chosen.id;

            int[] range = pageRange(all.size());
            for (int i = range[0]; i < range[1]; i++) {
                Property pr = all.get(i);
                float y = rowY(i, range[0]);
                area("home:" + pr.id, 8f, y, LIST_W, ROW_H, pr == chosen);
                text(pr.displayName(), Misc.getHighlightColor(), 18f, y + 5f, LIST_W - 20f);
                text(homeStatus(pr), homeStatusColor(pr), 18f, y + 25f, LIST_W - 20f);
            }

            float rx = rightX();
            Btn d = button("sub:DETAILS", "Details", rx, TOP, 110f, 24f, true);
            d.selected = sub == Sub.DETAILS;
            Btn t = button("sub:TENANT", chosen.tenant != null ? "Tenant" : "Tenant (" + chosen.applicants().size()
                    + ")", rx + 116f, TOP, 110f, 24f, true);
            t.selected = sub == Sub.TENANT;
            Btn l = button("sub:LOG", "Log", rx + 232f, TOP, 110f, 24f, true);
            l.selected = sub == Sub.LOG;

            float y0 = TOP + 34f;
            switch (sub) {
                case TENANT:
                    buildTenantTab(chosen, rx, y0);
                    break;
                case LOG:
                    buildLog(chosen, rx, y0);
                    break;
                default:
                    buildDetails(chosen, rx, y0);
            }
        }

        private String homeStatus(Property pr) {
            MarketAPI m = pr.market();
            if (pr.condemned) return "Condemned";
            if (m != null && MarketRules.hostile(m) && pr.isRented()) return "Rent frozen";
            if (pr.tenant != null) return Misc.getDGSCredits(pr.expectedRent()) + " a month, " + pr.tenant.name;
            int a = pr.applicants().size();
            if (a > 0) return "Vacant, " + a + (a == 1 ? " applicant" : " applicants");
            return pr.advertising ? "Vacant, advertising" : "Vacant";
        }

        private Color homeStatusColor(Property pr) {
            if (pr.condemned) return Misc.getNegativeHighlightColor();
            if (pr.tenant == null) return Misc.getHighlightColor();
            return Misc.getGrayColor();
        }

        private void buildDetails(Property chosen, float rx, float y0) {
            MarketAPI m = chosen.market();
            PropertyStats st = chosen.stats();
            boolean own = m != null && MarketRules.isOwn(m);
            float rw = rightW();
            Sheet s = new Sheet();
            s.title = chosen.tier().name;
            s.subtitle = m != null ? where(m, st) : chosen.marketName + " (gone)";
            s.art = chosen.artPath;
            s.valueLine = Misc.getDGSCredits(chosen.value());
            float next = chosen.expectedRent();
            s.rentLine = next > 0f ? Misc.getDGSCredits(next) + " a month" + (own ? "" : ", after tariff") : "none";
            s.condition = chosen.condition;
            if (chosen.condemned) {
                s.occupancy = "Condemned";
                s.occupancyColor = Misc.getNegativeHighlightColor();
            } else if (chosen.tenant == null) {
                s.occupancy = chosen.advertising ? "Vacant, advertising" : "Vacant";
                s.occupancyColor = Misc.getHighlightColor();
            } else if (m != null && MarketRules.hostile(m)) {
                s.occupancy = "Rent frozen: the owners are hostile";
                s.occupancyColor = Misc.getNegativeHighlightColor();
            } else {
                s.occupancy = "Rented to " + chosen.tenant.name;
            }
            s.stats = st;
            s.traits.addAll(houseTraits(chosen.traits()));
            s.extra.add(new String[] {"Bought for", Misc.getDGSCredits(chosen.paidPrice)});
            s.extra.add(new String[] {"Rent so far", Misc.getDGSCredits(chosen.totalRent)
                    + " (last month " + Misc.getDGSCredits(chosen.lastRent) + ")"});
            float maxImg = height - y0 - sheetTextHeight(s) - 48f;
            float end = sheet(s, rx, y0, rw, Math.min(380f, maxImg));

            float cy = Math.min(end + 8f, height - 36f);
            RentSetting set = chosen.setting();
            label("Rent", Misc.getGrayColor(), rx, cy, 50f, 28f, Alignment.LMID, Fonts.DEFAULT_SMALL);
            button("lower:" + chosen.id, "<", rx + 50f, cy, 30f, 28f, set != RentSetting.CHARITY);
            label(set.label, Misc.getHighlightColor(), rx + 84f, cy, 90f, 28f, Alignment.MID, Fonts.DEFAULT_SMALL);
            button("higher:" + chosen.id, ">", rx + 178f, cy, 30f, 28f, set != RentSetting.GREEDY);
            float cost = chosen.repairCost();
            button("repair:" + chosen.id, cost > 0f ? "Repair for " + Misc.getDGSCredits(cost) : "No repairs needed",
                    rx + 230f, cy, 220f, 28f, cost > 0f && Actions.credits() >= cost && m != null);
            boolean confirm = chosen.id.equals(pendingSell);
            button("sell:" + chosen.id, (confirm ? "Confirm: sell for " : "Sell for ")
                    + Misc.getDGSCredits(chosen.saleValue()), rx + 462f, cy, 240f, 28f,
                    Actions.cannotSell(chosen) == null);
        }

        private void buildTenantTab(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            if (p.tenant != null) {
                float ny = tenantCard(p.tenant, p, rx, y0, cw);
                float months = Global.getSector().getClock().getElapsedDaysSince(p.tenant.since) / 30f;
                text("Living here " + Math.max(0, Math.round(months)) + (Math.round(months) == 1 ? " month." : " months."),
                        Misc.getGrayColor(), rx, ny + 4f, cw);
                button("evict:" + p.id, "Evict " + p.tenant.name, rx, ny + 30f, 240f, 28f, true);
                return;
            }
            if (p.condemned) {
                text("Condemned. Repair it before anyone moves in.", Misc.getNegativeHighlightColor(), rx, y0, cw);
                return;
            }
            float normal = Actions.normalRent(p);
            Btn adv = button("advertise:" + p.id, p.advertising ? "Advertising: on" : "Advertising: off", rx, y0, 200f,
                    28f, true);
            adv.selected = p.advertising;
            Btn scr = button("screening:" + p.id, (p.screeningService ? "Screening service: on, " : "Screening service: off, ")
                    + Misc.getDGSCredits(TenantRules.screeningService(normal)) + " a month", rx + 210f, y0, 360f, 28f,
                    true);
            scr.selected = p.screeningService;
            int pool = Portfolio.get().tenantPool().size();
            text(pool > 0 ? pool + (pool == 1 ? " tenant waits" : " tenants wait") + " in your tenant pool." : "",
                    Misc.getGrayColor(), rx, y0 + 36f, cw);

            float y = y0 + 62f;
            if (p.applicants().isEmpty()) {
                text(p.advertising ? "No applicants yet." : "Not advertising.", Misc.getGrayColor(), rx, y, cw);
                return;
            }
            float fee = TenantRules.screeningFee(normal);
            for (Tenant a : p.applicants()) {
                applicantRow(p, a, rx, y, cw, fee);
                y += 72f;
            }
        }

        private void applicantRow(Property p, Tenant a, float x, float y, float w, float fee) {
            box(x, y, w, 66f);
            portrait(a.portrait, x + 6f, y + 6f, 54f);
            float tx = x + 68f;
            text(a.name + ", from " + Tenants.factionName(a.factionId) + ", wealth " + a.wealth,
                    Misc.getHighlightColor(), tx, y + 4f, w - 80f);
            List<Object[]> traits = new ArrayList<>();
            for (int i = 0; i < a.traits().size(); i++) {
                TenantTraits.Def d = TenantTraits.get(a.traits().get(i));
                if (d == null) continue;
                traits.add(a.isShown(i) ? new Object[] {d.name, d.good} : new Object[] {"?", null});
            }
            coloredRow(traits, tx, y + 22f, w - 80f - 330f);
            float h = PropertyStats.withTenant(p, a).happiness;
            text("Happiness there " + Math.round(h), traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)),
                    tx, y + 42f, 220f);
            float bx = x + w - 330f;
            button("screen:" + p.id + ":" + a.id, a.screened ? "Screened" : "Screen for " + Misc.getDGSCredits(fee),
                    bx, y + 18f, 150f, 28f, !a.screened && Actions.credits() >= fee);
            button("accept:" + p.id + ":" + a.id, "Accept", bx + 156f, y + 18f, 84f, 28f, true);
            button("reject:" + p.id + ":" + a.id, "Turn away", bx + 246f, y + 18f, 84f, 28f, true);
        }

        private void buildLog(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            List<String> log = p.log();
            if (log.isEmpty()) {
                text("Nothing yet.", Misc.getGrayColor(), rx, y0, cw);
                return;
            }
            float y = y0;
            for (int i = log.size() - 1; i >= 0 && y < height - 30f; i--) {
                text(log.get(i), rx, y, cw);
                y += 20f;
            }
        }

        // ---- Tenant pool ----

        private void buildPool() {
            List<Tenant> pool = Portfolio.get().tenantPool();
            if (pool.isEmpty()) {
                text("Nobody waits in your tenant pool. Tenant finders bring tenants here.", 10f, TOP + 10f,
                        width - 20f);
                return;
            }
            Tenant chosen = findPool(selectedPool);
            if (chosen == null) chosen = pool.get(0);
            selectedPool = chosen.id;
            int[] range = pageRange(pool.size());
            for (int i = range[0]; i < range[1]; i++) {
                Tenant t = pool.get(i);
                float y = rowY(i, range[0]);
                area("pool:" + t.id, 8f, y, LIST_W, ROW_H, t == chosen);
                text(t.name, Misc.getHighlightColor(), 18f, y + 5f, LIST_W - 20f);
                float days = TenantRules.POOL_DAYS - Global.getSector().getClock().getElapsedDaysSince(t.since);
                text("From " + Tenants.factionName(t.factionId) + ", waits " + Math.max(1, Math.round(days)) + " days",
                        Misc.getGrayColor(), 18f, y + 25f, LIST_W - 20f);
            }
            float rx = rightX();
            float cw = contentWidth(rightW());
            float y = tenantCard(chosen, null, rx, TOP, cw);
            button("dismiss:" + chosen.id, "Send away", rx + cw - 150f, TOP, 150f, 28f, true);
            text("Move into", Misc.getGrayColor(), rx, y + 4f, cw);
            y += 28f;
            List<Property> empty = new ArrayList<>();
            for (Property p : Portfolio.get().properties()) {
                if (p.tenant == null && !p.condemned && p.market() != null) empty.add(p);
            }
            if (empty.isEmpty()) {
                text("You have no empty homes.", Misc.getGrayColor(), rx, y, cw);
                return;
            }
            final Tenant who = chosen;
            empty.sort((a, b) -> Float.compare(PropertyStats.withTenant(b, who).happiness,
                    PropertyStats.withTenant(a, who).happiness));
            for (Property p : empty) {
                if (y > height - 40f) break;
                float h = PropertyStats.withTenant(p, chosen).happiness;
                text(p.displayName(), rx, y + 5f, cw - 330f);
                text("Happiness " + Math.round(h), traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)),
                        rx + cw - 320f, y + 5f, 150f);
                button("movein:" + p.id + ":" + chosen.id, "Move in", rx + cw - 150f, y, 150f, 28f, true);
                y += 34f;
            }
        }

        // ---- Finders ----

        private void buildFinders() {
            List<Finders.State> hired = Finders.hired();
            if (hired.isEmpty()) {
                text("You have hired no tenant finders. Each one works out of a comm directory somewhere in the"
                        + " Sector.", 10f, TOP + 10f, width - 20f);
                return;
            }
            Finders.State chosen = null;
            for (Finders.State s : hired) if (s.id.equals(selectedFinder)) chosen = s;
            if (chosen == null) chosen = hired.get(0);
            selectedFinder = chosen.id;
            int[] range = pageRange(hired.size());
            for (int i = range[0]; i < range[1]; i++) {
                Finders.State s = hired.get(i);
                FinderDefs.Def d = s.def();
                float y = rowY(i, range[0]);
                area("finder:" + s.id, 8f, y, LIST_W, ROW_H, s == chosen);
                text(d.name(), Misc.getHighlightColor(), 18f, y + 5f, LIST_W - 20f);
                text(s.searching ? "Searching, " + Math.max(1, Math.round(s.daysLeft())) + " days left" : "Ready",
                        s.searching ? Misc.getGrayColor() : Misc.getPositiveHighlightColor(), 18f, y + 25f, LIST_W - 20f);
            }
            FinderDefs.Def d = chosen.def();
            float rx = rightX();
            float cw = contentWidth(rightW());
            portrait(chosen.person != null ? chosen.person.getPortraitSprite() : null, rx, TOP, 86f);
            title(d.name(), rx + 98f, TOP, cw - 98f);
            text(d.pitch, rx + 98f, TOP + 28f, cw - 98f);
            text("Brings " + d.tenants + " screened tenants from " + Tenants.factionName(d.profile) + " in "
                    + Math.round(d.days) + " days.", Misc.getGrayColor(), rx + 98f, TOP + 48f, cw - 98f);
            float y = TOP + 100f;
            if (chosen.searching) {
                text("Searching. Back in " + Math.max(1, Math.round(chosen.daysLeft())) + " days.", rx, y, cw);
            } else {
                button("search:" + chosen.id, "Start a search for " + Misc.getDGSCredits(d.search), rx, y, 300f, 28f,
                        Actions.credits() >= d.search);
            }
        }

        // ---- Actions ----

        @Override
        protected void onAction(Object action) {
            String a = String.valueOf(action);
            String[] parts = a.split(":");
            String verb = parts[0];
            String keepPending = null;
            switch (verb) {
                case "mode":
                    mode = Mode.valueOf(parts[1]);
                    page = 0;
                    message = "";
                    break;
                case "sub":
                    sub = Sub.valueOf(parts[1]);
                    message = "";
                    break;
                case "prev":
                    page--;
                    break;
                case "next":
                    page++;
                    break;
                case "home":
                    selectedHome = parts[1];
                    message = "";
                    break;
                case "pool":
                    selectedPool = parts[1];
                    message = "";
                    break;
                case "finder":
                    selectedFinder = parts[1];
                    message = "";
                    break;
                case "search": {
                    String no = Finders.startSearch(Finders.byFlag(parts[1]));
                    if (no == null) good("The search has started.");
                    else bad(no);
                    break;
                }
                case "dismiss": {
                    Tenant t = findPool(parts[1]);
                    if (t != null) {
                        Portfolio.get().tenantPool().remove(t);
                        good("Sent " + t.name + " away.");
                    }
                    selectedPool = null;
                    break;
                }
                case "movein": {
                    Property p = findHome(parts[1]);
                    Tenant t = findPool(parts[2]);
                    if (p != null && t != null) result(Actions.moveIn(p, t));
                    selectedPool = null;
                    break;
                }
                default:
                    keepPending = homeAction(verb, parts);
            }
            pendingSell = keepPending;
            rebuild();
        }

        /** Actions on one home. Returns the home id when a sale waits for its second click. */
        private String homeAction(String verb, String[] parts) {
            if (parts.length < 2) return null;
            Property pr = findHome(parts[1]);
            if (pr == null) return null;
            Tenant applicant = parts.length > 2 ? findApplicant(pr, parts[2]) : null;
            switch (verb) {
                case "lower":
                    Actions.setRent(pr, pr.setting().lower());
                    message = "";
                    break;
                case "higher":
                    Actions.setRent(pr, pr.setting().higher());
                    message = "";
                    break;
                case "repair":
                    result(Actions.repair(pr));
                    break;
                case "sell":
                    if (pr.id.equals(pendingSell)) {
                        result(Actions.sell(pr));
                        selectedHome = null;
                    } else {
                        message = "Click again to sell " + pr.displayName() + ". The price takes off a broker fee"
                                + " and the local tariff.";
                        messageColor = Misc.getHighlightColor();
                        return pr.id;
                    }
                    break;
                case "advertise":
                    Actions.toggleAdvertising(pr);
                    message = "";
                    break;
                case "screening":
                    Actions.toggleScreening(pr);
                    message = "";
                    break;
                case "screen":
                    if (applicant != null) result(Actions.screen(pr, applicant));
                    break;
                case "accept":
                    if (applicant != null) result(Actions.moveIn(pr, applicant));
                    break;
                case "reject":
                    if (applicant != null) result(Actions.reject(pr, applicant));
                    break;
                case "evict":
                    result(Actions.evict(pr));
                    break;
                default:
                    break;
            }
            return null;
        }

        private void result(String text) {
            boolean ok = text.startsWith("Sold") || text.startsWith("Repaired") || text.startsWith("Rebuilt")
                    || text.startsWith("Screened") || text.contains(" moved into ") || text.startsWith("Turned")
                    || text.startsWith("Evicted");
            if (ok) good(text);
            else bad(text);
        }

        private void good(String text) {
            message = text;
            messageColor = Misc.getPositiveHighlightColor();
        }

        private void bad(String text) {
            message = text;
            messageColor = Misc.getNegativeHighlightColor();
        }
    }

    private static Property findHome(String id) {
        if (id == null) return null;
        for (Property pr : Portfolio.get().properties()) if (pr.id.equals(id)) return pr;
        return null;
    }

    private static Tenant findPool(String id) {
        if (id == null) return null;
        for (Tenant t : Portfolio.get().tenantPool()) if (t.id.equals(id)) return t;
        return null;
    }

    private static Tenant findApplicant(Property p, String id) {
        for (Tenant t : p.applicants()) if (t.id.equals(id)) return t;
        return null;
    }
}
