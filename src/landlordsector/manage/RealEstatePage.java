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

    private enum Sub { DETAILS, TENANT, FURNITURE, UPGRADES, SERVICES, LOG }

    /** Kept while the game runs, so reopening the tab shows the same place. */
    private Mode mode = Mode.HOMES;
    private Sub sub = Sub.DETAILS;
    private String selectedHome;
    private String selectedPool;
    private String selectedFinder;
    /** 1.2.0: the "Move in with" list is open. */
    private boolean choosingPartner;

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
            modeButton(Mode.HOMES, "Homes: " + all.size(), "H", bx);
            modeButton(Mode.POOL, "Tenant pool: " + portfolio.tenantPool().size(), "P", bx + 160f);
            modeButton(Mode.FINDERS, "Headhunters: " + Finders.hired().size(), "U", bx + 320f);
            text(all.size() + (all.size() == 1 ? " property" : " properties") + " worth "
                    + Misc.getDGSCredits(worth) + ". Credits: " + Misc.getDGSCredits(Actions.credits()) + ".",
                    10f, 38f, width - 20f);
            String family = (portfolio.lastKidPay > 0f ? ", your kids sent home " + Misc.getDGSCredits(portfolio.lastKidPay)
                    : "") + (portfolio.lastKidCost > 0f ? ", raising your kids " + Misc.getDGSCredits(portfolio.lastKidCost)
                    : "") + (portfolio.lastAlimony > 0f ? ", alimony " + Misc.getDGSCredits(portfolio.lastAlimony) : "");
            text("Last month: rent " + Misc.getDGSCredits(portfolio.lastRent) + ", home sales on your colonies "
                    + Misc.getDGSCredits(portfolio.lastSales) + ", property tax "
                    + Misc.getDGSCredits(portfolio.lastTax) + family + ".", Misc.getGrayColor(), 10f, 56f, width - 20f);
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

        private void modeButton(Mode m, String caption, String key, float x) {
            Btn b = hotkeyButton("mode:" + m.name(), caption, key, x, 8f, 152f, 26f);
            b.selected = mode == m;
        }

        /** H, P, U switch Homes, Tenant pool, Headhunters; D, T, F, G, L the home's Details, Tenants, Furniture, Upgrades, Log. */
        @Override
        protected boolean onKey(int key) {
            String a = null;
            switch (key) {
                case org.lwjgl.input.Keyboard.KEY_H: a = "mode:HOMES"; break;
                case org.lwjgl.input.Keyboard.KEY_P: a = "mode:POOL"; break;
                case org.lwjgl.input.Keyboard.KEY_U: a = "mode:FINDERS"; break;
                case org.lwjgl.input.Keyboard.KEY_D: if (mode == Mode.HOMES) a = "sub:DETAILS"; break;
                case org.lwjgl.input.Keyboard.KEY_T: if (mode == Mode.HOMES) a = "sub:TENANT"; break;
                case org.lwjgl.input.Keyboard.KEY_F: if (mode == Mode.HOMES) a = "sub:FURNITURE"; break;
                case org.lwjgl.input.Keyboard.KEY_G: if (mode == Mode.HOMES) a = "sub:UPGRADES"; break;
                case org.lwjgl.input.Keyboard.KEY_S: if (mode == Mode.HOMES) a = "sub:SERVICES"; break;
                case org.lwjgl.input.Keyboard.KEY_L: if (mode == Mode.HOMES && !sideLog()) a = "sub:LOG"; break;
                default: break;
            }
            if (a == null) return false;
            onAction(a);
            return true;
        }

        /** 1.5.3: the icon row stops before the event log when the log sits right of the sheet. */
        @Override
        protected float iconRowWidth(float w, float cw) {
            return sideLog() ? cw : w;
        }

        private boolean sideLog() {
            return sideLogX() + 200f <= width - 8f;
        }

        /** Where the event log starts: right of the sheet. */
        private float sideLogX() {
            return rightX() + contentWidth(rightW()) + 28f;
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
                // Rented homes grey, vacant ones orange: the ones that need you stand out (0.3.8).
                text(pr.displayName(), pr.playerHome ? Misc.getTextColor() : (pr.tenant != null && !pr.condemned
                        ? Misc.getGrayColor() : Misc.getHighlightColor()), 18f, y + 5f, LIST_W - 20f);
                text(homeStatus(pr), homeStatusColor(pr), 18f, y + 25f, LIST_W - 20f);
            }

            float rx = rightX();
            boolean sideLog = sideLog();
            Btn d = hotkeyButton("sub:DETAILS", "Details", "D", rx, TOP, 124f, 24f);
            d.selected = sub == Sub.DETAILS;
            Btn t = hotkeyButton("sub:TENANT", chosen.playerHome ? "Family" : (chosen.tenant != null ? "Tenants"
                    : "Tenants: " + chosen.applicants().size()), "T", rx + 128f, TOP, 124f, 24f);
            t.selected = sub == Sub.TENANT;
            Btn f = hotkeyButton("sub:FURNITURE", "Furniture", "F", rx + 256f, TOP, 124f, 24f);
            f.selected = sub == Sub.FURNITURE;
            Btn g = hotkeyButton("sub:UPGRADES", "Upgrades", "G", rx + 384f, TOP, 124f, 24f);
            g.selected = sub == Sub.UPGRADES;
            Btn sv = hotkeyButton("sub:SERVICES", "Services", "S", rx + 512f, TOP, 124f, 24f);
            sv.selected = sub == Sub.SERVICES;
            if (!sideLog) {
                Btn l = hotkeyButton("sub:LOG", "Log", "L", rx + 640f, TOP, 124f, 24f);
                l.selected = sub == Sub.LOG;
            } else if (sub == Sub.LOG) {
                sub = Sub.DETAILS;
            }

            float y0 = TOP + 34f;
            switch (sub) {
                case TENANT:
                    buildTenantTab(chosen, rx, y0);
                    break;
                case FURNITURE:
                    buildFurnitureTab(chosen, rx, y0);
                    break;
                case UPGRADES:
                    buildUpgradesTab(chosen, rx, y0);
                    break;
                case SERVICES:
                    buildServicesTab(chosen, rx, y0);
                    break;
                case LOG:
                    buildLog(chosen, rx, y0, contentWidth(rightW()));
                    break;
                default:
                    buildDetails(chosen, rx, y0);
            }
            if (sideLog) {
                float lx = sideLogX();
                title("Events", lx, TOP, width - 8f - lx);
                buildLog(chosen, lx, TOP + 34f, width - 8f - lx);
            }
        }

        private String homeStatus(Property pr) {
            MarketAPI m = pr.market();
            if (pr.condemned) return "Condemned";
            if (pr.playerHome) {
                landlordsector.Tenant t = pr.partner();
                return t != null ? "Your home, with " + t.firstName() + " (" + landlordsector.Family.noun(t) + ")"
                        : "Your home";
            }
            if (m != null && MarketRules.hostile(m) && pr.isRented()) return "Rent frozen";
            if (pr.tenant != null) return Misc.getDGSCredits(pr.expectedRent()) + " a month, " + pr.tenant.name;
            int a = pr.applicants().size();
            if (a > 0) return "Vacant, " + a + (a == 1 ? " applicant" : " applicants");
            return pr.advertising ? "Vacant, advertising" : "Vacant";
        }

        private Color homeStatusColor(Property pr) {
            if (pr.condemned) return Misc.getNegativeHighlightColor();
            if (pr.playerHome) return Misc.getPositiveHighlightColor();
            if (pr.tenant == null) return Misc.getHighlightColor();
            return Misc.getGrayColor();
        }

        private void buildDetails(Property chosen, float rx, float y0) {
            MarketAPI m = chosen.market();
            PropertyStats st = chosen.stats();
            float rw = rightW();
            Sheet s = new Sheet();
            s.title = chosen.name != null ? chosen.name : chosen.tier().name;
            // Andrzej, 0.5.0: the place at the top, not under the picture.
            text(chosen.tier().name + " on " + (m != null ? where(m) : chosen.marketName + " (gone)"),
                    Misc.getGrayColor(), rx, y0, contentWidth(rw));
            y0 += 22f;
            s.art = chosen.artPath;
            s.valueLine = Misc.getDGSCredits(chosen.value());
            s.valueLines = PropertyStats.valueLines(chosen.tier(), chosen.basePrice, st.grade, st.marketMult,
                    chosen.drift, chosen.traitPrice(), chosen.decor(), chosen.condition, chosen.condemned,
                    chosen.value());
            float next = chosen.expectedRent();
            s.rentLine = next > 0f ? Misc.getDGSCredits(next) + " a month" : "none";
            s.condition = chosen.condition;
            if (chosen.condemned) {
                s.occupancy = "Condemned";
                s.occupancyColor = Misc.getNegativeHighlightColor();
            } else if (chosen.playerHome) {
                landlordsector.Tenant pt = chosen.partner();
                s.occupancy = pt != null ? "You live here with " + pt.name + ", your " + landlordsector.Family.noun(pt)
                        : "You live here";
                s.occupancyColor = Misc.getPositiveHighlightColor();
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
            s.traitIds.addAll(chosen.traits());
            s.tierId = chosen.tier().id;
            if (chosen.market() != null) s.areaPicture = areaPicture(chosen.market(), chosen.neighbor);
            s.upgrades.addAll(upgradeItems(chosen));
            s.upgradeIds.addAll(chosen.renovationIds());
            s.furniture.addAll(furnitureItems(chosen));
            s.size = chosen.tier().name;
            s.furnitureSlots = chosen.tier().slots;
            s.tenant = chosen.tenant == null ? "" : (chosen.tenant.isPartner() ? chosen.tenant.name + ", your "
                    + landlordsector.Family.noun(chosen.tenant) : nameAndFaction(chosen.tenant));
            s.tenantRef = chosen.tenant;
            s.extra.add(new String[] {"Bought for", Misc.getDGSCredits(chosen.paidPrice)});
            s.extra.add(new String[] {"Rent so far", Misc.getDGSCredits(chosen.totalRent)
                    + " (last month " + Misc.getDGSCredits(chosen.lastRent) + ")"});
            float maxImg = height - y0 - sheetTextHeight(s, rw) - 48f;
            float end = sheet(s, rx, y0, rw, maxImg);

            float cy = Math.min(end + 8f, height - 36f);
            // 1.12.0: the arrows set the rent you ask; a running lease keeps its own rent until it ends.
            RentSetting set = chosen.askingRent();
            if (!chosen.playerHome) {
                label("Rent", Misc.getGrayColor(), rx, cy, 50f, 28f, Alignment.LMID, Fonts.DEFAULT_SMALL);
                button("lower:" + chosen.id, "<", rx + 50f, cy, 30f, 28f, set != RentSetting.FREE);
                label(set.label, Misc.getHighlightColor(), rx + 84f, cy, 90f, 28f, Alignment.MID, Fonts.DEFAULT_SMALL);
                button("higher:" + chosen.id, ">", rx + 178f, cy, 30f, 28f, set != RentSetting.GREEDY);
            }
            float cost = chosen.repairCost();
            button("repair:" + chosen.id, cost > 0f ? "Repair for " + Misc.getDGSCredits(cost) : "No repairs needed",
                    rx + 220f, cy, 200f, 28f, cost > 0f && Actions.credits() >= cost && m != null);
            if (chosen.hasMarks()) {
                tip(rx + 220f, cy, 200f, 28f, "Repair", "Brings the home back to 100% and cleans off graffiti and dirt.");
            }
            boolean confirm = chosen.id.equals(pendingSell);
            button("sell:" + chosen.id, (confirm ? "Confirm: sell for " : "Sell for ")
                    + Misc.getDGSCredits(chosen.saleValue(false)), rx + 430f, cy, 220f, 28f,
                    Actions.cannotSell(chosen) == null);
            String noSale = Actions.cannotSell(chosen);
            boolean furnished = chosen.furnitureCount() > 0;
            tip(rx + 430f, cy, 220f, 28f, "Selling", "You can sell a home in any condition, rented or not. You get"
                    + " its value minus a " + Math.round(landlordsector.TierTable.economy().brokerFee * 100f)
                    + "% broker fee." + (furnished ? " The furniture goes back into your"
                    + " furniture storage." : ""), noSale);
            // Only as wide as the sheet, so it never runs into the event log.
            float fw = Math.min(230f, contentWidth(rw) - 660f);
            if (furnished && fw >= 150f) {
                boolean confirmF = ("furnished:" + chosen.id).equals(pendingSell);
                button("sellfurnished:" + chosen.id, (confirmF ? "Confirm: " : "Sell furnished: ")
                        + Misc.getDGSCredits(chosen.saleValue(true)), rx + 660f, cy, fw, 28f,
                        Actions.cannotSell(chosen) == null);
                tip(rx + 660f, cy, fw, 28f, "Selling furnished", "The furniture stays in the home and the buyer pays"
                        + " its full price on top: " + Misc.getDGSCredits(chosen.furnitureValue()) + ".", noSale);
            }
            if (!chosen.playerHome) {
                String leaseNote = landlordsector.Leases.has(chosen.tenant) && chosen.tenant.leaseRent != null
                        && chosen.tenant.leaseRent != set ? chosen.tenant.firstName() + " pays "
                        + chosen.tenant.leaseRent.label + " rent until the lease ends." : null;
                tip(rx, cy, 210f, 28f, "Rent", "Free: no rent at all. Anyone can afford it, tenants are grateful, and rich"
                        + " ones take a smaller home than usual. Charity and Low: less rent, happier tenants, more reputation."
                        + " High and Greedy: more rent, unhappier tenants, lost reputation.", "A lower rent counts at once."
                        + " During a lease, a higher rent starts with the next lease.", leaseNote);
            }
        }

        private void buildTenantTab(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            if (p.playerHome) {
                buildFamilyTab(p, rx, y0, cw);
                return;
            }
            if (p.tenant != null) {
                float ny = tenantCard(p.tenant, p, rx, y0, cw);
                float months = Global.getSector().getClock().getElapsedDaysSince(p.tenant.since) / 30f;
                text("Living here " + Math.max(0, Math.round(months)) + (Math.round(months) == 1 ? " month. " : " months. ")
                        + landlordsector.Leases.cardLine(p.tenant), Misc.getGrayColor(), rx, ny + 4f, cw);
                if (isTyping("nick:" + p.id)) {
                    nicknameBox(rx, ny + 30f, cw);
                    return;
                }
                float fee = landlordsector.Leases.evictFee(p);
                button("evict:" + p.id, fee > 0f ? "Evict, pay " + Misc.getDGSCredits(fee) : "Evict " + p.tenant.name,
                        rx, ny + 30f, 240f, 28f, fee <= 0f || Actions.credits() >= fee);
                tip(rx, ny + 30f, 240f, 28f, "Evict", fee > 0f ? "The lease still runs " + landlordsector.Leases.monthsLeft(
                        p.tenant) + " months. Breaking it costs you half their rent for the months left, at least one"
                        + " month." : (landlordsector.Leases.breached(p.tenant) ? p.tenant.firstName() + " broke the"
                        + " lease lately, so evicting is free." : "Free: there is no lease to break."));
                declareButton(p, rx + 250f, ny + 30f);
                nicknameButton(p, p.tenant, rx + 460f, ny + 30f);
                leaseButton(p, rx, ny + 64f, "Next lease: ");
                questionBox(p, rx, ny + 104f, cw);
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
            landlordsector.data.Services.Def scrDef = landlordsector.data.Services.get(
                    landlordsector.data.Services.SCREENING);
            boolean screening = p.hasService(landlordsector.data.Services.SCREENING);
            Btn scr = button("screening:" + p.id, (screening ? "Screening service: on, " : "Screening service: off, ")
                    + Misc.getDGSCredits(scrDef != null ? Actions.servicePrice(p, scrDef) : 0f) + " a month",
                    rx + 210f, y0, 360f, 28f, scrDef != null);
            scr.selected = screening;
            tip(rx, y0, 200f, 28f, "Advertising", "While on, people looking for a home apply to rent it, up to "
                    + TenantRules.MAX_APPLICANTS + " at a time. Each waits " + Math.round(TenantRules.APPLICANT_DAYS)
                    + " days for your answer. They come faster to good locations, comfortable homes and cheap rent."
                    + " Applicants are more likely to have traits that suit this home and less likely to hate it."
                    + " You see one trait of each until you screen them.");
            tip(rx + 210f, y0, 360f, 28f, "Screening service", "A company checks everyone who answers your advert"
                    + " before they reach you, for a tenth of the Normal rent a month. Applicants are then half as"
                    + " likely to have bad traits. It does not show their traits: the Screen button on an applicant"
                    + " does that, for half a month's Normal rent (at least 500).");
            int pool = Portfolio.get().tenantPool().size();
            text(pool > 0 ? pool + (pool == 1 ? " tenant waits" : " tenants wait") + " in your tenant pool." : "",
                    Misc.getGrayColor(), rx, y0 + 36f, cw);
            if (cw >= 790f) declareButton(p, rx + 580f, y0);
            else declareButton(p, rx + cw - 200f, y0 + 32f);
            if (cw >= 790f) leaseButton(p, rx + 580f, y0 + 32f, "Lease: ");

            float y = y0 + 62f;
            title("Applicants", rx, y, cw);
            y += 30f;
            if (p.applicants().isEmpty()) {
                text(p.advertising ? "No applicants yet." : "Not advertising.", Misc.getGrayColor(), rx, y, cw);
                y += 26f;
            }
            float fee = TenantRules.screeningFee(normal);
            for (Tenant a : p.applicants()) {
                applicantRow(p, a, rx, y, cw, fee);
                y += 72f;
            }
            // The tenant pool in its own spot under the applicants (Andrzej, 0.3.8).
            List<Tenant> poolList = Portfolio.get().tenantPool();
            y += 8f;
            title("From your tenant pool", rx, y, cw);
            y += 30f;
            if (poolList.isEmpty()) {
                text("Nobody waits in your tenant pool. Headhunters bring tenants there.", Misc.getGrayColor(), rx, y,
                        cw);
                return;
            }
            for (Tenant a : poolList) {
                if (y + 66f > height - 8f) break;
                poolRow(p, a, rx, y, cw);
                y += 72f;
            }
        }

        /** 1.12.0 (LLS-33): the lease length new tenants sign here. */
        private void leaseButton(Property p, float x, float y, String caption) {
            button("lease:" + p.id, caption + landlordsector.Leases.termName(p.leaseTerm()), x, y, 200f, 28f, true);
            tip(x, y, 200f, 28f, "Lease", "How long new tenants sign for: no lease, 6, 12 or 24 months. During a lease the"
                    + " rent stays as signed (you can still lower it), a tenant who moves out early pays you a month's"
                    + " rent, and evicting costs you half the rent of the months left, unless they broke the lease"
                    + " first. A longer lease makes tenants a little happier. When it ends, you decide whether to"
                    + " renew.");
        }

        /** 1.12.0 (LLS-32): a question from this home, with a button per answer. */
        private void questionBox(Property p, float x, float y, float w) {
            landlordsector.data.TenantEvents.Def d = landlordsector.Choices.question(p);
            if (d == null || y + 60f > height - 8f) return;
            title("Waiting for your answer", x, y, w);
            float yy = y + 30f;
            yy += wrapped(p.decision.text, 0, Misc.getTextColor(), Misc.getTextColor(), x, yy, w) + 4f;
            if (p.decision.said != null) {
                yy += wrapped(p.decision.who + ": \"" + p.decision.said + "\"", 0, Misc.getGrayColor(),
                        Misc.getGrayColor(), x, yy, w) + 4f;
            }
            for (int i = 0; i < d.choices.size(); i++) {
                if (yy + 28f > height - 8f) break;
                landlordsector.data.TenantEvents.Def a = d.choices.get(i);
                String no = landlordsector.Choices.cannot(p, a);
                button("answer:" + p.id + ":" + i, landlordsector.Choices.label(p, a), x, yy, Math.min(w, 420f), 26f,
                        no == null);
                if (no != null) tip(x, yy, Math.min(w, 420f), 26f, "Not now", no);
                yy += 30f;
            }
        }

        /** 1.8.0 (LLS-29): give a tenant a name you will remember. */
        private void nicknameButton(Property p, Tenant t, float x, float y) {
            button("nickname:" + p.id, t.hasNickname() ? "Change nickname" : "Nickname", x, y, 180f, 28f, true);
            tip(x, y, 180f, 28f, "Nickname", t.hasNickname() ? "Real name: " + t.realName + "."
                    + " Leave the nickname empty to use the real name again." : "Call " + t.firstName()
                    + " something you will remember. It shows everywhere instead of the name.");
        }

        private void nicknameBox(float x, float y, float w) {
            box(x, y, Math.min(w, 420f), 28f);
            text(typedText() + "_", Misc.getHighlightColor(), x + 8f, y + 5f, Math.min(w, 420f) - 16f);
            text("Type a nickname. Enter saves it, Escape cancels. Leave it empty for the real name.",
                    Misc.getGrayColor(), x, y + 34f, w);
        }

        @Override
        protected void onTyped(Object key, String text) {
            String k = String.valueOf(key);
            if (!k.startsWith("nick:")) return;
            Property p = findHome(k.substring(5));
            if (p == null || p.tenant == null) return;
            String before = p.tenant.name;
            p.tenant.setNickname(text);
            good(p.tenant.hasNickname() ? before + " is now " + p.tenant.name + "." : "Back to " + p.tenant.name + ".");
        }

        /** 1.2.0: "Declare player home": you move in, the adverts stop. */
        private void declareButton(Property p, float x, float y) {
            String no = landlordsector.Family.cannotDeclare(p);
            button("declare:" + p.id, "Declare player home", x, y, 200f, 28f, no == null);
            if (no != null) tip(x, y, 200f, 28f, "Declare player home", no);
        }

        /**
         * 1.2.0 (Andrzej's family design): the home you live in. Alone: move in with one of your officers or
         * administrators. With a partner: their card. Then your kids.
         */
        private void buildFamilyTab(Property p, float rx, float y0, float cw) {
            landlordsector.Tenant t = p.partner();
            float y = y0;
            if (t == null) {
                text("You live here.", Misc.getGrayColor(), rx, y + 5f, 200f);
                Btn mw = button("choose:" + p.id, "Propose to...", rx + 210f, y, 200f, 28f, !p.condemned);
                mw.selected = choosingPartner;
                String no = landlordsector.Family.cannotLeave(p);
                button("leave:" + p.id, "Move out", rx + 420f, y, 200f, 28f, no == null);
                if (no != null) tip(rx + 420f, y, 200f, 28f, "Move out", no);
                y += 40f;
                if (choosingPartner) y = partnerList(p, rx, y, cw);
            } else {
                y = partnerCard(p, t, rx, y, cw);
                float[] need = landlordsector.Family.childNeeds(p.children().size());
                if (need != null) {
                    boolean ok = landlordsector.Family.cannotTry(p) == null;
                    text("Another child needs happiness " + Math.round(need[0]) + " and comfort " + Math.round(need[1])
                            + ".", traitColor(ok ? null : Boolean.FALSE), rx, y, cw);
                    y += 22f;
                }
            }
            y = kidsList(p, rx, y + 8f, cw);
            int away = Portfolio.get().exKids().size();
            if (away > 0 && y < height - 30f) {
                text(away + (away == 1 ? " child lives" : " children live") + " with a former partner. Alimony: "
                        + Misc.getDGSCredits(away * landlordsector.Settings.alimony()) + " a month.",
                        Misc.getGrayColor(), rx, y + 4f, cw);
            }
        }

        /**
         * Officers and administrators of the opposite sex (and Sophia Anderson with AoTD), with what they need to say
         * yes and a Propose button (Andrzej, 2026-11-16).
         */
        private float partnerList(Property p, float rx, float y, float cw) {
            List<com.fs.starfarer.api.characters.PersonAPI> people = landlordsector.Family.candidates();
            if (people.isEmpty()) {
                text("No officers or administrators of the opposite sex.", Misc.getGrayColor(), rx, y, cw);
                return y + 26f;
            }
            for (com.fs.starfarer.api.characters.PersonAPI who : people) {
                if (y + 60f > height - 8f) break;
                box(rx, y, cw, 56f);
                portrait(who.getPortraitSprite(), rx + 4f, y + 4f, 48f);
                text(who.getNameString(), Misc.getHighlightColor(), rx + 60f, y + 6f, cw - 300f);
                String no = landlordsector.Family.cannotPropose(p, who);
                text(landlordsector.Family.role(who), traitColor(no == null ? Boolean.TRUE : null), rx + 60f, y + 28f,
                        cw - 300f);
                float bx = rx + cw - 8f - 160f;
                button("partner:" + p.id + ":" + who.getId(), "Propose", bx, y + 14f, 160f, 28f, no == null);
                if (no != null) tip(bx, y + 14f, 160f, 28f, "Propose", no);
                else tip(bx, y + 14f, 160f, 28f, "Propose", landlordsector.Family.isSophia(who)
                        ? "Moves in with you and keeps her research post." : "Retires and moves in with you.");
                y += 60f;
            }
            return y;
        }

        /** The partner: portrait, how long together, traits, happiness and health with what makes them up. */
        private float partnerCard(Property p, landlordsector.Tenant t, float x, float y, float w) {
            float size = 86f;
            portrait(t.portrait, x, y, size);
            float tx = x + size + 12f;
            float tw = w - size - 12f;
            title(t.name, tx, y, tw);
            float months = Global.getSector().getClock().getElapsedDaysSince(t.since) / 30f;
            int mo = Math.max(0, Math.round(months));
            text("Your " + landlordsector.Family.noun(t) + ". Together " + mo + (mo == 1 ? " month." : " months."),
                    Misc.getGrayColor(), tx, y + 28f, tw);
            text("Traits", Misc.getGrayColor(), tx, y + 48f, 60f);
            traitRow(tenantTraits(t), tx + 60f, y + 46f, 36f, tw - 60f);
            float ny = y + size + 8f;
            PropertyStats st = p.stats();
            text("Happiness", Misc.getGrayColor(), x, ny, 120f);
            Color happy = st.happiness >= 50f ? Misc.getPositiveHighlightColor()
                    : (st.happiness >= 25f ? Misc.getHighlightColor() : Misc.getNegativeHighlightColor());
            bar(x + 120f, ny + 5f, 110f, 10f, st.happiness / 100f, happy);
            text(String.valueOf(Math.round(st.happiness)), x + 238f, ny, 40f);
            tipColored(x, ny, 280f, 22f, "Happiness: " + Math.round(st.happiness), st.happinessLines);
            ny += 22f;
            text("Health", Misc.getGrayColor(), x, ny, 120f);
            Color healthy = st.health >= landlordsector.data.Levels.sickBelow() ? Misc.getPositiveHighlightColor()
                    : (st.health >= landlordsector.data.Levels.treatmentBelow() ? Misc.getHighlightColor()
                            : Misc.getNegativeHighlightColor());
            bar(x + 120f, ny + 5f, 110f, 10f, st.health / 100f, healthy);
            text(String.valueOf(Math.round(st.health)), x + 238f, ny, 40f);
            tipColored(x, ny, 280f, 22f, "Health: " + Math.round(st.health), st.healthLines);
            ny += 22f;
            for (PropertyStats.Note n : st.notes) {
                com.fs.starfarer.api.ui.LabelAPI nl = text(n.text, traitColor(n.good), x + 12f, ny, w - 12f);
                landlordsector.ui.Canvas.highlight(nl, n.text, n.line());
                ny += 18f;
            }
            if (t.conceivedAt == 0L || !t.toldPregnant) {
                text("Chance of a child each time you try: " + Math.round(landlordsector.Family.childChance(p) * 100f)
                        + "%.", Misc.getGrayColor(), x, ny + 4f, w);
                ny += 22f;
            }
            if (t.conceivedAt != 0L && t.toldPregnant) {
                int days = Math.max(1, Math.round(landlordsector.Family.BIRTH_DAYS
                        - landlordsector.Family.daysSince(t.conceivedAt)));
                text("A baby is on the way: " + days + (days == 1 ? " day left." : " days left."),
                        Misc.getPositiveHighlightColor(), x, ny + 4f, w);
                ny += 22f;
            }
            return ny + 8f;
        }

        /** Your kids living here: name, age, happiness and what they want to be. */
        private float kidsList(Property p, float x, float y, float w) {
            if (p.children().isEmpty()) return y;
            title("Kids", x, y, w);
            y += 30f;
            for (landlordsector.Child c : p.children()) {
                if (y + 82f > height - 8f) break;
                box(x, y, w, 78f);
                portrait(c.portrait, x + 4f, y + 4f, 70f);
                float tx = x + 82f, tw = w - 82f - 240f;
                text(c.name, Misc.getHighlightColor(), tx, y + 6f, tw);
                // 1.8.0 (LLS-28): the stage, the wish (decided from the Teen stage) and what was paid for.
                String what = landlordsector.Family.pathName(c.path);
                String stage = c.grown ? "Grown up" : landlordsector.Kids.stageName(c) + ", "
                        + landlordsector.Family.age(c).toLowerCase();
                text(stage + (what != null ? (c.pathLocked ? ". Will become " : ". Wants to be ") + what + "." : "."),
                        Misc.getGrayColor(), tx, y + 28f, tw);
                text(training(c), Misc.getGrayColor(), tx, y + 50f, tw);
                float h = c.happiness;
                text("Happiness " + Math.round(h), traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)),
                        x + w - 230f, y + 6f, 110f);
                // 1.9.0: the kid's own health, with what makes it up.
                if (!c.grown) {
                    PropertyStats ks = landlordsector.Kids.healthOf(p, c);
                    float kh = ks.health;
                    text("Health " + Math.round(kh), traitColor(kh >= landlordsector.data.Levels.sickBelow() ? Boolean.TRUE
                            : (kh < landlordsector.data.Levels.treatmentBelow() ? Boolean.FALSE : null)),
                            x + w - 115f, y + 6f, 110f);
                    List<String[]> lines = new ArrayList<>(ks.healthLines);
                    if (!lines.isEmpty()) lines.remove(lines.size() - 1);
                    lines.add(new String[] {"Sick kids fall ill more often. A childhood in poor health (below "
                            + Math.round(landlordsector.Kids.SICKLY_CHILDHOOD) + " on average) leaves a mark for life,"
                            + " and how well a kid is raised (happiness and health) decides the risk of losing them.",
                            null});
                    tipColored(x + w - 115f, y + 6f, 110f, 20f, "Health: " + Math.round(kh), lines);
                }
                if (c.traits().isEmpty() && !c.grown && landlordsector.Kids.stageOf(c) < landlordsector.Kids.CHILD) {
                    text("Traits come when " + landlordsector.Kids.he(c) + " starts school.", Misc.getGrayColor(),
                            x + w - 230f, y + 28f, 220f);
                } else {
                    traitRow(kidTraits(c), x + w - 230f, y + 28f, 40f, 220f);
                }
                y += 82f;
            }
            return y;
        }

        /** "Costs 2,500 a month. Lessons 1 of 3, 5 days together, the Academy, extra classes." */
        private String training(landlordsector.Child c) {
            List<String> parts = new ArrayList<>();
            int s = landlordsector.Kids.stageOf(c);
            if (s >= landlordsector.Kids.TEEN) {
                parts.add("lessons " + c.lessons + " of " + landlordsector.Kids.MAX_LESSONS);
                parts.add(c.bonds + (c.bonds == 1 ? " day" : " days") + " together");
            }
            if (c.academy) parts.add("the Academy");
            if (c.classes) parts.add("extra classes");
            float cost = landlordsector.Kids.upkeep(c);
            String head = cost > 0f ? "Costs " + Misc.getDGSCredits(cost) + " a month." : "Costs you nothing.";
            if (parts.isEmpty()) return head;
            String list = String.join(", ", parts);
            return head + " " + Character.toUpperCase(list.charAt(0)) + list.substring(1) + ".";
        }

        private List<Object[]> kidTraits(landlordsector.Child c) {
            List<Object[]> out = new ArrayList<>();
            for (String id : c.traits()) {
                landlordsector.data.TenantTraits.Def d = landlordsector.data.TenantTraits.get(id);
                if (d != null) {
                    out.add(new Object[] {d.name, d.good, d.name, landlordsector.ui.Tips.kidTrait(d), traitIcon(d.id)});
                }
            }
            return out;
        }

        /**
         * Furniture (0.5.0, Andrzej: "slots like the inventory ... drag&drop the furniture items into the squares"): one
         * square per slot of the home, and the furniture storage under it. Drag a piece from the storage into a slot to
         * place it, and from a slot back to the storage to take it out.
         */
        private void buildFurnitureTab(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            int slots = p.tier().slots;
            float gap = 8f;
            float size = Math.max(56f, Math.min(96f, (cw - 7 * gap) / 8f));
            List<landlordsector.Installed> placed = new ArrayList<>();
            for (landlordsector.Installed i : p.upgrades()) if (i.furniture) placed.add(i);

            float y = y0;
            title("Furniture: " + placed.size() + " of " + slots, rx, y, cw);
            tip(rx, y, cw, 26f, "Furniture", "Drag a piece from your storage into an empty square. Drag it back to"
                    + " the storage to take it out. A home holds one of each piece.");
            y += 32f;
            for (int k = 0; k < slots; k++) {
                float sx = rx + k * (size + gap);
                box(sx, y, size, size);
                dropZone("slot", sx, y, size, size);
                if (k >= placed.size()) continue;
                landlordsector.Installed i = placed.get(k);
                String icon = icon(i.id);
                portrait(icon, sx + 4f, y + 4f, size - 8f);
                draggable("placed:" + p.id + ":" + p.upgrades().indexOf(i), icon, sx, y, size, size);
                landlordsector.data.Upgrades.Def d = i.def();
                if (d != null) tipColored(sx, y, size, size, d.name, landlordsector.ui.Tips.upgrade(d));
            }
            y += size + 24f;

            java.util.Map<String, Integer> store = Portfolio.get().furniture();
            int total = 0;
            for (Integer n : store.values()) total += n;
            title("Furniture storage: " + total, rx, y, cw);
            tip(rx, y, cw, 26f, "Furniture storage", "Furniture you keep for your homes. Open markets on worlds with a"
                    + " property agent sell a few pieces, and salvage sometimes has some. Right-click a piece in your"
                    + " cargo to put it here.");
            y += 32f;
            int perRow = Math.max(1, (int) ((cw + gap) / (size + gap)));
            int rows = Math.max(1, (store.size() + perRow - 1) / perRow);
            float areaH = rows * (size + 22f + gap);
            dropZone("storage", rx, y, cw, Math.max(size + 22f, areaH));
            int k = 0;
            for (java.util.Map.Entry<String, Integer> e : store.entrySet()) {
                landlordsector.data.Upgrades.Def d = landlordsector.data.Upgrades.get(e.getKey());
                if (d == null || e.getValue() <= 0) continue;
                float sx = rx + (k % perRow) * (size + gap);
                float sy = y + (k / perRow) * (size + 22f + gap);
                box(sx, sy, size, size);
                String icon = icon(d.id);
                portrait(icon, sx + 4f, sy + 4f, size - 8f);
                String no = Actions.cannotPlace(p, d);
                label((e.getValue() > 1 ? e.getValue() + "x " : "") + d.name, no == null ? Misc.getTextColor()
                        : Misc.getGrayColor(), sx - 4f, sy + size + 2f, size + 8f, 18f, Alignment.MID,
                        Fonts.DEFAULT_SMALL);
                List<String[]> lines = new ArrayList<>(landlordsector.ui.Tips.upgrade(d));
                if (no != null && !no.startsWith("All furniture slots")) lines.add(new String[] {no, "-"});
                tipColored(sx, sy, size, size + 20f, d.name, lines);
                draggable("stored:" + p.id + ":" + d.id, icon, sx, sy, size, size);
                k++;
            }
            float after = y + Math.max(size + 22f, areaH) + 8f;
            if (k == 0) {
                text("Empty. Open markets sell furniture, and salvage sometimes has some.", Misc.getGrayColor(), rx,
                        y + 8f, cw);
            }
            int inCargo = Actions.furnitureInCargo();
            if (inCargo > 0) {
                text(inCargo + (inCargo == 1 ? " piece" : " pieces") + " of furniture in your cargo: right-click "
                        + (inCargo == 1 ? "it" : "them") + " there to put " + (inCargo == 1 ? "it" : "them")
                        + " in storage.", Misc.getHighlightColor(), rx, after, cw);
            }
        }

        /** A piece of furniture's icon: its item's own picture. */
        private String icon(String upgradeId) {
            try {
                String n = Global.getSettings().getSpecialItemSpec(landlordsector.data.Upgrades.ITEM_PREFIX + upgradeId)
                        .getIconName();
                if (n != null && !n.isEmpty()) return n;
            } catch (Throwable ignored) {
                // An item from a mod that is gone.
            }
            return "graphics/icons/cargo/landlordsector/" + upgradeId + ".png";
        }

        @Override
        protected void onDrop(Object dragKey, Object dropKey) {
            String[] parts = String.valueOf(dragKey).split(":");
            if (parts.length < 3) return;
            Property p = findHome(parts[1]);
            if (p == null) return;
            if ("stored".equals(parts[0]) && "slot".equals(dropKey)) {
                landlordsector.data.Upgrades.Def d = landlordsector.data.Upgrades.get(parts[2]);
                if (d != null) result(Actions.place(p, d));
            } else if ("placed".equals(parts[0]) && "storage".equals(dropKey)) {
                result(Actions.takeBack(p, installedAt(p, parts[2])));
            } else {
                return;
            }
            rebuild();
        }

        /**
         * Upgrades (0.5.0, Andrzej: "Upgrades are permanent", built at once, no slots, the cost in the Build button's
         * tooltip): what the home has, then what it can get.
         */
        private void buildUpgradesTab(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            float col = (cw - 16f) / 2f;
            float lx = rx, gx = rx + col + 16f;

            float y = y0;
            title("Upgrades", lx, y, col);
            tip(lx, y, col, 26f, "Upgrades", "Upgrades are permanent.");
            y += 30f;
            boolean any = false;
            for (landlordsector.Installed i : p.upgrades()) {
                if (i.furniture) continue;
                any = true;
                if (y > height - 40f) break;
                upgradeName(p, i.def(), i.name(), Boolean.TRUE, lx, y + 5f, col - 110f);
                button("remove:" + p.id + ":" + p.upgrades().indexOf(i), "Remove", lx + col - 100f, y, 100f, 26f,
                        true);
                y += 30f;
            }
            if (!any) text("None yet.", Misc.getGrayColor(), lx, y, col);

            y = y0;
            title("Build", gx, y, col);
            y += 30f;
            int hidden = 0;
            for (landlordsector.data.Upgrades.Def d : landlordsector.data.Upgrades.of(
                    landlordsector.data.Upgrades.Kind.RENOVATION)) {
                if (p.has(d.id) || !landlordsector.data.Upgrades.fits(d, p)) {
                    continue;
                }
                // 1.0.1 (Andrzej: "just hide them conditionally from the upgrades panel"): no heated floors on a
                // hot world.
                if (landlordsector.PropertyStats.noUse(d, p.market(), p.features(false))) continue;
                if (y > height - 40f) {
                    hidden++;
                    continue;
                }
                float cost = Actions.renovationCost(p, d);
                String no = Actions.cannotBuild(p, d);
                upgradeName(p, d, d.name, null, gx, y + 5f, col - 190f);
                button("build:" + p.id + ":" + d.id, "Build for " + Misc.getDGSCredits(cost), gx + col - 180f, y, 180f,
                        26f, no == null);
                String costLine = "Costs " + Misc.getDGSCredits(d.minCost) + " plus " + Math.round(d.cost * 100f)
                        + "% of the home's value.";
                if (no != null) tip(gx + col - 180f, y, 180f, 26f, d.name, costLine, no);
                else tip(gx + col - 180f, y, 180f, 26f, d.name, costLine);
                y += 30f;
            }
            if (hidden > 0) text("And " + hidden + " more that do not fit on the screen.", Misc.getGrayColor(), gx, y, col);
        }

        /**
         * Services (0.6.0, Andrzej: "Continuous payments for some boosts", their own panel): every service that fits
         * the home, hired or not, with its monthly price and a Hire / Cancel button.
         */
        private void buildServicesTab(Property p, float rx, float y0) {
            float cw = contentWidth(rightW());
            float y = y0;
            title("Services", rx, y, cw);
            tip(rx, y, cw, 26f, "Services", "Paid at the end of every month while hired, as a share of the home's Normal"
                    + " rent, also while it stands empty. Cancel at any time.");
            y += 30f;
            float total = 0f;
            for (String id : p.services()) {
                landlordsector.data.Services.Def d = landlordsector.data.Services.get(id);
                if (d != null) total += Actions.servicePrice(p, d);
            }
            text(p.services().isEmpty() ? "No services hired." : "Hired services cost " + Misc.getDGSCredits(total)
                    + " a month.", Misc.getGrayColor(), rx, y, cw);
            y += 28f;
            for (landlordsector.data.Services.Def d : landlordsector.data.Services.all()) {
                if (!landlordsector.data.Services.fits(d, p.tier().index, p.onStation())) continue;
                if (y > height - 40f) break;
                boolean on = p.hasService(d.id);
                float price = Actions.servicePrice(p, d);
                text(d.name, traitColor(on ? Boolean.TRUE : null), rx, y + 5f, cw - 420f);
                tipColored(rx, y + 5f, Math.min(cw - 420f, 260f), 18f, d.name, landlordsector.ui.Tips.service(d));
                text(Misc.getDGSCredits(price) + " a month", Misc.getGrayColor(), rx + cw - 400f, y + 5f, 180f);
                Btn b = button("service:" + p.id + ":" + d.id, on ? "Cancel" : "Hire", rx + cw - 200f, y, 200f, 26f,
                        true);
                b.selected = on;
                y += 30f;
            }
        }

        /** An upgrade's name with its tooltip. good: green (true) or plain (null). */
        private void upgradeName(landlordsector.data.Upgrades.Def d, String shown, Boolean good, float x, float y,
                float w) {
            upgradeName(null, d, shown, good, x, y, w);
        }

        /** The same, saying when it does nothing in this home (0.6.0). */
        private void upgradeName(Property p, landlordsector.data.Upgrades.Def d, String shown, Boolean good, float x,
                float y, float w) {
            List<String[]> useless = p != null && d != null ? PropertyStats.uselessHere(p, d)
                    : new ArrayList<String[]>();
            text(shown, useless.isEmpty() ? traitColor(good) : Misc.getGrayColor(), x, y, w);
            if (d == null) return;
            List<String[]> lines = new ArrayList<>(landlordsector.ui.Tips.upgrade(d));
            lines.addAll(useless);
            tipColored(x, y, Math.min(w, 260f), 18f, d.name, lines);
        }

        private void poolRow(Property p, Tenant a, float x, float y, float w) {
            box(x, y, w, 66f);
            portrait(a.portrait, x + 6f, y + 6f, 54f);
            float tx = x + 68f;
            float hw = w - 80f - 180f;
            rowTraits(p, a, y, tx, hw);
            button("movein:" + p.id + ":" + a.id, "Move in", x + w - 8f - 160f, y + 18f, 160f, 28f,
                    Actions.refusal(p, a) == null);
        }

        /**
         * LLS-31: an applicant row's text and traits. With every icon in, name and happiness on the left and the trait
         * icons in a column of their own; otherwise the 1.x layout with the names on the middle line.
         */
        private void rowTraits(Property p, Tenant a, float y, float tx, float hw) {
            List<Object[]> items = tenantTraits(a);
            boolean icons = tenantIconsReady();
            for (Object[] it : items) icons &= landlordsector.Art.has((String) it[4]);
            if (!icons || hw < 470f) {
                headLine(a, tx, y + 4f, hw);
                coloredRow(items, tx, y + 22f, hw);
                happinessThere(p, a, tx, y + 42f);
                return;
            }
            float col = 370f;
            headLine(a, tx, y + 8f, col - 10f);
            happinessThere(p, a, tx, y + 36f);
            traitRow(items, tx + col, y + 6f, 50f, hw - col);
        }

        /** "Name - Faction   Wealth: [B]" with the wealth tooltip. */
        private void headLine(Tenant a, float tx, float y, float hw) {
            String head = nameAndFaction(a);
            com.fs.starfarer.api.ui.LabelAPI nl = text(head, Misc.getHighlightColor(), tx, y, hw);
            factionColored(nl, a);
            float nw = Math.min(hw, nl.computeTextWidth(head) + 4f);
            String letter = TenantRules.wealthLetter(a.wealth);
            String wl = PropertyStats.graded("Wealth", letter);
            com.fs.starfarer.api.ui.LabelAPI w = text(wl, Misc.getGrayColor(), tx + nw + 14f, y,
                    Math.max(40f, hw - nw - 14f));
            w.setHighlight("[" + letter + "]");
            w.setHighlightColors(Misc.getHighlightColor());
            tip(tx + nw + 14f, y, 100f, 18f, wl, landlordsector.ui.Tips.wealth(a.wealth, a.homeShift()));
        }

        /** "Happiness there 56" or why they won't move in, with the happiness tooltip. */
        private void happinessThere(Property p, Tenant a, float tx, float y) {
            String no = Actions.refusal(p, a);
            PropertyStats st = PropertyStats.withTenant(p, a);
            if (no != null) {
                text(no, traitColor(Boolean.FALSE), tx, y, 360f);
            } else {
                float h = st.happiness;
                text("Happiness there " + Math.round(h),
                        traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)), tx, y, 220f);
            }
            tipColored(tx, y, 220f, 18f, "Happiness there: " + Math.round(st.happiness),
                    visibleLines(a, st.happinessLines));
        }

        private void applicantRow(Property p, Tenant a, float x, float y, float w, float fee) {
            box(x, y, w, 66f);
            portrait(a.portrait, x + 6f, y + 6f, 54f);
            float tx = x + 68f;
            float hw = w - 80f - 330f;
            rowTraits(p, a, y, tx, hw);
            String no = Actions.refusal(p, a);
            // 8 pixels from the card's right edge (0.3.8: Turn away touched it).
            float bx = x + w - 8f - 330f;
            button("screen:" + p.id + ":" + a.id, a.screened ? "Screened" : "Screen for " + Misc.getDGSCredits(fee),
                    bx, y + 18f, 150f, 28f, !a.screened && Actions.credits() >= fee);
            tip(bx, y + 18f, 150f, 28f, "Screen", "Shows all of this applicant's traits.");
            button("accept:" + p.id + ":" + a.id, "Accept", bx + 156f, y + 18f, 84f, 28f, no == null);
            button("reject:" + p.id + ":" + a.id, "Turn away", bx + 246f, y + 18f, 84f, 28f, true);
        }

        /** The home's events, newest first, the event part in red or green. */
        private void buildLog(Property p, float x, float y0, float w) {
            List<String> log = p.log();
            if (log.isEmpty()) {
                text("Nothing yet.", Misc.getGrayColor(), x, y0, w);
                return;
            }
            float y = y0;
            for (int i = log.size() - 1; i >= 0 && y < height - 40f; i--) {
                String[] parts = log.get(i).split("\t", -1);
                String date, colored, rest;
                Boolean good = null;
                if (parts.length == 4) {
                    good = "+".equals(parts[0]) ? Boolean.TRUE : ("-".equals(parts[0]) ? Boolean.FALSE : null);
                    date = parts[1];
                    colored = parts[2];
                    rest = parts[3];
                } else {
                    // Entries written before 0.3.2: "date  text".
                    date = "";
                    colored = "";
                    rest = log.get(i);
                }
                if (!date.isEmpty()) {
                    text(date, Misc.getGrayColor(), x, y, w);
                    y += 16f;
                }
                String all = colored + rest;
                int n = good == null ? 0 : colored.length();
                y += wrapped(all, n, landlordsector.Notify.color(good), Misc.getTextColor(), x, y, w) + 6f;
            }
        }

        // ---- Tenant pool ----

        private void buildPool() {
            List<Tenant> pool = Portfolio.get().tenantPool();
            if (pool.isEmpty()) {
                text("Nobody waits in your tenant pool. Headhunters bring tenants here.", 10f, TOP + 10f,
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
                String wants = PropertyStats.graded("Wealth", TenantRules.wealthLetter(t.wealth)) + ", wants "
                        + PropertyStats.article(PropertyStats.desiredTier(t.wealth, t.homeShift()).toLowerCase());
                com.fs.starfarer.api.ui.LabelAPI nl = text(nameAndFaction(t), Misc.getHighlightColor(), 18f, y + 5f,
                        LIST_W - 20f);
                boolean tooLong = nl.computeTextWidth(nameAndFaction(t)) > LIST_W - 24f;
                if (tooLong) {
                    // Too long for one line: the faction moves down next to the wealth.
                    nl.setText(t.name);
                    wants = Tenants.factionName(t.factionId) + " - " + wants;
                } else {
                    factionColored(nl, t);
                }
                com.fs.starfarer.api.ui.LabelAPI wl = text(wants, Misc.getGrayColor(), 18f, y + 25f, LIST_W - 20f);
                if (tooLong) factionColored(wl, t);
            }
            float rx = rightX();
            float cw = contentWidth(rightW());
            float y = tenantCard(chosen, null, rx, TOP, cw);
            button("dismiss:" + chosen.id, "Dismiss", rx + cw - 150f, TOP, 150f, 28f, true);
            text("Move into", Misc.getGrayColor(), rx, y + 4f, cw);
            y += 28f;
            List<Property> empty = new ArrayList<>();
            for (Property p : Portfolio.get().properties()) {
                if (p.tenant == null && !p.condemned && !p.playerHome && p.market() != null) empty.add(p);
            }
            if (empty.isEmpty()) {
                text("You have no empty homes.", Misc.getGrayColor(), rx, y, cw);
                return;
            }
            final Tenant who = chosen;
            // Homes they would take first, the happiest on top.
            empty.sort((a, b) -> {
                boolean fa = Actions.refusal(a, who) == null, fb = Actions.refusal(b, who) == null;
                if (fa != fb) return fa ? -1 : 1;
                return Float.compare(PropertyStats.withTenant(b, who).happiness,
                        PropertyStats.withTenant(a, who).happiness);
            });
            for (Property p : empty) {
                if (y > height - 40f) break;
                String no = Actions.refusal(p, chosen);
                text(p.displayName() + ", " + p.tier().name.toLowerCase(), rx, y + 5f, cw - 440f);
                PropertyStats pst = PropertyStats.withTenant(p, chosen);
                if (no != null) {
                    text(no, traitColor(Boolean.FALSE), rx + cw - 430f, y + 5f, 270f);
                } else {
                    float h = pst.happiness;
                    text("Happiness " + Math.round(h),
                            traitColor(h >= 50f ? Boolean.TRUE : (h < 25f ? Boolean.FALSE : null)),
                            rx + cw - 430f, y + 5f, 270f);
                }
                tipColored(rx + cw - 430f, y + 5f, 270f, 18f, "Happiness there: " + Math.round(pst.happiness),
                        visibleLines(chosen, pst.happinessLines));
                button("movein:" + p.id + ":" + chosen.id, "Move in", rx + cw - 150f, y, 150f, 28f, no == null);
                y += 34f;
            }
        }

        // ---- Finders ----

        private void buildFinders() {
            List<Finders.State> hired = Finders.hired();
            float rx = rightX();
            float cw = contentWidth(rightW());
            if (hired.isEmpty()) {
                text("You have hired no headhunters. Each works out of one market's comm directory:", 10f, TOP + 10f,
                        width - 20f);
                float y = TOP + 36f;
                for (FinderDefs.Def d : FinderDefs.all()) {
                    com.fs.starfarer.api.campaign.econ.MarketAPI m = Finders.homeMarket(d);
                    if (m == null) continue;
                    String line = d.name() + " on " + m.getName() + ": " + d.perk;
                    text(line, 20f, y, width - 40f);
                    tipColored(20f, y, width - 40f, 18f, d.name(), landlordsector.ui.Tips.headhunter(d));
                    y += 20f;
                }
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
            portrait(chosen.person != null ? chosen.person.getPortraitSprite() : null, rx, TOP, 86f);
            title(d.name(), rx + 98f, TOP, cw - 98f);
            com.fs.starfarer.api.campaign.econ.MarketAPI home = Finders.homeMarket(d);
            text("Headhunter" + (home != null ? " from " + home.getName() : "") + ". " + d.perk, rx + 98f, TOP + 28f,
                    cw - 98f);
            tipColored(rx + 98f, TOP + 28f, cw - 98f, 18f, d.name(), landlordsector.ui.Tips.headhunter(d));
            text("Brings " + d.tenants + " screened tenants in " + Math.round(d.days) + " days.", Misc.getGrayColor(),
                    rx + 98f, TOP + 48f, cw - 98f);
            float y = TOP + 100f;
            if (chosen.searching) {
                text("Searching. Back in " + Math.max(1, Math.round(chosen.daysLeft())) + " days.", rx, y, cw);
            } else {
                button("search:" + chosen.id, "Start a search for " + Misc.getDGSCredits(d.search), rx, y, 300f, 28f,
                        Actions.credits() >= d.search);
            }
            float oy = y + 50f;
            text("Other headhunters", Misc.getGrayColor(), rx, oy, cw);
            oy += 22f;
            for (FinderDefs.Def o : FinderDefs.all()) {
                Finders.State os = Finders.byFlag(o.id);
                if (os != null && os.hired) continue;
                com.fs.starfarer.api.campaign.econ.MarketAPI m = Finders.homeMarket(o);
                if (m == null || oy > height - 30f) continue;
                text(o.name() + " on " + m.getName() + ": " + o.perk, rx, oy, cw);
                tipColored(rx, oy, cw, 18f, o.name(), landlordsector.ui.Tips.headhunter(o));
                oy += 20f;
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
                    choosingPartner = false;
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
                        good("Dismissed " + t.name + ".");
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
                case "nickname":
                    if (pr.tenant != null) {
                        message = "";
                        startTyping("nick:" + pr.id, pr.tenant.hasNickname() ? pr.tenant.name : "");
                    }
                    break;
                case "lower":
                    note(Actions.setRent(pr, pr.askingRent().lower()));
                    break;
                case "higher":
                    note(Actions.setRent(pr, pr.askingRent().higher()));
                    break;
                case "lease":
                    Actions.cycleLease(pr);
                    message = "";
                    break;
                case "answer":
                    if (parts.length > 2) {
                        try {
                            String what = landlordsector.Choices.answer(pr, Integer.parseInt(parts[2]),
                                    landlordsector.Choices.PLAYER, new java.util.Random());
                            message = what;
                            messageColor = Misc.getHighlightColor();
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    break;
                case "repair":
                    result(Actions.repair(pr));
                    break;
                case "sellfurnished":
                    if (("furnished:" + pr.id).equals(pendingSell)) {
                        result(Actions.sell(pr, true));
                        selectedHome = null;
                    } else {
                        message = "Click again to sell " + pr.displayName() + " with its furniture.";
                        messageColor = Misc.getHighlightColor();
                        return "furnished:" + pr.id;
                    }
                    break;
                case "build": {
                    landlordsector.data.Upgrades.Def d = landlordsector.data.Upgrades.get(parts[2]);
                    if (d != null) result(Actions.build(pr, d));
                    break;
                }
                case "remove":
                    result(Actions.remove(pr, installedAt(pr, parts[2])));
                    break;
                case "sell":
                    if (pr.id.equals(pendingSell)) {
                        result(Actions.sell(pr));
                        selectedHome = null;
                    } else {
                        message = "Click again to sell " + pr.displayName() + ". The price takes off a broker fee.";
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
                case "service":
                    if (parts.length > 2) result(Actions.toggleService(pr, parts[2]));
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
                case "declare":
                    result(landlordsector.Family.declareHome(pr));
                    sub = Sub.TENANT;
                    break;
                case "leave":
                    result(landlordsector.Family.stopLiving(pr));
                    choosingPartner = false;
                    break;
                case "choose":
                    choosingPartner = !choosingPartner;
                    message = "";
                    break;
                case "partner": {
                    com.fs.starfarer.api.characters.PersonAPI who = null;
                    for (com.fs.starfarer.api.characters.PersonAPI c : landlordsector.Family.candidates()) {
                        if (parts.length > 2 && c.getId().equals(parts[2])) who = c;
                    }
                    if (who != null) result(landlordsector.Family.propose(pr, who, new java.util.Random()));
                    choosingPartner = false;
                    break;
                }
                default:
                    break;
            }
            return null;
        }

        private void note(String text) {
            message = text == null ? "" : text;
            messageColor = Misc.getHighlightColor();
        }

        private void result(String text) {
            boolean ok = text.startsWith("Sold") || text.startsWith("Repaired") || text.startsWith("Rebuilt")
                    || text.startsWith("Screened") || text.contains(" moved into ") || text.startsWith("Turned")
                    || text.startsWith("Evicted") || text.startsWith("Placed") || text.startsWith("Took back")
                    || text.startsWith("Built") || text.startsWith("Removed") || text.startsWith("Hired")
                    || text.startsWith("Cancelled") || text.startsWith("You moved") || text.contains(" is now your ");
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

    private static landlordsector.Installed installedAt(Property p, String index) {
        try {
            int i = Integer.parseInt(index);
            return i >= 0 && i < p.upgrades().size() ? p.upgrades().get(i) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Tenant findApplicant(Property p, String id) {
        for (Tenant t : p.applicants()) if (t.id.equals(id)) return t;
        return null;
    }
}
