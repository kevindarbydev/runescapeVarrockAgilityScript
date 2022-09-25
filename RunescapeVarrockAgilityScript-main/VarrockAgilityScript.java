import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;

import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.dialogues.Dialogues;
import org.dreambot.api.methods.input.Camera;
import org.dreambot.api.methods.interactive.GameObjects;
import org.dreambot.api.methods.item.GroundItems;
import org.dreambot.api.methods.map.Area;
import org.dreambot.api.methods.skills.Skill;
import org.dreambot.api.methods.skills.SkillTracker;
import org.dreambot.api.methods.skills.Skills;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManager;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.script.listener.ChatListener;
import org.dreambot.api.wrappers.interactive.GameObject;
import org.dreambot.api.wrappers.interactive.Player;
import org.dreambot.api.wrappers.items.GroundItem;
import org.dreambot.api.wrappers.widgets.message.Message;

import static org.dreambot.api.methods.tabs.Tabs.logout;


//2.0 Changes
// Should loot marks of grace more reliably (method for checking coords of mark -- should prevent trying to loot marks
// on previous obstacle)
// Implemented a slow mode that activates every second lap, sleep for 3-30s at various times throughout the course
//Still needs to be started in front of the course start area
//


@ScriptManifest(author = "Brotato", category = Category.AGILITY, description = "Varrock Agility Course", name = "Varrock Agility", version = 1.0)
public final class BrotatoVarrock extends AbstractScript implements ChatListener {

    // --__--__--__--__--__--__--__--__--__--__--__--__--__--
    // __--Filters and variables_--__--__--__--__--__--__--__
    // --__--__--__--__--__--__--__--__--__--__--__--__--__--

    private final Area startArea = new Area(3221, 3419, 3225, 3410);
    //   private final Area longRoofArea =  new Area(3222, 3401, 3227, 3395, 3);
    private boolean slowMode;
    private int marksCollected;

    private int oldAgilityExp = 0;
    private int lapsCompleted;
    private int maxLapsCondition = 15;
    private long startTime = 0;

    @Override
    public void onStart() {
        doActionsOnStart();
    }

    @Override
    public void onExit() {
        doActionsOnExit();
    }

    @Override
    public int onLoop() {
        performLoopActions();
        return nextInt(60, 75);
    }

    @Override
    public void onPaint(Graphics g) {
        g.setColor(Color.RED);
        g.setFont(new Font("Arial", Font.BOLD, 15));
        g.drawString("Laps Completed: " + lapsCompleted, 12, 300);
        g.drawString("Run time: " + getElapsedTimeAsString(), 12, 240);
        g.drawString("Agility Lvl: " + Skills.getBoostedLevels(Skill.AGILITY), 12, 220);
        g.drawString("Exp/Hr: " + SkillTracker.getGainedExperiencePerHour(Skill.AGILITY), 12, 200);
        g.drawString("Time Till Level: " + makeTimeString(SkillTracker.getTimeToLevel(Skill.AGILITY)), 12, 180);
        g.drawString("Marks Collected: " + marksCollected, 12, 160);


    }


    @Override
    public void onPlayerMessage(Message msg) {
        handlePlayerMessage(msg);
    }

    @Override
    public void onMessage(Message msg) {
        handleGameMessages(msg);
    }


    private void doActionsOnStart() {
        startTime = System.currentTimeMillis();
        SkillTracker.start(Skill.AGILITY);
        oldAgilityExp = Skills.getExperience(Skill.AGILITY);
        Walking.setRunThreshold(nextInt(81, 92));
    }

    private void doActionsOnExit() {
        log(String.format("Gained agility xp: %d", (Skills.getExperience(Skill.AGILITY) - oldAgilityExp)));
        log("Runtime: " + getElapsedTimeAsString());
    }

    private void performLoopActions() {

        if (ScriptManager.getScriptManager().isRunning() && Client.isLoggedIn()) {
            slowMode();
            checkLapCondition();
            handleDialogues();
            checkIfWeFell();
            climbRoughWallToStart();
            clothesLine();
            leapGap();
            balanceWall();
            jumpGapAfterWall();
            jumpGapOverStreet();
            jumpOffPub();
            hurdleLedge();
            lastObstacle();
        }
    }

    // --__--__--__--__--__--__--__--__--__--__--__--__--__--
    // __--Helper functions__--__--__--__--__--__--__--__--__
    // --__--__--__--__--__--__--__--__--__--__--__--__--__--

    private void handleDialogues() {
        if (Dialogues.inDialogue()) { // see https://dreambot.org/javadocs/org/dreambot/api/methods/dialogues/Dialogues.html
            for (int i = 0; i < 4; i++) {
                if (Dialogues.canContinue()) { //
                    Dialogues.continueDialogue(); //
                    sleep(nextInt(500, 750)); //
                } else {
                    break; //break out of loop, if no more dialogues
                }
            }
        }
    }

    private void checkLapCondition() {
        if (lapsCompleted >= maxLapsCondition) {
            log("Max reasonable humanlike playtime reached -- logging out.");
            stop();
            logout();
        }
    }

    private String getElapsedTimeAsString() {
        return makeTimeString(getElapsedTime()); //make a formatted string from a long value
    }

    private long getElapsedTime() {
        return System.currentTimeMillis() - startTime; //return elapsed millis since start of script
    }

    private String makeTimeString(long ms) {
        final int seconds = (int) (ms / 1000) % 60;
        final int minutes = (int) ((ms / (1000 * 60)) % 60);
        final int hours = (int) ((ms / (1000 * 60 * 60)) % 24);
        final int days = (int) ((ms / (1000 * 60 * 60 * 24)) % 7);
        final int weeks = (int) (ms / (1000 * 60 * 60 * 24 * 7));
        if (weeks > 0) {
            return String.format("%02dw %03dd %02dh %02dm %02ds", weeks, days, hours, minutes, seconds);
        }
        if (weeks == 0 && days > 0) {
            return String.format("%03dd %02dh %02dm %02ds", days, hours, minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours > 0) {
            return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes > 0) {
            return String.format("%02dm %02ds", minutes, seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes == 0) {
            return String.format("%02ds", seconds);
        }
        if (weeks == 0 && days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            return String.format("%04dms", ms);
        }
        return "00";
    }

    private void handlePlayerMessage(Message msg) {
        log(String.format("%d, %d", msg.getTime(), msg.getTypeID()));
    }

    private void handleGameMessages(Message msg) {
        log(msg);
    }

    private void slowMode() {
        slowMode = (lapsCompleted % 2 == 0 && lapsCompleted != 0);
    }


    private int nextInt(int lowValIncluded, int highValExcluded) { //get a random value between a range, high end is not included
        return ThreadLocalRandom.current().nextInt(lowValIncluded, highValExcluded);
    }

    private Player player() { //get the local player, less typing
        return getLocalPlayer();
    }

    private int playerX() { //get player x location
        return player().getX();
    }

    private int playerY() { //get player y location
        return player().getY();
    }

    private int playerZ() { //get player z location
        return player().getZ();
    }

    private boolean isMoving() { //true if player is moving
        return player().isMoving();
    }

    private boolean isAnimating() {
        return player().isAnimating();
    }

    private boolean atStartArea() { //area before the agility log
        return startArea.contains(player());
    }


    private void checkForMarks() {
        if (Inventory.isFull()) {
            log("Full inventory -- please fix");
        }
        final GroundItem mark = GroundItems.closest("Mark of Grace");
        if (mark != null) {
            log("Found mark of grace -- sleeping briefly to ensure we loot it");
            sleep(nextInt(1500, 3000));
            if (Walking.canWalk(mark))
                if (mark.interact("Take")) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(() -> false, nextInt(500, 1000));
                    marksCollected++;
                }
        }
    }


    private void checkIfWeFell() {
        if (playerZ() == 0 && !atStartArea()) {
            log("We fell ... attempting to restart course...");
            Walking.walk(startArea);
            sleepUntil(
                    () -> (player().distance(Walking.getDestination()) <= nextInt(3, 5)),
                    () -> isMoving(),
                    nextInt(3600, 4000), //timer duration
                    nextInt(320, 480)); //every time, poll timer is up, check reset condition. If true, then reset timer duration
        }
    }

    private void climbRoughWallToStart() {
        if (atStartArea()) {
            log("At start area -- beginning course...");
            final GameObject rWall = GameObjects.closest(14412);
            if (rWall != null) {
                if (rWall.distance() > 9) {
                    Walking.walk(rWall);
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));

                    sleepUntil(
                            () -> (player().distance(Walking.getDestination()) <= nextInt(3, 5)),
                            () -> isMoving(),
                            nextInt(3600, 4000),
                            nextInt(320, 480));
                }
                if (rWall.interact()) {
                    sleepUntil(
                            () -> playerZ() == 3,
                            () -> isMoving(),
                            nextInt(1000, 2000),
                            nextInt(320, 480)
                    );
                }
                if (slowMode) {
                    sleep(nextInt(6000, 10000)); // Every 2nd lap, take longer breaks between obstacles
                }
            }
        }
    }

    private void clothesLine() {
        if (playerZ() == 3 && playerY() == 3414 && playerX() == 3219) {
            if (slowMode) {
                log("slow mode true -- sleeping");
                sleep(nextInt(2000, 3500));
            }
            checkForMarks();
            final GameObject cLine = GameObjects.closest(14413);
            if (cLine != null) {
                if (cLine.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));

                    sleepUntil(
                            () -> playerY() == 3414,
                            () -> isMoving(),
                            nextInt(1000, 2000),
                            nextInt(320, 480)
                    );
                }
            }
        }
    }

    private void leapGap() {
        if (playerZ() == 3 && playerY() == 3414 && playerX() == 3208) {
            checkForMarks();


            final GameObject firstGap = GameObjects.closest(14414);
            if (firstGap != null) {
                if (firstGap.interact()) {
                    
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(
                            
                            () -> playerX() == 3197,
                            () -> isMoving(),
                            nextInt(1000, 2000),
                            nextInt(320, 480)
                    );
                }
                if (slowMode) {
                    log("slow mode true -- sleeping");
                    sleep(nextInt(3000, 22000)); // Every 2nd lap, take longer breaks between obstacles
                }
            }
        }
    }

    private void balanceWall() {
        if (playerZ() == 1 && playerY() == 3416 && playerX() == 3197) {
            checkForMarks();

            if (slowMode) {
                log("slow mode true -- sleeping");
                sleep(nextInt(1500, 3000));
            }
            final GameObject wall = GameObjects.closest(14832);
            if (wall != null) {

                if (wall.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    
                    sleepUntil(
                            () -> playerZ() == 3,
                            () -> isMoving(),
                            nextInt(4000, 7000),
                            nextInt(320, 480)
                    );
                }
            }
        }
    }

    private void jumpGapAfterWall() {
        if (playerZ() == 3 && playerX() == 3192 && playerY() == 3406) {
            checkForMarks();

            final GameObject longGap = GameObjects.closest(14833);
            if (longGap != null) {
                if (longGap.interact()) {
                    sleepUntil(() -> (playerY() == 3398 && playerX() == 3193 && playerY() == 3), nextInt(3000, 3500));
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));

                }
            }
        }

    }

    private void jumpGapOverStreet() {
        if (playerZ() == 3 && playerY() == 3398 && playerX() == 3193) {
            checkForMarks();

            final GameObject streetGap = GameObjects.closest(14834);
            if (streetGap != null) {

                if (streetGap.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(
                            
                            () -> playerX() == 3218, //we succeeded
                            () -> (isMoving() || isAnimating()),
                            nextInt(1000, 2000),
                            nextInt(320, 480)
                    );
                }
                if (slowMode) {
                    log("slow mode true -- sleeping");
                    sleep(nextInt(2000, 3500)); // Every 2nd lap, take longer breaks between obstacles
                }
            }
        }
    }

    private void jumpOffPub() {
        if (playerZ() == 3 && playerX() == 3218 && playerY() == 3399) {
            checkForMarks();        
            final GameObject cornerGap = GameObjects.closest(14835);
            if (cornerGap != null) {
                if (cornerGap.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(() -> (playerX() == 3236 && playerY() == 3403), nextInt(500, 1000)
                    );
                }
            }
        }
    }

    private void hurdleLedge() {
        if (playerZ() == 3 && playerX() == 3236 && playerY() == 3403) {
            checkForMarks();
            final GameObject ledge = GameObjects.closest(14836);
            if (ledge != null) {
                if (ledge.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(() -> (playerX() == 3236 && playerY() == 3410), nextInt(500, 1000));
                }
                if (slowMode) {
                    log("slow mode true -- sleeping");
                    sleep(nextInt(4000, 10000)); // Every 2nd lap, take longer breaks between obstacles
                }
            }
        }
    }

    public void lastObstacle() {
        if (playerZ() == 3 && playerY() == 3410 && playerX() == 3236) {
            checkForMarks();
            final GameObject edge = GameObjects.closest(14841);
            if (edge != null) {
                if (edge.interact()) {
                    sleepUntil(() -> isMoving(), nextInt(500, 1000));
                    sleepUntil(() -> playerZ() == 0, nextInt(600, 1800));
                    lapsCompleted++;
                }
            }
        }
    }
}
