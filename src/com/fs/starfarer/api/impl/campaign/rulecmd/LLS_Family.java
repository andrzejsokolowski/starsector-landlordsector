package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoPickerListener;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomDialogDelegate;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;

import landlordsector.Child;
import landlordsector.Family;
import landlordsector.HomeownerScript;
import landlordsector.Property;
import landlordsector.Tenant;

/**
 * 1.2.0: talking to your partner or one of your kids over the comm link. One command, the first parameter says what
 * to do: greet, options, try, gift, breakup, breakupYes, breakupNo, greetChild, childOptions, rename, path.
 */
public class LLS_Family extends BaseCommandPlugin {

    private static final Random RANDOM = new Random();

    @Override
    public boolean doesCommandAddOptions() {
        return true;
    }

    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params,
            Map<String, MemoryAPI> memoryMap) {
        if (dialog == null || params.isEmpty()) return false;
        PersonAPI who = dialog.getInteractionTarget() != null ? dialog.getInteractionTarget().getActivePerson() : null;
        Property p = Family.homeOf(who);
        if (p == null) return false;
        String verb = params.get(0).getString(memoryMap);
        TextPanelAPI text = dialog.getTextPanel();
        OptionPanelAPI opts = dialog.getOptionPanel();
        Tenant t = p.partner();
        Child c = Family.childOf(who);
        switch (verb) {
            case "greet":
                if (t != null) say(text, partnerGreeting(p, t));
                return true;
            case "options":
                return t != null && partnerOptions(opts, p, t);
            case "try":
                return t != null && tryForChild(dialog, memoryMap, p, t);
            case "gift":
                return t != null && gift(dialog, memoryMap, p, t);
            case "breakup":
                if (t == null) return false;
                opts.clearOptions();
                opts.addOption("Yes. It's over.", "lls_fam_breakup_yes");
                if (t.isSpouse()) {
                    opts.setTooltip("lls_fam_breakup_yes", "The house is sold and you get half. The kids go with "
                            + him(t) + ".");
                } else if (!p.children().isEmpty()) {
                    opts.setTooltip("lls_fam_breakup_yes", capital(Family.his(t)) + " kids go with " + him(t) + ".");
                }
                opts.addOption("No. Forget I said anything.", "lls_fam_breakup_no");
                return true;
            case "breakupYes": {
                if (t == null) return false;
                boolean spouse = t.isSpouse();
                say(text, spouse ? "Fine. My lawyer will call you." : "Fine. I'll pack my things.");
                text.addPara(Family.breakUp(p));
                HomeownerScript.refresh();
                end(opts);
                return true;
            }
            case "breakupNo":
                FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
                return true;
            case "greetChild":
                if (c != null) childGreeting(text, c);
                return true;
            case "childOptions":
                return c != null && childOptions(opts, c);
            case "rename":
                return c != null && rename(dialog, memoryMap, c);
            case "path":
                return c != null && params.size() > 1 && path(dialog, memoryMap, p, c, params.get(1).getString(memoryMap));
            default:
                return false;
        }
    }

    // ---- Partner ----

    private static String partnerGreeting(Property p, Tenant t) {
        float h = p.stats().happiness;
        if (t.conceivedAt != 0L && t.toldPregnant) {
            return pick("Not long now. The baby kicks all night.", "I keep thinking about names.");
        }
        if (t.lastGap > 60f) {
            return pick("Look who finally remembered where they live.", "Oh, you're alive. Good to know.");
        }
        if (h < 25f) return pick("Oh. It's you.", "What do you want?");
        if (h >= 60f) return pick("There you are. I missed you.", "Hey, love. Staying for dinner?");
        return pick("Hi. How was the trip?", "Hey. You look tired.");
    }

    private static boolean partnerOptions(OptionPanelAPI opts, Property p, Tenant t) {
        opts.addOption("Let's try for a child.", "lls_fam_try");
        String needs = Family.cannotTry(p);
        if (t.conceivedAt != 0L && t.toldPregnant) {
            opts.setEnabled("lls_fam_try", false);
            opts.setTooltip("lls_fam_try", "A child is already on the way.");
        } else if (needs != null) {
            opts.setEnabled("lls_fam_try", false);
            opts.setTooltip("lls_fam_try", needs);
        } else if (t.lastTry != 0L && Family.daysSince(t.lastTry) < 1f) {
            opts.setEnabled("lls_fam_try", false);
            opts.setTooltip("lls_fam_try", "Again? Not today, give me some time to rest...");
        }
        opts.addOption("I got you something.", "lls_fam_gift");
        if (Family.giftable().isEmpty()) {
            opts.setEnabled("lls_fam_gift", false);
            opts.setTooltip("lls_fam_gift", "You have nothing to give.");
        }
        opts.addOption(t.isSpouse() ? "I want a divorce." : "I think we should break up.", "lls_fam_breakup");
        return true;
    }

    private static boolean tryForChild(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, Property p,
            Tenant t) {
        TextPanelAPI text = dialog.getTextPanel();
        int r = Family.tryForChild(p, RANDOM);
        // 1.3.0 (Andrzej, text review): "We already have one on the way, remember?" removed.
        if (r == 2) text.addPara("You spend the night together.");
        else say(text, "Not tonight. I'm exhausted.");
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
        return true;
    }

    private static boolean gift(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap,
            final Property p, final Tenant t) {
        CargoAPI cargo = Family.giftable();
        dialog.showCargoPickerDialog("Pick a gift", "Give", "Cancel", true, 310f, cargo, new CargoPickerListener() {
            @Override
            public void pickedCargo(CargoAPI picked) {
                if (picked == null || picked.isEmpty()) {
                    back(dialog, memoryMap);
                    return;
                }
                Family.Gift g = Family.gift(p, picked, RANDOM);
                TextPanelAPI text = dialog.getTextPanel();
                if (g.value <= 0f) {
                    back(dialog, memoryMap);
                    return;
                }
                say(text, reaction(g));
                String he = capital(Family.he(t));
                StringBuilder sb = new StringBuilder(he + " sells it for " + Misc.getDGSCredits(g.value));
                if (!g.built.isEmpty()) sb.append(" and has ").append(list(g.built)).append(" built");
                if (g.repairs > 0f) sb.append(g.built.isEmpty() ? " and has" : ", then has").append(" the house repaired");
                sb.append(".");
                if (g.kept >= 1f) {
                    sb.append(" ").append(he).append(" keeps the rest: ").append(Misc.getDGSCredits(g.kept)).append(".");
                }
                text.addPara(sb.toString());
                back(dialog, memoryMap);
            }

            @Override
            public void cancelledCargoSelection() {
                // 1.7.0 (Andrzej: "cancel makes an unescapable dialog"): the options were cleared when the gift was
                // chosen, so they come back here.
                back(dialog, memoryMap);
            }

            @Override
            public void recreateTextPanel(TooltipMakerAPI panel, CargoAPI cargo, CargoStackAPI pickedUp,
                    boolean pickedUpFromSource, CargoAPI combined) {
                float value = 0f;
                for (CargoStackAPI s : combined.getStacksCopy()) value += s.getBaseValuePerUnit() * s.getSize();
                panel.addPara("Worth about %s.", 0f, Misc.getHighlightColor(), Misc.getDGSCredits(value));
            }
        });
        return true;
    }

    private static String reaction(Family.Gift g) {
        String id = g.firstItem == null ? "" : g.firstItem;
        switch (id) {
            case "corrupted_nanoforge":
                return "A ruined nanoforge? I can't even print furniture with this.";
            case "pristine_nanoforge":
                return "Where did you find this? I'm going to open up a business with this that loses 10k a month!";
            case "orbital_fusion_lamp":
                return "A fusion lamp. For the living room. Sure.";
            case "synchrotron":
                return "We will never have to pay for gas again!";
            case "soil_nanites":
                return "Oh my god, the flowers are going to be so beautiful!";
            case "cryoarithmetic_engine":
                return "Why is it so cold suddenly?";
            case "coronal_portal":
                return "What in the goddamn?";
            case "mantle_bore":
                return "Going to mount this on our truck and mow through traffic.";
            case "biofactory_embryo":
                return "Can we use this next time you want a child?";
            case "drone_replicator":
                return "Am I going to be put on a list now?";
            case "dealmaker_holosuite":
                return "I'm selling all our used clothes for 40% more tomorrow!";
            case "fullerene_spool":
                return "A perfect gift if you ever decide to leave me. I could strangle you with it. Haha, just kidding. Unless?";
            case "plasma_dynamo":
                return "Can I use this as an air filter?";
            case "catalytic_core":
                return "Uhh... thanks. But this is quite useless...";
            default:
                break;
        }
        if (g.value < 20000f) return "Thank you!";
        if (g.value < 200000f) return "For me? You shouldn't have.";
        if (g.value < 1000000f) return "Oh my god, you might as well buy a yacht!";
        return "Are you out of your mind? This could buy a moon!";
    }

    // ---- Kids ----

    private static void childGreeting(TextPanelAPI text, Child c) {
        String dad = Family.parentWord();
        if (!c.grown && Family.ageDays(c) < Family.DAYS_PER_CYCLE) {
            // 1.3.0 (Andrzej): you are in the home, visiting in person.
            text.addPara(c.name + " grabs your finger and giggles.");
            return;
        }
        if (c.grown && c.path == null) {
            say(text, "So, " + dad + ". What now?");
            return;
        }
        String path = c.path == null ? "" : c.path;
        switch (path) {
            case Child.OFFICER:
                say(text, dad + "! When can we take the Kite out again?");
                break;
            case Child.ADMIN:
                say(text, dad + ", did you know a gamma core can run a whole farm?");
                break;
            case Child.WORKER:
                say(text, "I dug all the way down to bedrock today!");
                break;
            case Child.AGENT:
                say(text, "I followed the neighbor around all day and he never saw me.");
                break;
            case Child.GROWTH:
                say(text, "Everyone at school says I'm the prettiest.");
                break;
            default:
                say(text, "Hi, " + dad + "!");
        }
    }

    private static boolean childOptions(OptionPanelAPI opts, Child c) {
        opts.addOption("Let's pick a new name for you.", "lls_kid_rename");
        opts.addOption("Wanna go for a drive in the Kite I bought, " + c.noun() + "?", "lls_kid_officer");
        // 1.3.0 (Andrzej: "Needs a Kite-class frigate in fleet.").
        if (Family.kite() == null) {
            opts.setEnabled("lls_kid_officer", false);
            opts.setTooltip("lls_kid_officer", "You need a Kite in your fleet.");
        }
        opts.addOption("I see you're reading \"AI Cores in Industry\"?", "lls_kid_admin");
        opts.addOption("I see you're playing Minecraft...", "lls_kid_worker");
        if (Family.nexerelin()) opts.addOption("That 007 movie is pretty cool, eh?", "lls_kid_agent");
        opts.addOption("Wow, you're so pretty!", "lls_kid_growth");
        return true;
    }

    private static boolean path(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap, Property p, Child c,
            String path) {
        TextPanelAPI text = dialog.getTextPanel();
        // 1.3.0 (Andrzej: "a 2% chance of your kid crashing and dying in an explosion due to a malfunction,
        // increased by 5% per D-MOD on the shuttle"). Babies only giggle and stay home.
        if (Child.OFFICER.equals(path) && (c.grown || Family.ageDays(c) >= Family.DAYS_PER_CYCLE)) {
            String crash = Family.kiteDrive(p, c, RANDOM);
            if (crash != null) {
                text.addPara(crash, Misc.getNegativeHighlightColor());
                HomeownerScript.refresh();
                end(dialog.getOptionPanel());
                return true;
            }
        }
        c.path = path;
        if (!c.grown && Family.ageDays(c) < Family.DAYS_PER_CYCLE) {
            text.addPara(c.name + " giggles.");
        } else {
            switch (path) {
                case Child.OFFICER:
                    say(text, "Yes! Can I fly it this time?");
                    break;
                case Child.ADMIN:
                    say(text, "It's really good. I want to run a colony one day.");
                    break;
                case Child.WORKER:
                    say(text, "I'm going to dig the biggest mine ever.");
                    break;
                case Child.AGENT:
                    say(text, "I want to be a spy when I grow up.");
                    break;
                default:
                    say(text, "Thanks, " + Family.parentWord() + "! Everyone says that.");
            }
        }
        if (c.grown) {
            if (Family.startLife(p, c, RANDOM)) {
                text.addPara(c.name + " moves out.");
                end(dialog.getOptionPanel());
                return true;
            }
            text.addPara(waitReason(path), Misc.getHighlightColor());
        }
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
        return true;
    }

    private static String waitReason(String path) {
        switch (path) {
            case Child.OFFICER:
                return "There's no room for another officer in your fleet yet.";
            case Child.ADMIN:
                return "You have no room for another administrator yet.";
            case Child.GROWTH:
                return "You have no colony for them to move to yet.";
            default:
                return "They'll wait at home for now.";
        }
    }

    private static boolean rename(final InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap,
            final Child c) {
        dialog.showCustomDialog(420f, 70f, new CustomDialogDelegate() {
            private TextFieldAPI field;

            @Override
            public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
                TooltipMakerAPI tt = panel.createUIElement(420f, 70f, false);
                tt.addPara("New name", 0f);
                field = tt.addTextField(400f, 8f);
                field.setText(c.name);
                panel.addUIElement(tt).inTL(0f, 0f);
                field.grabFocus(false);
            }

            @Override
            public boolean hasCancelButton() {
                return true;
            }

            @Override
            public String getConfirmText() {
                return "Rename";
            }

            @Override
            public String getCancelText() {
                return "Cancel";
            }

            @Override
            public void customDialogConfirm() {
                if (field == null || field.getText() == null || field.getText().trim().isEmpty()) {
                    back(dialog, memoryMap);
                    return;
                }
                Family.rename(c, field.getText());
                TextPanelAPI text = dialog.getTextPanel();
                if (!c.grown && Family.ageDays(c) < Family.DAYS_PER_CYCLE) text.addPara(c.name + " giggles.");
                else say(text, c.name.split(" ")[0] + "? I like it.");
                back(dialog, memoryMap);
            }

            @Override
            public void customDialogCancel() {
                back(dialog, memoryMap);
            }

            @Override
            public CustomUIPanelPlugin getCustomPanelPlugin() {
                return null;
            }
        });
        return true;
    }

    // ---- Text ----

    private static void say(TextPanelAPI text, String line) {
        text.addPara("\"" + line + "\"");
    }

    /** The partner's or child's options again, after a picker or a name box closes. */
    private static void back(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        dialog.getOptionPanel().clearOptions();
        FireAll.fire(null, dialog, memoryMap, "PopulateOptions");
    }

    private static void end(OptionPanelAPI opts) {
        opts.clearOptions();
        opts.addOption("Cut the comm link", "cutCommLink");
    }

    private static String pick(String... lines) {
        return lines[RANDOM.nextInt(lines.length)];
    }

    private static String him(Tenant t) {
        String he = Family.he(t);
        return "she".equals(he) ? "her" : ("he".equals(he) ? "him" : "them");
    }

    private static String capital(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String list(List<String> items) {
        if (items.size() == 1) return items.get(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(i == items.size() - 1 ? " and " : ", ");
            sb.append(items.get(i));
        }
        return sb.toString();
    }
}
