package com.bradsmultitool;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

public class AggressionTest {

    private static final MonsterData DATA = new MonsterData();
    private static final List<String> NOTHING = Collections.emptyList();

    @BeforeClass
    public static void load() {
        DATA.load(new Gson());
    }

    private static MonsterData.Stats byNote(String part) {
        for (MonsterData.Stats stats : DATA.allStats()) {
            if (stats.aggroNote != null && stats.aggroNote.contains(part)) {
                return stats;
            }
        }
        throw new AssertionError("no monster with note " + part);
    }

    @Test
    public void levelRule() {
        MonsterData.Stats plain = null;
        for (MonsterData.Stats stats : DATA.allStats()) {
            if (stats.aggressive && stats.aggroNote == null) {
                plain = stats;
                break;
            }
        }
        // Level 28 monster: attacks up to level 56, ignores 57+
        assertEquals(Aggression.State.YES, Aggression.of(plain, 28, 56, false, NOTHING).state);
        assertEquals("No (lvl 57+)", Aggression.of(plain, 28, 57, false, NOTHING).text);
        assertEquals(Aggression.State.YES, Aggression.of(plain, 28, 126, true, NOTHING).state);
    }

    @Test
    public void godItemsProtectRegardlessOfLevel() {
        MonsterData.Stats bandosFollower = byNote("Bandos-affiliated");
        assertEquals(Aggression.State.YES, Aggression.of(bandosFollower, 20, 126, false, NOTHING).state);
        assertEquals("No (Bandos item)",
            Aggression.of(bandosFollower, 20, 126, false, Arrays.asList("bandos chestplate")).text);
    }

    @Test
    public void vyreClothingNeedsTheFullSet() {
        MonsterData.Stats vyre = byNote("Vyre noble");
        List<String> set = Arrays.asList("vyre noble top", "vyre noble legs", "vyre noble shoes");
        assertEquals(Aggression.State.NO, Aggression.of(vyre, 100, 100, false, set).state);
        assertEquals(Aggression.State.YES, Aggression.of(vyre, 100, 100, false, set.subList(0, 2)).state);
    }

    @Test
    public void uncheckableConditionsShowTheWikiText() {
        MonsterData.Stats stats = byNote("if wielding a weapon");
        Aggression.Verdict verdict = Aggression.of(stats, 10, 10, false, NOTHING);
        assertEquals(Aggression.State.MAYBE, verdict.state);
        assertEquals(stats.aggroNote, verdict.text);
    }
}
