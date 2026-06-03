package com.tonicbox.dukes8bit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;

/**
 * Draws a Game's current state to an AWT Graphics. Stateless: every visual is
 * derived from the Game and generated procedurally, so no asset files ship. A
 * camera follows the player so Duke stays centered while the dungeon scrolls.
 * Colors live as constants so no Color objects are allocated while rendering.
 */
final class Renderer {

    private static final Color BACKGROUND = new Color(8, 8, 12);
    private static final Color FLOOR_LIT = new Color(64, 60, 78);
    private static final Color FLOOR_DIM = new Color(26, 24, 32);
    private static final Color WALL_LIT = new Color(112, 106, 142);
    private static final Color WALL_DIM = new Color(46, 44, 60);
    private static final Color WALL_EDGE = new Color(150, 144, 184);
    private static final Color STAIRS_LIT = new Color(236, 206, 92);
    private static final Color STAIRS_DIM = new Color(92, 80, 42);
    private static final Color UP_STAIRS_LIT = new Color(228, 84, 84);
    private static final Color UP_STAIRS_DIM = new Color(96, 40, 40);
    private static final Color HUD_BACKGROUND = new Color(18, 18, 26);
    private static final Color HUD_TEXT = Color.WHITE;
    private static final Color HUD_HINT = new Color(150, 150, 172);
    private static final Color HP_BACK = new Color(58, 22, 22);
    private static final Color HP_FILL = new Color(210, 64, 64);
    private static final Color XP_BACK = new Color(24, 28, 46);
    private static final Color XP_FILL = new Color(96, 140, 230);
    private static final Color DUKE_BODY = new Color(60, 92, 212);
    private static final Color DUKE_NOSE = new Color(232, 44, 44);
    private static final Color BUG_COLOR = new Color(214, 70, 64);
    private static final Color NULL_COLOR = new Color(158, 96, 214);
    private static final Color LEAK_COLOR = new Color(92, 192, 112);
    private static final Color FORK_COLOR = new Color(232, 150, 44);
    private static final Color DEADLOCK_COLOR = new Color(120, 122, 136);
    private static final Color ENEMY_EYE = new Color(20, 16, 24);
    private static final Color OVERLAY = new Color(0, 0, 0, 178);
    private static final Color SWORD_COLOR = new Color(224, 226, 240);
    private static final Color SWORD_TRAIL_1 = new Color(224, 226, 240, 150);
    private static final Color SWORD_TRAIL_2 = new Color(224, 226, 240, 70);
    private static final BasicStroke SWORD_STROKE = new BasicStroke(3f);
    private static final Color FLOOR_LIT_ALT = new Color(56, 52, 70);
    private static final Color FLOOR_DIM_ALT = new Color(22, 20, 28);
    private static final Color WALL_SHADOW = new Color(30, 28, 40);
    private static final Color DUKE_OUTLINE = new Color(22, 28, 70);
    private static final Color DUKE_BELLY = new Color(236, 239, 246);
    private static final Color DUKE_FOOT = new Color(242, 170, 44);
    private static final Color DUKE_FLIPPER = new Color(40, 60, 150);
    private static final Color BUG_LEG = new Color(120, 32, 28);
    private static final Color LEAK_DRIP = new Color(156, 232, 174);
    private static final Color MERCHANT_ROBE = new Color(150, 110, 60);
    private static final Color MERCHANT_TRIM = new Color(232, 200, 110);
    private static final Color MERCHANT_SKIN = new Color(232, 196, 158);
    private static final Color MERCHANT_HAT = new Color(96, 64, 32);
    private static final Color PROMPT = new Color(245, 240, 210);
    private static final Color TRAP_COLOR = new Color(212, 78, 68);
    private static final Color CHEST_BODY = new Color(150, 96, 40);
    private static final Color CHEST_LID = new Color(220, 174, 72);
    private static final Color LOOT_GEM = new Color(118, 210, 232);

    /**
     * Draws one frame: the camera-relative, fog-aware dungeon, then loot, entities,
     * Duke, the HUD, and any overlay for the current game state.
     */
    void render(Graphics graphics, Game game) {
        graphics.setColor(BACKGROUND);
        graphics.fillRect(0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);

        // Round the camera to whole pixels so the scrolling dungeon stays crisp instead of shimmering.
        int cameraX = Math.round(game.cameraX());
        int cameraY = Math.round(game.cameraY());

        graphics.setClip(0, 0, Game.PLAY_WIDTH, Game.PLAY_HEIGHT);
        int firstX = Math.max(0, cameraX / Game.TILE);
        int firstY = Math.max(0, cameraY / Game.TILE);
        int lastX = Math.min(Game.MAP_WIDTH - 1, (cameraX + Game.PLAY_WIDTH) / Game.TILE);
        int lastY = Math.min(Game.MAP_HEIGHT - 1, (cameraY + Game.PLAY_HEIGHT) / Game.TILE);
        for (int y = firstY; y <= lastY; y++) {
            for (int x = firstX; x <= lastX; x++) {
                int idx = Game.index(x, y);
                if (!game.explored[idx]) {
                    continue;
                }
                int sx = x * Game.TILE - cameraX;
                int sy = y * Game.TILE - cameraY;
                int tile = game.map[idx];
                boolean lit = game.visible[idx];
                drawTile(graphics, sx, sy, tile, lit, ((x + y) & 1) == 1);
                if (tile == Game.TRAP && lit && Math.abs(x - game.playerX) + Math.abs(y - game.playerY) <= 2) {
                    drawTrap(graphics, sx, sy);
                }
            }
        }

        for (int i = 0; i < game.lootCount; i++) {
            if (game.visible[Game.index(game.lootX[i], game.lootY[i])]) {
                int sx = game.lootX[i] * Game.TILE - cameraX;
                int sy = game.lootY[i] * Game.TILE - cameraY;
                if (game.lootChest[i]) {
                    drawChest(graphics, sx, sy);
                } else {
                    drawGem(graphics, sx, sy);
                }
            }
        }

        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                int hop = Math.round(game.enemyHit[i] * 6f);
                drawEnemy(graphics, Math.round(game.enemyRenderPixelX(i)) - cameraX, Math.round(game.enemyRenderPixelY(i)) - cameraY - hop, game.enemyType[i]);
            }
        }

        if (game.merchantX >= 0 && game.visible[Game.index(game.merchantX, game.merchantY)]) {
            int mx = game.merchantX * Game.TILE - cameraX;
            int my = game.merchantY * Game.TILE - cameraY;
            drawMerchant(graphics, mx, my);
            if (game.adjacentToMerchant()) {
                graphics.setColor(PROMPT);
                drawCenteredAt(graphics, "ENTER", mx + Game.TILE / 2, my - 4);
            }
        }

        int dukeX = Math.round(game.renderPixelX()) - cameraX;
        int dukeY = Math.round(game.renderPixelY()) - cameraY;
        drawDuke(graphics, dukeX, dukeY, game.facing);
        if (game.attackProgress < 1f) {
            drawSword(graphics, dukeX + Game.TILE / 2, dukeY + Game.TILE / 2, game.attackProgress);
        }

        graphics.setClip(null);
        drawHud(graphics, game);
        if (game.state == Game.DEAD) {
            drawDeath(graphics, game);
        } else if (game.state == Game.SHOP) {
            drawShop(graphics, game);
        } else if (game.state == Game.PAUSED) {
            drawPause(graphics, game);
        } else if (game.state == Game.INVENTORY) {
            drawInventory(graphics, game);
        }
    }

    private void drawTile(Graphics graphics, int px, int py, int tile, boolean lit, boolean alt) {
        if (tile == Game.WALL) {
            graphics.setColor(lit ? WALL_LIT : WALL_DIM);
            graphics.fillRect(px, py, Game.TILE, Game.TILE);
            if (lit) {
                graphics.setColor(WALL_EDGE);
                graphics.fillRect(px, py, Game.TILE, 3);
                graphics.setColor(WALL_SHADOW);
                graphics.fillRect(px, py + Game.TILE - 3, Game.TILE, 3);
            }
            return;
        }
        if (lit) {
            graphics.setColor(alt ? FLOOR_LIT_ALT : FLOOR_LIT);
        } else {
            graphics.setColor(alt ? FLOOR_DIM_ALT : FLOOR_DIM);
        }
        graphics.fillRect(px, py, Game.TILE, Game.TILE);
        if (tile == Game.DOWN_STAIRS) {
            drawStairs(graphics, px, py, lit ? STAIRS_LIT : STAIRS_DIM);
        } else if (tile == Game.UP_STAIRS) {
            drawStairs(graphics, px, py, lit ? UP_STAIRS_LIT : UP_STAIRS_DIM);
        }
    }

    private void drawStairs(Graphics graphics, int px, int py, Color color) {
        graphics.setColor(color);
        graphics.drawRect(px + 2, py + 2, Game.TILE - 5, Game.TILE - 5);
        int step = Game.TILE / 4;
        for (int i = 0; i < 3; i++) {
            graphics.fillRect(px + 4 + i * step, py + 5 + i * step, Game.TILE - 8 - i * 2 * step, step);
        }
    }

    private void drawTrap(Graphics graphics, int px, int py) {
        graphics.setColor(TRAP_COLOR);
        for (int i = 0; i < 3; i++) {
            int sx = px + 5 + i * 7;
            int[] xs = {sx, sx + 3, sx + 6};
            int[] ys = {py + Game.TILE - 5, py + 8, py + Game.TILE - 5};
            graphics.fillPolygon(xs, ys, 3);
        }
    }

    private void drawChest(Graphics graphics, int px, int py) {
        graphics.setColor(CHEST_BODY);
        graphics.fillRect(px + 5, py + 10, Game.TILE - 10, Game.TILE - 13);
        graphics.setColor(CHEST_LID);
        graphics.fillRect(px + 5, py + 7, Game.TILE - 10, 5);
        graphics.setColor(ENEMY_EYE);
        graphics.fillRect(px + Game.TILE / 2 - 1, py + 11, 2, 4);
    }

    private void drawGem(Graphics graphics, int px, int py) {
        graphics.setColor(LOOT_GEM);
        int cx = px + Game.TILE / 2;
        int cy = py + Game.TILE / 2;
        int[] xs = {cx, cx + 5, cx, cx - 5};
        int[] ys = {cy - 6, cy, cy + 6, cy};
        graphics.fillPolygon(xs, ys, 4);
    }

    /** Draws Duke facing his current travel direction; right reuses the left sprite mirrored. */
    private void drawDuke(Graphics graphics, int px, int py, int facing) {
        switch (facing) {
            case Game.FACE_UP -> drawDukeBack(graphics, px, py);
            case Game.FACE_LEFT -> drawDukeLeft(graphics, px, py);
            case Game.FACE_RIGHT -> drawDukeRight(graphics, px, py);
            default -> drawDukeFront(graphics, px, py);
        }
    }

    private void drawDukeFront(Graphics graphics, int px, int py) {
        graphics.setColor(DUKE_FOOT);
        graphics.fillRect(px + 7, py + 21, 4, 2);
        graphics.fillRect(px + 13, py + 21, 4, 2);
        graphics.setColor(DUKE_FLIPPER);
        graphics.fillRoundRect(px + 3, py + 10, 4, 8, 4, 4);
        graphics.fillRoundRect(px + 17, py + 10, 4, 8, 4, 4);
        graphics.setColor(DUKE_BODY);
        graphics.fillRoundRect(px + 5, py + 2, 14, 20, 11, 11);
        graphics.setColor(DUKE_BELLY);
        graphics.fillOval(px + 8, py + 10, 8, 11);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(px + 7, py + 6, 5, 5);
        graphics.fillOval(px + 12, py + 6, 5, 5);
        graphics.setColor(Color.BLACK);
        graphics.fillOval(px + 9, py + 7, 2, 2);
        graphics.fillOval(px + 14, py + 7, 2, 2);
        graphics.setColor(DUKE_NOSE);
        int[] beakX = {px + 10, px + 14, px + 12};
        int[] beakY = {py + 12, py + 12, py + 15};
        graphics.fillPolygon(beakX, beakY, 3);
        graphics.setColor(DUKE_OUTLINE);
        graphics.drawRoundRect(px + 5, py + 2, 14, 20, 11, 11);
    }

    /** Back view used while walking away: body and flippers with a nape patch, no face. */
    private void drawDukeBack(Graphics graphics, int px, int py) {
        graphics.setColor(DUKE_FOOT);
        graphics.fillRect(px + 7, py + 21, 4, 2);
        graphics.fillRect(px + 13, py + 21, 4, 2);
        graphics.setColor(DUKE_FLIPPER);
        graphics.fillRoundRect(px + 3, py + 10, 4, 8, 4, 4);
        graphics.fillRoundRect(px + 17, py + 10, 4, 8, 4, 4);
        graphics.setColor(DUKE_BODY);
        graphics.fillRoundRect(px + 5, py + 2, 14, 20, 11, 11);
        graphics.setColor(DUKE_FLIPPER);
        graphics.fillRoundRect(px + 9, py + 5, 6, 8, 5, 5);
        graphics.setColor(DUKE_OUTLINE);
        graphics.drawRoundRect(px + 5, py + 2, 14, 20, 11, 11);
    }

    /** Left-facing profile: belly, eye, and beak turned to the side. */
    private void drawDukeLeft(Graphics graphics, int px, int py) {
        graphics.setColor(DUKE_FOOT);
        graphics.fillRect(px + 8, py + 21, 4, 2);
        graphics.fillRect(px + 13, py + 21, 3, 2);
        graphics.setColor(DUKE_BODY);
        graphics.fillRoundRect(px + 5, py + 2, 14, 20, 11, 11);
        graphics.setColor(DUKE_BELLY);
        graphics.fillOval(px + 6, py + 10, 8, 11);
        graphics.setColor(Color.WHITE);
        graphics.fillOval(px + 7, py + 6, 5, 5);
        graphics.setColor(Color.BLACK);
        graphics.fillOval(px + 8, py + 8, 2, 2);
        graphics.setColor(DUKE_NOSE);
        int[] beakX = {px + 4, px + 9, px + 9};
        int[] beakY = {py + 11, py + 9, py + 13};
        graphics.fillPolygon(beakX, beakY, 3);
        graphics.setColor(DUKE_FLIPPER);
        graphics.fillRoundRect(px + 7, py + 13, 4, 7, 4, 4);
        graphics.setColor(DUKE_OUTLINE);
        graphics.drawRoundRect(px + 5, py + 2, 14, 20, 11, 11);
    }

    /** Right-facing profile: the left sprite mirrored about the tile's vertical center. */
    private void drawDukeRight(Graphics graphics, int px, int py) {
        Graphics2D g2 = (Graphics2D) graphics;
        AffineTransform previous = g2.getTransform();
        g2.translate(2 * px + Game.TILE, 0);
        g2.scale(-1, 1);
        drawDukeLeft(g2, px, py);
        g2.setTransform(previous);
    }

    private void drawSword(Graphics graphics, int centerX, int centerY, float progress) {
        Graphics2D g2 = (Graphics2D) graphics;
        Stroke previousStroke = g2.getStroke();
        g2.setStroke(SWORD_STROKE);
        double angle = progress * Math.PI * 2;
        drawBlade(g2, centerX, centerY, angle - 0.7, SWORD_TRAIL_2);
        drawBlade(g2, centerX, centerY, angle - 0.35, SWORD_TRAIL_1);
        drawBlade(g2, centerX, centerY, angle, SWORD_COLOR);
        g2.setStroke(previousStroke);
    }

    private void drawBlade(Graphics2D g2, int centerX, int centerY, double angle, Color color) {
        int hiltX = centerX + (int) (Math.cos(angle) * ((double) Game.TILE / 3));
        int hiltY = centerY + (int) (Math.sin(angle) * ((double) Game.TILE / 3));
        int tipX = centerX + (int) (Math.cos(angle) * Game.TILE);
        int tipY = centerY + (int) (Math.sin(angle) * Game.TILE);
        g2.setColor(color);
        g2.drawLine(hiltX, hiltY, tipX, tipY);
    }

    private void drawEnemy(Graphics graphics, int px, int py, int type) {
        switch (type) {
            case Game.NULLPTR -> {
                graphics.setColor(NULL_COLOR);
                int[] xs = {px + 12, px + 20, px + 12, px + 4};
                int[] ys = {py + 3, py + 12, py + 21, py + 12};
                graphics.fillPolygon(xs, ys, 4);
            }
            case Game.LEAK -> {
                graphics.setColor(LEAK_COLOR);
                graphics.fillRoundRect(px + 3, py + 5, Game.TILE - 6, Game.TILE - 8, 14, 14);
                graphics.setColor(LEAK_DRIP);
                graphics.fillRect(px + 8, py + 8, 4, 3);
            }
            case Game.FORKBOMB -> {
                graphics.setColor(FORK_COLOR);
                graphics.fillRect(px + 5, py + 8, Game.TILE - 10, Game.TILE - 12);
                graphics.fillRect(px + 9, py + 4, Game.TILE - 18, 5);
            }
            case Game.DEADLOCK -> {
                graphics.setColor(DEADLOCK_COLOR);
                graphics.fillRect(px + 3, py + 4, Game.TILE - 6, Game.TILE - 7);
            }
            default -> {
                graphics.setColor(BUG_LEG);
                graphics.fillRect(px + 5, py + Game.TILE - 6, 2, 5);
                graphics.fillRect(px + 11, py + Game.TILE - 6, 2, 5);
                graphics.fillRect(px + 17, py + Game.TILE - 6, 2, 5);
                graphics.setColor(BUG_COLOR);
                graphics.fillOval(px + 4, py + 6, Game.TILE - 8, Game.TILE - 10);
            }
        }
        graphics.setColor(ENEMY_EYE);
        graphics.fillRect(px + 8, py + 10, 3, 3);
        graphics.fillRect(px + Game.TILE - 11, py + 10, 3, 3);
    }

    private void drawHud(Graphics graphics, Game game) {
        int top = Game.PLAY_HEIGHT;
        graphics.setColor(HUD_BACKGROUND);
        graphics.fillRect(0, top, Game.VIEW_WIDTH, Game.HUD_HEIGHT);

        graphics.setColor(HUD_TEXT);
        graphics.drawString(
                "DEPTH " + game.floor + "   LVL " + game.playerLevel
                + "   ATK " + game.attackPower() + "   DEF " + game.defense()
                + "   GOLD " + game.gold + "   POT " + game.potions, 12, top + 22
        );

        int barX = 430;
        int barWidth = 200;
        int hpY = top + 10;
        graphics.setColor(HP_BACK);
        graphics.fillRect(barX, hpY, barWidth, 15);
        graphics.setColor(HP_FILL);
        graphics.fillRect(barX, hpY, barWidth * Math.max(0, game.playerHp) / game.playerMaxHp, 15);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("HP " + game.playerHp + "/" + game.playerMaxHp, barX + 64, hpY + 12);

        int xpY = top + 34;
        graphics.setColor(XP_BACK);
        graphics.fillRect(barX, xpY, barWidth, 7);
        graphics.setColor(XP_FILL);
        graphics.fillRect(barX, xpY, barWidth * Math.min(game.playerXp, game.xpForNext()) / game.xpForNext(), 7);

        graphics.setColor(HUD_HINT);
        graphics.drawString("Move WASD/Arrows   Space attack   Q potion   I inventory   Enter shop   Stairs descend",
                12, top + 58);
    }

    private void drawDeath(Graphics graphics, Game game) {
        graphics.setColor(OVERLAY);
        graphics.fillRect(0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "DUKE HAS FALLEN", Game.PLAY_HEIGHT / 2 - 24);
        drawCentered(graphics, "Reached depth " + game.floor, Game.PLAY_HEIGHT / 2 + 4);
        drawCentered(graphics, "Press Space to descend anew", Game.PLAY_HEIGHT / 2 + 32);
    }

    private void drawShop(Graphics graphics, Game game) {
        graphics.setColor(OVERLAY);
        graphics.fillRect(0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "DUKE FINDS A MERCHANT", Game.PLAY_HEIGHT / 2 - 52);
        drawCentered(graphics, "Gold: " + game.gold + "      Potions: " + game.potions, Game.PLAY_HEIGHT / 2 - 20);
        drawCentered(graphics, "[B]  Buy a potion  -  " + Game.POTION_COST + " gold", Game.PLAY_HEIGHT / 2 + 12);
        drawCentered(graphics, "[Enter]  Leave the merchant", Game.PLAY_HEIGHT / 2 + 40);
    }

    private void drawPause(Graphics graphics, Game game) {
        graphics.setColor(OVERLAY);
        graphics.fillRect(0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "PAUSED", Game.PLAY_HEIGHT / 2 - 40);
        graphics.setColor(game.pauseSelection == 0 ? PROMPT : HUD_HINT);
        drawCentered(graphics, (game.pauseSelection == 0 ? "> " : "  ") + "Resume", Game.PLAY_HEIGHT / 2);
        graphics.setColor(game.pauseSelection == 1 ? PROMPT : HUD_HINT);
        drawCentered(graphics, (game.pauseSelection == 1 ? "> " : "  ") + "Quit", Game.PLAY_HEIGHT / 2 + 30);
        graphics.setColor(HUD_HINT);
        drawCentered(graphics, "Up / Down to choose      Enter to confirm      Esc to resume", Game.PLAY_HEIGHT / 2 + 64);
    }

    private void drawInventory(Graphics graphics, Game game) {
        graphics.setColor(OVERLAY);
        graphics.fillRect(0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "INVENTORY", 64);

        int left = Game.VIEW_WIDTH / 2 - 150;
        graphics.setColor(HUD_HINT);
        graphics.drawString("EQUIPPED", left, 110);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("Weapon    " + slotName(game.equippedWeapon), left, 132);
        graphics.drawString("Armor     " + slotName(game.equippedArmor), left, 152);
        graphics.drawString("Trinket   " + slotName(game.equippedTrinket), left, 172);

        graphics.setColor(HUD_HINT);
        graphics.drawString("CARRIED", left, 212);
        if (game.inventoryCount == 0) {
            graphics.drawString("  (empty)", left, 234);
        } else {
            for (int i = 0; i < game.inventoryCount; i++) {
                boolean selected = i == game.inventorySelection;
                graphics.setColor(selected ? PROMPT : HUD_TEXT);
                graphics.drawString((selected ? "> " : "  ") + Game.ITEM_NAME[game.inventory[i]], left, 234 + i * 20);
            }
        }

        graphics.setColor(HUD_HINT);
        drawCentered(graphics, "[Up/Down] select    [Enter] equip    [D] drop    [I/Esc] close",
                Game.PLAY_HEIGHT - 20);
    }

    private String slotName(int item) {
        return item >= 0 ? Game.ITEM_NAME[item] : "(none)";
    }

    private void drawMerchant(Graphics graphics, int px, int py) {
        int cx = px + Game.TILE / 2;
        graphics.setColor(MERCHANT_ROBE);
        int[] bodyX = {px + 8, px + Game.TILE - 8, px + Game.TILE - 4, px + 4};
        int[] bodyY = {py + 13, py + 13, py + Game.TILE - 1, py + Game.TILE - 1};
        graphics.fillPolygon(bodyX, bodyY, 4);
        graphics.setColor(MERCHANT_TRIM);
        graphics.fillRect(cx - 4, py + 13, 8, 2);
        graphics.fillRect(cx - 1, py + 15, 2, Game.TILE - 17);
        graphics.setColor(MERCHANT_SKIN);
        graphics.fillOval(cx - 5, py + 4, 10, 9);
        graphics.setColor(MERCHANT_HAT);
        graphics.fillRect(cx - 6, py + 3, 12, 2);
        graphics.fillRect(cx - 4, py, 8, 3);
        graphics.setColor(ENEMY_EYE);
        graphics.fillRect(cx - 3, py + 8, 2, 2);
        graphics.fillRect(cx + 1, py + 8, 2, 2);
    }

    private void drawCentered(Graphics graphics, String text, int y) {
        drawCenteredAt(graphics, text, Game.VIEW_WIDTH / 2, y);
    }

    private void drawCenteredAt(Graphics graphics, String text, int centerX, int y) {
        FontMetrics metrics = graphics.getFontMetrics();
        graphics.drawString(text, centerX - metrics.stringWidth(text) / 2, y);
    }
}
