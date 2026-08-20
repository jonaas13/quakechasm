/*
 * Quakechasm, a Quake minigame plugin for Minecraft servers running PaperMC
 * 
 * Copyright (C) 2024-present Polyzium
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.polyzium.quakechasm.matchmaking.matches;

import com.github.polyzium.quakechasm.misc.TableBuilder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import com.github.polyzium.quakechasm.PluginConfig;
import com.github.polyzium.quakechasm.QuakePlugin;
import com.github.polyzium.quakechasm.QuakeUserState;
import com.github.polyzium.quakechasm.game.combat.DamageCause;
import com.github.polyzium.quakechasm.matchmaking.Team;
import com.github.polyzium.quakechasm.matchmaking.map.QMap;
import com.github.polyzium.quakechasm.misc.Pair;
import com.github.polyzium.quakechasm.misc.TranslationManager;

import java.time.Duration;
import java.util.*;

public class FFAMatch extends Match {
    @QManageable(name = "fraglimit", min = 1, max = 1000, description = "Number of frags needed to win")
    public int fraglimit = 10;
    
    @QManageable(name = "needPlayers", min = 1, max = 100, description = "Number of players needed to start")
    private int needPlayers = 2;
    
    private HashMap<Player, Integer> scores = new HashMap<>();
    private boolean started = false;
    private BukkitTask warmupTask = null;
    private int warmupSeconds = -1;
    
    public FFAMatch(QMap map) {
        super(map);
    }
    
    public FFAMatch(QMap map, UUID ownerId, MatchPrivacy privacy, String password) {
        super(map, ownerId, privacy, password);
    }

    public static String getNameKeyStatic() {
        return "match.ffa.name";
    }
    public String getNameKey() {
        return getNameKeyStatic();
    }

    @Override
    public void setScoreLimit(int scoreLimit) {
        this.fraglimit = scoreLimit;
    }

    @Override
    public void setNeedPlayers(int needPlayers) {
        this.needPlayers = needPlayers;
    }

    @Override
    public boolean hasStarted() {
        return started;
    }

    @Override
    public boolean startNow() {
        if (started || matchEnding || players.isEmpty()) {
            return false;
        }

        if (warmupTask != null) {
            warmupTask.cancel();
            warmupTask = null;
        }
        start();
        return true;
    }

    @Override
    protected boolean hasWarmupPhase() {
        return true;
    }

    @Override
    public void join(Player player, Team team) {
        super.join(player, team);
        scores.put(player, 0);

        this.updateScoreboard();

        //noinspection SizeReplaceableByIsEmpty
        if (players.size() >= needPlayers && this.warmupTask == null && !started) {
            warmup();
        }
    }

    @Override
    public void leave(Player player) {
        super.leave(player);
        scores.remove(player);
        stopWarmupIfNeeded();
        this.updateScoreboard();
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        if (QuakePlugin.INSTANCE.matchmakingService != null) {
            QuakePlugin.INSTANCE.matchmakingService.autoJoinPendingPlayers();
        }

        if (players.isEmpty()) {
            QuakePlugin.INSTANCE.getLogger().warning("Last player of match "+this.getNameKey()+", "+map.name+" has left. Ending match.");
            this.end();
        }
    }

    @Override
    public List<Team> allowedTeams() {
        return List.of(Team.FREE);
    }

    public void warmup() {
        Set<Player> players = this.players.keySet();
        this.warmupTask = new BukkitRunnable() {
            int count = 10;
            @Override
            public void run() {
                warmupSeconds = count;
                updateScoreboard();

                for (Player player : players) {
                    player.showTitle(Title.title(
                            TranslationManager.t(getNameKey(), player),
                            TranslationManager.t("match.countdown", player, Placeholder.unparsed("count", String.valueOf(count))),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ZERO)
                    ));
                }

                count--;

                if (count == -1) {
                    warmupSeconds = -1;
                    warmupTask = null;
                    start();
                    cancel();
                }
            }
        }.runTaskTimer(QuakePlugin.INSTANCE, 0, 20);
    }

    public void start() {
        map.prepareForMatch(this);

        for (Player player : new ArrayList<>(players.keySet())) {
            scores.put(player, 0);

            QuakeUserState userState = QuakePlugin.INSTANCE.userStates.get(player);
            if (userState == null || userState.currentMatch != this) {
                continue;
            }
            userState.reset();
            userState.respawn();
        }

        started = true;
        warmupSeconds = -1;
        startMatchTimer();

        this.updateScoreboard();
        for (Player player : this.players.keySet()) {
            player.showTitle(Title.title(
                    TranslationManager.t("match.start", player),
                    TranslationManager.t("match.generic.startMessage", player, Placeholder.unparsed("fraglimit", String.valueOf(fraglimit))).color(TextColor.color(0xff0000)),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))
            ));
        }
    }

    public void end() {
        super.end();
    }

    @Override
    public Team assignTeam(Player player) {
        return Team.FREE;
    }

    private Component getScoreboard() {
        TableBuilder tableBuilder = new TableBuilder();

        Component scoreboard = Component.empty();

        List<Map.Entry<Player, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        tableBuilder.addRow("Score", "Ping", "Name");
        tableBuilder.addRow("", "", "");

        for (Map.Entry<Player, Integer> scoreEntry : sortedScores) {
            tableBuilder.addRow(scoreEntry.getValue().toString(), String.valueOf(scoreEntry.getKey().getPing()), scoreEntry.getKey().getName());
        }

        scoreboard = scoreboard.append(Component.text(tableBuilder.build()).font(Key.key("mono")));

        return scoreboard;
    }

    private void updateScoreboard() {
        this.updateSidebar(getSidebarLines());

        for (Player player : players.keySet()) {
            Component place = getPlaceComponent(player);
            place = place.append(TranslationManager.t("match.score.place", player, Placeholder.unparsed("score", String.valueOf(scores.get(player)))).color(TextColor.color(0xFFFFFF))).appendNewline();
            player.sendPlayerListHeaderAndFooter(place, this.getScoreboard());
        }
    }

    private List<Component> getSidebarLines() {
        PluginConfig.ScoreboardGuiConfig config = scoreboardConfig();
        ArrayList<Component> lines = new ArrayList<>();
        if (config.showMode) {
            lines.add(sidebarLine("scoreboard.mode",
                    Placeholder.unparsed("mode", TranslationManager.tLegacy(getNameKey(), TranslationManager.FALLBACK))));
        }
        if (config.showMap) {
            lines.add(sidebarLine("scoreboard.map",
                    Placeholder.unparsed("map_name", map.getDisplayName())));
        }

        if (!started) {
            if (warmupTask == null) {
                lines.add(sidebarLine("scoreboard.waiting.title"));
                if (config.splitWaitingPlayers) {
                    lines.add(sidebarLine("scoreboard.waiting.playersCurrent",
                            Placeholder.unparsed("current", String.valueOf(players.size())),
                            Placeholder.unparsed("needed", String.valueOf(needPlayers))));
                    lines.add(sidebarLine("scoreboard.waiting.playersNeeded"));
                } else {
                    lines.add(sidebarLine("scoreboard.waiting.players",
                            Placeholder.unparsed("current", String.valueOf(players.size())),
                            Placeholder.unparsed("needed", String.valueOf(needPlayers))));
                }
            } else {
                lines.add(sidebarLine("scoreboard.waiting.warmup"));
                lines.add(sidebarLine("scoreboard.waiting.countdown",
                        Placeholder.unparsed("seconds", String.valueOf(Math.max(0, warmupSeconds)))));
            }
            return lines;
        }

        if (config.showScoreLimit) {
            lines.add(sidebarLine("scoreboard.limit.frags",
                    Placeholder.unparsed("limit", String.valueOf(fraglimit))));
        }
        if (hasTimeLimit()) {
            lines.add(sidebarLine("scoreboard.limit.time",
                    Placeholder.unparsed("time", getFormattedTimeRemaining())));
        }
        lines.add(sidebarLine("scoreboard.players.title"));

        List<Map.Entry<Player, Integer>> sortedScores = new ArrayList<>(scores.entrySet());
        sortedScores.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        int place = 1;
        for (Map.Entry<Player, Integer> scoreEntry : sortedScores) {
            if (place > config.maxPlayerRows) break;
            lines.add(sidebarLine("scoreboard.players.entry",
                    Placeholder.unparsed("place", String.valueOf(place)),
                    Placeholder.unparsed("player_name", scoreEntry.getKey().getName()),
                    Placeholder.unparsed("score", scoreEntry.getValue().toString())));
            place++;
        }

        return lines;
    }

    private void stopWarmupIfNeeded() {
        if (started || warmupTask == null || players.size() >= needPlayers) {
            return;
        }

        warmupTask.cancel();
        warmupTask = null;
        warmupSeconds = -1;
    }

    @Override
    protected void onMatchTimerTick() {
        updateScoreboard();
    }

    @Override
    protected void onTimeLimitReached() {
        if (matchEnding) {
            return;
        }

        List<Map.Entry<Player, Integer>> sortedList = new ArrayList<>(scores.entrySet());
        sortedList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        if (sortedList.isEmpty()) {
            end();
            return;
        }

        int winningScore = sortedList.get(0).getValue();
        List<Player> leaders = sortedList.stream()
                .filter(entry -> entry.getValue() == winningScore)
                .map(Map.Entry::getKey)
                .toList();

        for (Player player : this.players.keySet()) {
            if (leaders.size() == 1) {
                player.showTitle(Title.title(
                        TranslationManager.t("match.generic.wins", player,
                                Placeholder.unparsed("winner", leaders.get(0).getName())),
                        TranslationManager.t("match.time.limitReached", player),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
            } else {
                player.showTitle(Title.title(
                        TranslationManager.t("match.time.tied", player),
                        TranslationManager.t("match.time.limitReached", player),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
            }
            player.sendMessage(this.getScoreboard());
        }

        end();
    }

    public Pair<Integer, Boolean> getPlace(Player player) {
        int playerScore = scores.get(player);
        int place = 1;
        boolean isTied = false;

        for (Map.Entry<Player, Integer> entry : scores.entrySet()) {
            if (!entry.getKey().equals(player) && entry.getValue() > playerScore) {
                place++;
            } else if (entry.getValue() == playerScore && !entry.getKey().equals(player)) {
                isTied = true;
            }
        }

        return new Pair<>(place, isTied);
    }

    public Component getPlaceComponent(Player player) {
        Pair<Integer, Boolean> place = getPlace(player);
        String formattedPlace = String.valueOf(place.getLeft());
        TextColor placeColor;
        switch (place.getLeft()) {
            case 1:
                formattedPlace += TranslationManager.tLegacy("suffix.first", player);
                placeColor = TextColor.color(0x0000FF);
                break;
            case 2:
                formattedPlace += TranslationManager.tLegacy("suffix.second", player);
                placeColor = TextColor.color(0xFF0000);
                break;
            case 3:
                formattedPlace += TranslationManager.tLegacy("suffix.third", player);
                placeColor = TextColor.color(0xFFFF00);
                break;
            default:
                formattedPlace += TranslationManager.tLegacy("suffix.nth", player);
                placeColor = TextColor.color(0xFFFFFF);
                break;
        }

        Component placeComponent;
        if (place.getRight()) // tied
            placeComponent = TranslationManager.t("match.score.tiedFor", player).color(TextColor.color(0xFFFFFF)).append(Component.text(formattedPlace).color(placeColor));
        else
            placeComponent = Component.text(formattedPlace).color(placeColor);

        return placeComponent;
    }

    @Override
    public void onDeath(Player victim, Entity attacker, DamageCause cause) {
        super.onDeath(victim, attacker, cause);
        for (Player viewer : this.players.keySet()) {
            viewer.sendMessage(getDeathMessage(victim, attacker, cause, viewer.locale()));
        }

        if (!started) return;

        if (attacker instanceof Player pAttacker && victim != attacker) {
            Integer oldScore = scores.putIfAbsent(pAttacker, 0);
            if (oldScore == null) {
                oldScore = 0;
            }
            scores.put(pAttacker, oldScore+1);

            Component placeComponent = getPlaceComponent(pAttacker);

            pAttacker.showTitle(Title.title(
                    TranslationManager.t("game.kill.message", pAttacker, Placeholder.unparsed("victim", victim.getName())),
                    placeComponent.append(TranslationManager.t("match.score.place", pAttacker, Placeholder.unparsed("score", String.valueOf(scores.get(pAttacker)))).color(TextColor.color(0xFFFFFF))),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
            ));
        } else if (attacker == null || victim == attacker) {
            Integer oldScore = scores.get(victim);
            scores.put(victim, Math.max(0, oldScore - 1));
            victim.playSound(victim, "quake.feedback.score_down", SoundCategory.NEUTRAL, 1, 1);
        }

        this.updateScoreboard();

        List<Map.Entry<Player, Integer>> sortedList = new ArrayList<>(scores.entrySet());
        sortedList.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        // End match if the fraglimit is reached
        Map.Entry<Player, Integer> winningPlayerEntry = sortedList.get(0);
        Player winningPlayer = winningPlayerEntry.getKey();
        int winningScore = winningPlayerEntry.getValue();

        if (winningScore == fraglimit) {
            for (Player player : this.players.keySet()) {
                player.showTitle(Title.title(
                        TranslationManager.t("match.generic.wins", player, Placeholder.unparsed("winner", winningPlayer.getName())),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
                player.sendMessage(this.getScoreboard());
            }

            this.end();
        }
    }
}
