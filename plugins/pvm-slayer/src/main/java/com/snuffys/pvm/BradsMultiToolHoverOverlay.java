package com.snuffys.pvm;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.util.function.BiPredicate;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.Text;

/**
 * Tooltip-style card next to the cursor when hovering an attackable monster: level (coloured like the
 * game's right-click menu), health, weakness, your style against it, its attack style and aggression.
 */
public class BradsMultiToolHoverOverlay extends Overlay {

    @Inject
    private CombatStyle combatStyle;

    private static final int WIDTH = 205;

    private final Client client;
    private final PvmTracker plugin;
    private final PvmConfig config;
    private final ItemManager itemManager;
    private final SpriteManager spriteManager;
    private volatile BiPredicate<NPC, Boolean> skillingCards;
    @Inject
    private SlayerMonsterInfo slayerInfo;
    @Inject
    private PluginFamily family;
    private final PanelComponent panel = new PanelComponent();

    @Inject
    public BradsMultiToolHoverOverlay(Client client, PvmTracker plugin, PvmConfig config,
                                      ItemManager itemManager, SpriteManager spriteManager) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        this.itemManager = itemManager;
        this.spriteManager = spriteManager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setPriority(PRIORITY_HIGHEST);
        panel.setPreferredSize(new Dimension(WIDTH, 0));
    }

    /** Which NPCs the skilling card shows instead (given whether Attack is the left-click option), or null. */
    void setSkillingCards(BiPredicate<NPC, Boolean> covers) {
        skillingCards = covers;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showHoverInfo() || client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()) {
            return null;
        }

        NPC npc = hoveredNpc();
        if (npc == null || !plugin.isAttackable(npc) || GemstoneCrabs.isGemstoneCrab(npc) || isAlreadyShownByPvmHud(npc)) {
            return null;
        }

        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(FontManager.getRunescapeSmallFont());
        buildPanel(npc);

        CursorTooltip.render(client, graphics, panel);
        return null;
    }

    /**
     * The NPC of the left-click option (what the game's top-left tooltip names), or null if that option
     * isn't on an NPC, or when the Skilling Helper's skilling card shows it instead (a pickpocket target,
     * a sea monster): joined directly in the Toolbelt, or told through PluginFamily when both run separately.
     */
    private NPC hoveredNpc() {
        MenuEntry[] entries = client.getMenu().getMenuEntries();
        if (entries.length == 0) {
            return null;
        }
        MenuEntry top = entries[entries.length - 1];
        NPC npc = top.getNpc();
        if (npc == null) {
            return null;
        }
        boolean attack = "attack".equalsIgnoreCase(Text.removeTags(top.getOption()));
        BiPredicate<NPC, Boolean> skilling = skillingCards;
        boolean skillingCard = skilling != null ? skilling.test(npc, attack) : family.skillingCardShows(npc.getIndex());
        return skillingCard ? null : npc;
    }

    // The PvM HUD already covers the monster being fought, and others of the same kind
    private boolean isAlreadyShownByPvmHud(NPC npc) {
        if (!config.enablePvmOverlay() || !plugin.isPvmOverlayActive()) {
            return false;
        }
        NPC target = plugin.getCurrentPvmTarget();
        if (target != null && (target == npc || target.getId() == npc.getId())) {
            return true;
        }
        return npc.getName() != null && npc.getName().equalsIgnoreCase(plugin.getLastPvmTargetName());
    }

    private void buildPanel(NPC npc) {
        panel.getChildren().clear();
        MonsterData.Stats stats = plugin.statsFor(npc);
        MonsterWeakness weakness = plugin.weaknessFor(npc);

        int level = npc.getCombatLevel();
        panel.getChildren().add(TitleComponent.builder()
            .text(npc.getName() + (level > 0 ? " (Lvl " + level + ")" : ""))
            .color(levelColor(level))
            .build());

        int maxHp = stats != null ? stats.hitpoints : 0;
        int ratio = npc.getHealthRatio();
        int scale = npc.getHealthScale();
        int percent = ratio >= 0 && scale > 0 ? ratio * 100 / scale : 100;
        int currentHp = maxHp > 0 ? (int) Math.ceil(percent * maxHp / 100.0) : 0;
        panel.getChildren().add(new CenteredHealthBarComponent(percent, currentHp, maxHp));

        panel.getChildren().add(new IconLineComponent("Weakness:", icon(
            weakness.type == MonsterWeakness.Type.MAGIC && weakness.element != null
                ? CombatStyle.elementSpellIcon(weakness.element)
                : CombatIcons.forWeakness(weakness)),
            weakness.label(), weaknessColor(weakness)));

        if (weakness.hasSeparateElement()) {
            panel.getChildren().add(new IconLineComponent("Elemental:",
                icon(CombatStyle.elementSpellIcon(weakness.element)), weakness.elementLabel(), Color.WHITE));
        }

        if (stats != null && !stats.attackStyles.isEmpty()) {
            panel.getChildren().add(new IconLineComponent("Attacks With:",
                icon(CombatIcons.forAttackStyle(stats.attackStyles.get(0))),
                String.join(", ", stats.attackStyles), Color.WHITE));
        }

        // To you specifically: your combat level, the Wilderness and god items / vyre clothing / greegrees
        Aggression.Verdict aggro = plugin.aggressionFor(npc);
        panel.getChildren().add(LineComponent.builder()
            .left("Aggressive:")
            .right(aggro.text)
            .rightColor(aggro.state == Aggression.State.YES ? HudColors.WARN
                : aggro.state == Aggression.State.NO ? HudColors.GOOD : Color.WHITE)
            .build());

        if (config.showSlayerMonsterInfo()) {
            slayerInfo.addTo(panel, npc, stats);
        }
    }

    // Green/red when your current style does/doesn't hit the weakness, white when it can't be told
    private Color weaknessColor(MonsterWeakness weakness) {
        WeaponStyles.Style style = combatStyle.getCurrentStyle();
        return style != null ? CombatStyle.weaknessMatchColor(style, weakness) : Color.WHITE;
    }

    private Color levelColor(int npcLevel) {
        Player local = client.getLocalPlayer();
        return local != null ? BradsMultiToolOverlay.combatLevelColor(local.getCombatLevel(), npcLevel) : Color.WHITE;
    }

    private Image icon(CombatIcons.Icon icon) {
        if (icon == null) {
            return null;
        }
        return icon.sprite ? spriteManager.getSprite(icon.id, 0) : itemManager.getImage(icon.id);
    }
}
