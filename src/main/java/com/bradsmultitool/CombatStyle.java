package com.bradsmultitool;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.util.Text;

/**
 * Your current combat style, as the combat tab shows it (or the spell you just cast by hand): its discipline,
 * label and icon, whether it hits a monster's weakness, and how much XP a point of damage gives each skill
 * with it. Shared by the PvM HUD and the Skill Tracker's kill counts.
 */
@Slf4j
@Singleton
class CombatStyle {

    // Raw ids rather than the deprecated VarPlayer/Varbits/InventoryID constants
    private static final int VARP_ATTACK_STYLE = 43;
    private static final int VARBIT_EQUIPPED_WEAPON_TYPE = 357;
    private static final int VARBIT_DEFENSIVE_CASTING = 2668;
    private static final int VARBIT_AUTOCAST_SPELL = 276;
    private static final int CONTAINER_EQUIPMENT = 94;
    // A manual cast stays the "active" style this long; casting repeats every 5 ticks
    private static final int MANUAL_CAST_TIMEOUT_TICKS = 10;
    private static final int EQUIPMENT_WEAPON_SLOT = 3;
    // The game's own combat style data: enum of weapon category -> enum of style structs, and the
    // struct param naming each style's XP type ("Accurate", "Rapid", "Casting"...)
    private static final int ENUM_WEAPON_STYLES = 3908;
    private static final int PARAM_ATTACK_STYLE_NAME = 1407;

    @Getter
    private String combatDiscipline = "Melee";

    @Getter
    private String combatMethod = "Slash";

    @Getter
    private CombatIcons.Icon combatMethodIcon = null;

    /** The player's current combat style (weapon style, autocast or hand-cast spell). */
    @Getter
    private WeaponStyles.Style currentStyle = null;
    private int xpStyleKey = -1;
    private String xpStyle = "";

    private final SpellbookReader spellbook = new SpellbookReader();
    private String manualSpellName = null;
    private int manualSpellSpriteId = -1;
    private int manualCastTick = -1;

    private final Client client;

    @Inject
    CombatStyle(Client client) {
        this.client = client;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (client.getGameState() == GameState.LOGGED_IN) {
            update();
        }
    }

    /**
     * XP per damage your current combat style gives in this skill, for a kill estimate. A style that doesn't
     * train it counts as if you switched to one that does (4 per damage; 2 for Defence by Longrange, 1 by
     * defensive casting). Controlled splits 4/3 to each of Attack, Strength and Defence; Longrange 2 each to
     * Ranged and Defence; Hitpoints is always 4/3.
     */
    public double xpPerDamage(Skill skill) {
        if (skill == Skill.HITPOINTS) {
            return 4.0 / 3;
        }
        boolean melee = skill == Skill.ATTACK || skill == Skill.STRENGTH || skill == Skill.DEFENCE;
        String discipline = currentStyle != null ? currentStyle.discipline : "";
        if (melee && "Melee".equals(discipline) && xpStyle.contains("controlled")) {
            return 4.0 / 3;
        }
        if ((skill == Skill.RANGED || skill == Skill.DEFENCE) && "Ranged".equals(discipline) && xpStyle.contains("longrange")) {
            return 2;
        }
        if (skill == Skill.DEFENCE && "Defensive Cast".equals(currentStyle != null ? currentStyle.buttonName : null)) {
            return 1;
        }
        return 4;
    }

    // Weapon category (varbit 357) -> its row in the game's weapon category table, or -1
    private final Map<Integer, Integer> weaponCategoryRows = new HashMap<>();

    private int weaponCategoryRow(int category) {
        Integer row = weaponCategoryRows.get(category);
        if (row == null) {
            row = -1;
            try {
                List<Integer> rows = client.getDBRowsByValue(DBTableID.CombatInterfaceWeaponCategory.ID,
                    DBTableID.CombatInterfaceWeaponCategory.COL_ID, 0, category);
                if (!rows.isEmpty()) {
                    row = rows.get(0);
                }
            } catch (RuntimeException e) {
                log.debug("No weapon category row for {}", category, e);
            }
            log.debug("Weapon category {} is row {}", category, row);
            weaponCategoryRows.put(category, row);
        }
        return row;
    }

    /** The game's name for a combat tab button's XP style ("Accurate", "Rapid"...), or null. */
    private String gameStyleName(int category, int styleIndex) {
        try {
            int stylesEnum = client.getEnum(ENUM_WEAPON_STYLES).getIntValue(category);
            if (stylesEnum == -1) {
                return null;
            }
            int[] structs = client.getEnum(stylesEnum).getIntVals();
            if (styleIndex < 0 || styleIndex >= structs.length) {
                return null;
            }
            return client.getStructComposition(structs[styleIndex]).getStringValue(PARAM_ATTACK_STYLE_NAME);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void update() {
        int category = client.getVarbitValue(VARBIT_EQUIPPED_WEAPON_TYPE);
        int styleIndex = client.getVarpValue(VARP_ATTACK_STYLE);
        int categoryRow = weaponCategoryRow(category);
        boolean defensiveCasting = client.getVarbitValue(VARBIT_DEFENSIVE_CASTING) == 1;
        int autocastSpell = client.getVarbitValue(VARBIT_AUTOCAST_SPELL);
        // The game's name for the style's XP type ("Accurate", "Controlled", "Longrange"...), looked up once per style
        int styleKey = category * 16 + styleIndex;
        if (styleKey != xpStyleKey) {
            xpStyleKey = styleKey;
            String name = styleIndex == 4 ? "Casting" : gameStyleName(category, styleIndex);
            xpStyle = name != null ? name.toLowerCase() : "";
        }
        WeaponStyles.Style style = WeaponStyles.resolve(categoryRow, styleIndex, defensiveCasting, autocastSpell, null);
        if ("Unknown".equals(style.buttonName)) {
            // A weapon type the table doesn't know: go by the game's name for the style
            style = WeaponStyles.resolve(categoryRow, styleIndex, defensiveCasting, autocastSpell,
                gameStyleName(category, styleIndex));
        }

        int weaponId = equippedWeaponId();
        if (weaponId > 0) {
            style = WeaponStyles.withEquippedWeapon(style, client.getItemDefinition(weaponId).getName());
        }

        spellbook.refresh(client);

        // Which spell is being cast: a recent hand cast wins, then the combat tab's autocast slot
        String spellName = null;
        int fallbackSprite = -1;
        if (manualSpellName != null && client.getTickCount() - manualCastTick <= MANUAL_CAST_TIMEOUT_TICKS) {
            spellName = manualSpellName;
            fallbackSprite = manualSpellSpriteId;
            style = WeaponStyles.manualCast(spellName);
        } else if ("Spell Cast".equals(style.buttonName) || "Defensive Cast".equals(style.buttonName)) {
            boolean defensive = "Defensive Cast".equals(style.buttonName);
            spellName = spellbook.autocastSpell(client, defensive);
            if (spellName != null) {
                style = WeaponStyles.spellStyle(spellName, defensive);
            }
        }

        combatDiscipline = style.discipline;
        combatMethod = style.label();

        int spellSprite = spellName != null ? spellbook.spriteFor(spellName) : -1;
        if (spellSprite <= 0) {
            spellSprite = fallbackSprite;
        }
        combatMethodIcon = spellSprite > 0 ? CombatIcons.sprite(spellSprite) : CombatIcons.forStyle(style, weaponId);

        currentStyle = style;
    }

    // Spells cast by hand (not autocast) override the weapon's style for a few ticks after each cast
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC || !"Cast".equals(event.getMenuOption())) {
            return;
        }

        String target = Text.removeTags(event.getMenuTarget());
        int arrow = target.indexOf("->");
        String spell = (arrow >= 0 ? target.substring(0, arrow) : target).trim();
        if (spell.isEmpty()) {
            return;
        }

        Widget selectedSpell = client.getSelectedWidget();
        manualSpellName = spell;
        manualSpellSpriteId = selectedSpell != null ? selectedSpell.getSpriteId() : -1;
        manualCastTick = client.getTickCount();
    }

    /** Spellbook icon of the Strike spell for an element, as shown in the player's own spellbook. */
    // The element's basic Strike spell icon (standard spellbook), whatever spellbook you're on
    public static CombatIcons.Icon elementSpellIcon(MonsterWeakness.Element element) {
        switch (element) {
            case AIR: return CombatIcons.sprite(SpriteID.Magicon.WIND_STRIKE);
            case WATER: return CombatIcons.sprite(SpriteID.Magicon.WATER_STRIKE);
            case EARTH: return CombatIcons.sprite(SpriteID.Magicon.EARTH_STRIKE);
            case FIRE: return CombatIcons.sprite(SpriteID.Magicon.FIRE_STRIKE);
            default: return CombatIcons.spell(element, null);
        }
    }

    private int equippedWeaponId() {
        ItemContainer equipment = client.getItemContainer(CONTAINER_EQUIPMENT);
        if (equipment == null) {
            return -1;
        }
        Item weapon = equipment.getItem(EQUIPMENT_WEAPON_SLOT);
        return weapon != null ? weapon.getId() : -1;
    }

    // Green = your style hits the weakness, red = it doesn't, white = can't tell
    // (weakness unknown, or the ammo type / spell element couldn't be detected).
    public static Color weaknessMatchColor(WeaponStyles.Style style, MonsterWeakness weakness) {
        Color match = BradsMultiToolOverlay.GOOD;
        Color mismatch = BradsMultiToolOverlay.BAD;
        Color unsure = Color.WHITE;

        if (weakness.type == MonsterWeakness.Type.UNKNOWN) {
            return unsure;
        }
        if (weakness.type != style.type()) {
            return mismatch;
        }
        if (weakness.type == MonsterWeakness.Type.MAGIC) {
            if (weakness.element == null) {
                return match;
            }
            if (style.element == null) {
                return unsure;
            }
            return weakness.element == style.element ? match : mismatch;
        }
        if (weakness.subType == null) {
            return match;
        }
        if (style.damageType == null) {
            return unsure;
        }
        return weakness.subType.equalsIgnoreCase(style.damageType) ? match : mismatch;
    }
}
