package com.tonicbox.dukes8bit;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;

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
    private static final Color POISON_TINT = new Color(90, 220, 110, 115);
    private static final Color CRIT_FLASH = new Color(255, 232, 90, 150);
    private static final Color HEAL_FLASH = new Color(110, 240, 130, 130);
    private static final Color DODGE_FLASH = new Color(220, 230, 255, 120);
    private static final Color DELTA_UP = new Color(120, 224, 132);
    private static final Color DELTA_DOWN = new Color(232, 96, 96);
    private static final Color DOOR_FRAME_LIT = new Color(120, 88, 52);
    private static final Color DOOR_FRAME_DIM = new Color(54, 40, 26);
    private static final Color DOOR_PANEL_LIT = new Color(168, 120, 66);
    private static final Color DOOR_PANEL_DIM = new Color(74, 54, 32);
    private static final Color DOOR_LOCK_LIT = new Color(232, 200, 110);
    private static final Color DOOR_LOCK_DIM = new Color(96, 82, 46);
    private static final Color KEY_COLOR = new Color(238, 206, 96);
    private static final Color KEY_SHADE = new Color(176, 142, 52);
    private static final Color BOSS_SHADOW = new Color(0, 0, 0, 96);
    private static final Color[] BOSS_BODIES = {
        new Color(58, 36, 74), new Color(34, 54, 76), new Color(54, 62, 34), new Color(70, 44, 36),
    };
    private static final Color BOSS_BODY_ENRAGED = new Color(96, 32, 44);
    private static final Color BOSS_EDGE = new Color(158, 104, 200);
    private static final Color BOSS_CORE = new Color(236, 96, 72);
    private static final Color BOSS_CORE_HOT = new Color(255, 198, 104);
    private static final Color BOSS_EYE = new Color(248, 232, 120);
    private static final Color BOSS_EYE_DARK = new Color(20, 12, 20);
    private static final Color BOSS_TELEGRAPH = new Color(255, 70, 60, 130);
    private static final Color BOSS_FLASH = new Color(255, 255, 255, 160);
    private static final Color SHOCKWAVE_BRIGHT = new Color(255, 150, 90, 200);
    private static final Color SHOCKWAVE_FAINT = new Color(255, 150, 90, 90);
    private static final Color SLAM_DANGER = new Color(228, 64, 52, 120);
    private static final Color SLAM_DANGER_FAINT = new Color(228, 64, 52, 60);
    private static final int MINIMAP_CELL_W = 2;
    private static final int MINIMAP_CELL_H = 3;
    private static final int MINIMAP_PAD_X = 5;
    private static final int MINIMAP_MARGIN = 28;
    private static final int MINIMAP_TOP = 26;
    private static final Color MINIMAP_BACK = new Color(10, 10, 16, 220);
    private static final Color MINIMAP_BORDER = new Color(84, 80, 110);
    private static final Color MINIMAP_FLOOR_LIT = new Color(118, 114, 148);
    private static final Color MINIMAP_FLOOR_DIM = new Color(58, 56, 76);
    private static final Color MINIMAP_DOWN = new Color(236, 206, 92);
    private static final Color MINIMAP_UP = new Color(228, 84, 84);
    private static final Color MINIMAP_DOOR = new Color(200, 150, 70);
    private static final Color MINIMAP_ENEMY = new Color(222, 70, 64);
    private static final Color MINIMAP_BOSS = new Color(244, 60, 72);
    private static final Color MINIMAP_PLAYER = new Color(96, 224, 236);
    private static final Color BOSS_BAR_BACK = new Color(40, 16, 20);
    private static final Color BOSS_BAR_FILL = new Color(206, 60, 72);
    private static final Color BOSS_BAR_FILL_ENRAGED = new Color(240, 120, 60);

    // Reusable polygon scratch arrays — render loop is single-threaded so sharing is safe.
    private static final int[] POLY_X3 = new int[3];
    private static final int[] POLY_Y3 = new int[3];
    private static final int[] POLY_X4 = new int[4];
    private static final int[] POLY_Y4 = new int[4];
    private static final int[] POLY_X5 = new int[5];
    private static final int[] POLY_Y5 = new int[5];

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
                if (game.lootKey[i]) {
                    drawKey(graphics, sx, sy);
                } else if (game.lootChest[i]) {
                    drawChest(graphics, sx, sy);
                } else {
                    drawGem(graphics, sx, sy);
                }
            }
        }

        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                int hop = Math.round(game.enemyHit[i] * 6f);
                drawEnemy(graphics, Math.round(game.enemyRenderPixelX(i)) - cameraX, Math.round(game.enemyRenderPixelY(i)) - cameraY - hop,
                        game.enemyType[i], game.enemyCrit[i], game.enemyPoison[i] > 0f);
            }
        }

        boolean bossShown = game.bossActive && bossVisible(game);
        if (bossShown) {
            int bx = Math.round(game.bossRenderPixelX()) - cameraX;
            int by = Math.round(game.bossRenderPixelY()) - cameraY;
            float telegraph = game.bossTelegraph();
            if (telegraph > 0f) {
                drawSlamDanger(graphics, game, cameraX, cameraY, telegraph);
            }
            drawBoss(graphics, bx, by, game.bossType, game.bossHit, telegraph, game.bossAnimTime, game.bossEnraged);
            if (game.bossShockwave > 0f) {
                int half = Game.BOSS_SIZE * Game.TILE / 2;
                drawShockwave(graphics, bx + half, by + half, game.bossShockwave);
            }
        }

        if (game.merchantX >= 0 && game.visible[Game.index(game.merchantX, game.merchantY)]) {
            int mx = game.merchantX * Game.TILE - cameraX;
            int my = game.merchantY * Game.TILE - cameraY;
            drawMerchant(graphics, mx, my);
            if (game.adjacentToMerchant()) {
                graphics.setColor(PROMPT);
                drawCenteredAt(graphics, "E", mx + Game.TILE / 2, my - 4);
            }
        }

        int dukeX = Math.round(game.renderPixelX()) - cameraX;
        int dukeY = Math.round(game.renderPixelY()) - cameraY;
        drawDuke(graphics, dukeX, dukeY, game.facing, game.playerHeal, game.playerDodge);
        if (game.attackProgress < 1f) {
            drawSword(graphics, dukeX + Game.TILE / 2, dukeY + Game.TILE / 2, game.attackProgress);
        }

        if (bossShown) {
            drawBossBar(graphics, game);
        }
        drawMinimap(graphics, game);

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
        if (tile == Game.LOCKED_DOOR) {
            drawLockedDoor(graphics, px, py, lit);
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
        POLY_Y3[0] = py + Game.TILE - 5; POLY_Y3[1] = py + 8; POLY_Y3[2] = py + Game.TILE - 5;
        for (int i = 0; i < 3; i++) {
            int sx = px + 5 + i * 7;
            POLY_X3[0] = sx; POLY_X3[1] = sx + 3; POLY_X3[2] = sx + 6;
            graphics.fillPolygon(POLY_X3, POLY_Y3, 3);
        }
    }

    /** A studded vault door with a brass lock plate and keyhole; dims when out of the light. */
    private void drawLockedDoor(Graphics graphics, int px, int py, boolean lit) {
        graphics.setColor(lit ? DOOR_FRAME_LIT : DOOR_FRAME_DIM);
        graphics.fillRect(px, py, Game.TILE, Game.TILE);
        graphics.setColor(lit ? DOOR_PANEL_LIT : DOOR_PANEL_DIM);
        graphics.fillRect(px + 3, py + 2, Game.TILE - 6, Game.TILE - 4);
        int cx = px + Game.TILE / 2;
        graphics.setColor(lit ? DOOR_LOCK_LIT : DOOR_LOCK_DIM);
        graphics.fillRect(cx - 3, py + Game.TILE / 2 - 3, 6, 8);
        graphics.setColor(ENEMY_EYE);
        graphics.fillRect(cx - 1, py + Game.TILE / 2 - 1, 2, 2);
        graphics.fillRect(cx - 1, py + Game.TILE / 2 + 1, 2, 3);
    }

    /** A small golden key with a ring bow, shaft, and a pair of teeth. */
    private void drawKey(Graphics graphics, int px, int py) {
        int cx = px + Game.TILE / 2;
        int cy = py + Game.TILE / 2;
        graphics.setColor(KEY_COLOR);
        graphics.fillOval(cx - 7, cy - 5, 7, 7);
        graphics.setColor(BACKGROUND);
        graphics.fillOval(cx - 5, cy - 3, 3, 3);
        graphics.setColor(KEY_COLOR);
        graphics.fillRect(cx - 1, cy - 2, 8, 2);
        graphics.setColor(KEY_SHADE);
        graphics.fillRect(cx + 3, cy, 2, 3);
        graphics.fillRect(cx + 6, cy, 2, 3);
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
        POLY_X4[0] = cx; POLY_X4[1] = cx + 5; POLY_X4[2] = cx; POLY_X4[3] = cx - 5;
        POLY_Y4[0] = cy - 6; POLY_Y4[1] = cy; POLY_Y4[2] = cy + 6; POLY_Y4[3] = cy;
        graphics.fillPolygon(POLY_X4, POLY_Y4, 4);
    }

    /** Draws Duke facing his current travel direction; right reuses the left sprite mirrored. */
    private void drawDuke(Graphics graphics, int px, int py, int facing, float heal, float dodge) {
        switch (facing) {
            case Game.FACE_UP -> drawDukeBack(graphics, px, py);
            case Game.FACE_LEFT -> drawDukeLeft(graphics, px, py);
            case Game.FACE_RIGHT -> drawDukeRight(graphics, px, py);
            default -> drawDukeFront(graphics, px, py);
        }
        if (heal > 0f) {
            graphics.setColor(HEAL_FLASH);
            graphics.fillRect(px + 3, py, Game.TILE - 6, Game.TILE);
        }
        if (dodge > 0f) {
            graphics.setColor(DODGE_FLASH);
            graphics.fillRect(px + 3, py, Game.TILE - 6, Game.TILE);
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
        POLY_X3[0] = px + 10; POLY_X3[1] = px + 14; POLY_X3[2] = px + 12;
        POLY_Y3[0] = py + 12; POLY_Y3[1] = py + 12; POLY_Y3[2] = py + 15;
        graphics.fillPolygon(POLY_X3, POLY_Y3, 3);
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
        POLY_X3[0] = px + 4; POLY_X3[1] = px + 9; POLY_X3[2] = px + 9;
        POLY_Y3[0] = py + 11; POLY_Y3[1] = py + 9; POLY_Y3[2] = py + 13;
        graphics.fillPolygon(POLY_X3, POLY_Y3, 3);
        graphics.setColor(DUKE_FLIPPER);
        graphics.fillRoundRect(px + 7, py + 13, 4, 7, 4, 4);
        graphics.setColor(DUKE_OUTLINE);
        graphics.drawRoundRect(px + 5, py + 2, 14, 20, 11, 11);
    }

    /** Right-facing profile: the left sprite mirrored about the tile's vertical center. */
    private void drawDukeRight(Graphics graphics, int px, int py) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.translate(2 * px + Game.TILE, 0);
        g2.scale(-1, 1);
        drawDukeLeft(g2, px, py);
        g2.scale(-1, 1);
        g2.translate(-(2 * px + Game.TILE), 0);
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

    private void drawEnemy(Graphics graphics, int px, int py, int type, float crit, boolean poisoned) {
        switch (type) {
            case Game.NULLPTR -> {
                graphics.setColor(NULL_COLOR);
                POLY_X4[0] = px + 12; POLY_X4[1] = px + 20; POLY_X4[2] = px + 12; POLY_X4[3] = px + 4;
                POLY_Y4[0] = py + 3; POLY_Y4[1] = py + 12; POLY_Y4[2] = py + 21; POLY_Y4[3] = py + 12;
                graphics.fillPolygon(POLY_X4, POLY_Y4, 4);
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
        if (poisoned) {
            graphics.setColor(POISON_TINT);
            graphics.fillRect(px + 2, py + 2, Game.TILE - 4, Game.TILE - 4);
        }
        if (crit > 0f) {
            graphics.setColor(CRIT_FLASH);
            graphics.fillRect(px + 1, py + 1, Game.TILE - 2, Game.TILE - 2);
        }
    }

    private boolean bossVisible(Game game) {
        for (int oy = 0; oy < Game.BOSS_SIZE; oy++) {
            for (int ox = 0; ox < Game.BOSS_SIZE; ox++) {
                int x = game.bossX + ox;
                int y = game.bossY + oy;
                if (x >= 0 && y >= 0 && x < Game.MAP_WIDTH && y < Game.MAP_HEIGHT
                        && game.visible[Game.index(x, y)]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Draws the multi-tile boss: a looming spiked mass with a pulsing core and glaring eyes that bobs
     * while idle, flares red as it winds up a slam, and whitewashes when struck.
     */
    private void drawBoss(Graphics graphics, int px, int py, int type, float hit, float telegraph, float anim, boolean enraged) {
        int size = Game.BOSS_SIZE * Game.TILE;
        py += (int) (Math.sin(anim / 240.0) * 3);

        graphics.setColor(BOSS_SHADOW);
        graphics.fillOval(px + 8, py + size - 16, size - 16, 14);

        graphics.setColor(enraged ? BOSS_BODY_ENRAGED : BOSS_BODIES[type & 3]);
        POLY_Y3[0] = py + 16; POLY_Y3[1] = py - 1; POLY_Y3[2] = py + 16;
        for (int i = 0; i < 5; i++) {
            int sx = px + 12 + i * ((size - 24) / 4);
            POLY_X3[0] = sx; POLY_X3[1] = sx + 7; POLY_X3[2] = sx + 14;
            graphics.fillPolygon(POLY_X3, POLY_Y3, 3);
        }
        graphics.fillRoundRect(px + 6, py + 10, size - 12, size - 20, 30, 30);
        graphics.setColor(BOSS_EDGE);
        graphics.drawRoundRect(px + 6, py + 10, size - 12, size - 20, 30, 30);

        int pulse = (int) (Math.sin(anim / 160.0) * 3) + 3;
        graphics.setColor(BOSS_CORE);
        graphics.fillOval(px + size / 2 - 14 - pulse / 2, py + size / 2 - 10 - pulse / 2, 28 + pulse, 24 + pulse);
        graphics.setColor(BOSS_CORE_HOT);
        graphics.fillOval(px + size / 2 - 7, py + size / 2 - 6, 14, 12);

        graphics.setColor(BOSS_EYE);
        int eyeY = py + 24;
        graphics.fillOval(px + 16, eyeY, 9, 9);
        graphics.fillOval(px + size - 25, eyeY, 9, 9);
        graphics.fillOval(px + size / 2 - 4, py + 17, 8, 8);
        graphics.setColor(BOSS_EYE_DARK);
        graphics.fillOval(px + 19, eyeY + 3, 3, 3);
        graphics.fillOval(px + size - 22, eyeY + 3, 3, 3);
        graphics.fillOval(px + size / 2 - 1, py + 20, 3, 3);

        graphics.setColor(BOSS_EYE_DARK);
        int mawY = py + size - 28;
        POLY_X5[0] = px + 22; POLY_X5[1] = px + 30; POLY_X5[2] = px + 38; POLY_X5[3] = px + 46; POLY_X5[4] = px + 54;
        POLY_Y5[0] = mawY; POLY_Y5[1] = mawY + 9; POLY_Y5[2] = mawY; POLY_Y5[3] = mawY + 9; POLY_Y5[4] = mawY;
        graphics.fillPolygon(POLY_X5, POLY_Y5, 5);

        if (telegraph > 0f) {
            graphics.setColor(BOSS_TELEGRAPH);
            int grow = (int) (telegraph * 10);
            graphics.fillRoundRect(px + 6 - grow, py + 10 - grow, size - 12 + grow * 2, size - 20 + grow * 2, 30, 30);
        }
        if (hit > 0f) {
            graphics.setColor(BOSS_FLASH);
            graphics.fillRoundRect(px + 6, py + 10, size - 12, size - 20, 30, 30);
        }
    }

    /**
     * Tints the exact tiles the boss's slam will strike during its wind-up, so the player can read which
     * neighbouring tiles to flee to. Brightens as the slam nears.
     */
    private void drawSlamDanger(Graphics graphics, Game game, int cameraX, int cameraY, float telegraph) {
        graphics.setColor(telegraph > 0.5f ? SLAM_DANGER : SLAM_DANGER_FAINT);
        int radius = Game.BOSS_SLAM_RADIUS;
        for (int y = game.bossY - radius; y < game.bossY + Game.BOSS_SIZE + radius; y++) {
            for (int x = game.bossX - radius; x < game.bossX + Game.BOSS_SIZE + radius; x++) {
                if (x < 0 || y < 0 || x >= Game.MAP_WIDTH || y >= Game.MAP_HEIGHT) {
                    continue;
                }
                int idx = Game.index(x, y);
                if (game.bossOccupies(x, y) || !game.visible[idx] || game.map[idx] == Game.WALL) {
                    continue;
                }
                graphics.fillRect(x * Game.TILE - cameraX, y * Game.TILE - cameraY, Game.TILE, Game.TILE);
            }
        }
    }

    /** An expanding ring marking the boss slam's reach, brightest at the moment of impact. */
    private void drawShockwave(Graphics graphics, int centerX, int centerY, float t) {
        Graphics2D g2 = (Graphics2D) graphics;
        Stroke previous = g2.getStroke();
        g2.setStroke(SWORD_STROKE);
        int radius = (int) ((1f - t) * (Game.BOSS_SIZE / 2f + Game.BOSS_SLAM_RADIUS) * Game.TILE);
        g2.setColor(t > 0.5f ? SHOCKWAVE_BRIGHT : SHOCKWAVE_FAINT);
        g2.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        g2.setStroke(previous);
    }

    private void drawBossBar(Graphics graphics, Game game) {
        int barWidth = 360;
        int barX = (Game.PLAY_WIDTH - barWidth) / 2;
        int barY = 16;
        graphics.setColor(BOSS_BAR_BACK);
        graphics.fillRect(barX, barY, barWidth, 16);
        graphics.setColor(game.bossEnraged ? BOSS_BAR_FILL_ENRAGED : BOSS_BAR_FILL);
        graphics.fillRect(barX, barY, barWidth * Math.max(0, game.bossHp) / game.bossMaxHp, 16);
        graphics.setColor(HUD_TEXT);
        drawCenteredAt(graphics, bossName(game.bossType) + (game.bossEnraged ? "   — ENRAGED" : ""),
                Game.PLAY_WIDTH / 2, barY - 4);
        graphics.setColor(HUD_HINT);
        drawCenteredAt(graphics, "The stairs below stay sealed until it falls", Game.PLAY_WIDTH / 2, barY + 30);
    }

    private String bossName(int type) {
        return switch (type) {
            case 0 -> "KERNEL PANIC";
            case 1 -> "STACK OVERFLOW";
            case 2 -> "SEGFAULT";
            default -> "HEAP CORRUPTION";
        };
    }

    /**
     * A fog-aware overview of the floor in the top-right: explored corridors and rooms (brighter where
     * currently lit), stairs, sealed vault doors, visible enemies and the boss, and Duke as a bright dot.
     */
    private void drawMinimap(Graphics graphics, Game game) {
        int cellW = MINIMAP_CELL_W;
        int cellH = MINIMAP_CELL_H;
        int height = Game.MAP_HEIGHT * cellH;
        int panelWidth = Game.MAP_WIDTH * cellW + MINIMAP_PAD_X * 2;
        int originX = Game.VIEW_WIDTH - panelWidth - MINIMAP_MARGIN;
        int originY = MINIMAP_TOP;
        int gridX = originX + MINIMAP_PAD_X;

        graphics.setColor(MINIMAP_BACK);
        graphics.fillRect(originX - 3, originY - 3, panelWidth + 6, height + 6);
        graphics.setColor(MINIMAP_BORDER);
        graphics.drawRect(originX - 3, originY - 3, panelWidth + 5, height + 5);

        for (int ty = 0; ty < Game.MAP_HEIGHT; ty++) {
            for (int tx = 0; tx < Game.MAP_WIDTH; tx++) {
                int idx = Game.index(tx, ty);
                if (!game.explored[idx]) {
                    continue;
                }
                Color color = minimapColor(game.map[idx], game.visible[idx]);
                if (color == null) {
                    continue;
                }
                graphics.setColor(color);
                graphics.fillRect(gridX + tx * cellW, originY + ty * cellH, cellW, cellH);
            }
        }

        graphics.setColor(MINIMAP_ENEMY);
        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                graphics.fillRect(gridX + game.enemyX[i] * cellW, originY + game.enemyY[i] * cellH, cellW, cellH);
            }
        }

        if (game.bossActive && bossVisible(game)) {
            graphics.setColor(MINIMAP_BOSS);
            graphics.fillRect(gridX + game.bossX * cellW, originY + game.bossY * cellH,
                    Game.BOSS_SIZE * cellW, Game.BOSS_SIZE * cellH);
        }

        graphics.setColor(MINIMAP_PLAYER);
        graphics.fillRect(gridX + game.playerX * cellW - 1, originY + game.playerY * cellH - 1, cellW + 2, cellH + 2);
    }

    /** Minimap colour for a tile: null hides unseen walls; floors dim when only remembered, not lit. */
    private Color minimapColor(int tile, boolean lit) {
        return switch (tile) {
            case Game.WALL -> null;
            case Game.DOWN_STAIRS -> MINIMAP_DOWN;
            case Game.UP_STAIRS -> MINIMAP_UP;
            case Game.LOCKED_DOOR -> MINIMAP_DOOR;
            default -> lit ? MINIMAP_FLOOR_LIT : MINIMAP_FLOOR_DIM;
        };
    }

    private void drawHud(Graphics graphics, Game game) {
        int top = Game.PLAY_HEIGHT;
        graphics.setColor(HUD_BACKGROUND);
        graphics.fillRect(0, top, Game.VIEW_WIDTH, Game.HUD_HEIGHT);

        graphics.setColor(HUD_TEXT);
        graphics.drawString(
                "DEPTH " + game.floor + "   LVL " + game.playerLevel
                + "   ATK " + game.attackPower() + "   DEF " + game.defense()
                + "   GOLD " + game.gold + "   POT " + game.potions + "   KEY " + game.keys, 12, top + 22
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
        graphics.drawString("Move WASD/Arrows   Space attack   Q potion   I inventory   M mute   E shop   Stairs descend",
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
        drawCentered(graphics, "[E]  Leave the merchant", Game.PLAY_HEIGHT / 2 + 40);
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
        drawCentered(graphics, "INVENTORY", 48);

        int leftX = 60;
        int rightX = 356;

        graphics.setColor(HUD_HINT);
        graphics.drawString("CHARACTER", leftX, 84);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("HP         " + game.playerHp + " / " + game.playerMaxHp, leftX, 108);
        graphics.drawString("ATK        " + game.attackPower(), leftX, 128);
        graphics.drawString("DEF        " + game.defense(), leftX, 148);
        graphics.drawString("Crit       " + game.critChance() + "%", leftX, 168);
        graphics.drawString("Lifesteal  " + game.lifestealPercent() + "%", leftX, 188);
        graphics.drawString("Dodge      " + game.dodgeChance() + "%", leftX, 208);

        graphics.setColor(HUD_HINT);
        graphics.drawString("EQUIPPED", rightX, 84);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("Weapon   " + slotName(game.equippedWeapon), rightX, 108);
        graphics.drawString("Armor    " + slotName(game.equippedArmor), rightX, 128);
        graphics.drawString("Trinket  " + slotName(game.equippedTrinket), rightX, 148);

        graphics.setColor(HUD_HINT);
        graphics.drawString("CARRIED", leftX, 252);
        if (game.inventoryCount == 0) {
            graphics.setColor(HUD_TEXT);
            graphics.drawString("  (empty)", leftX, 276);
        } else {
            for (int i = 0; i < game.inventoryCount; i++) {
                boolean selected = i == game.inventorySelection;
                graphics.setColor(selected ? PROMPT : HUD_TEXT);
                graphics.drawString((selected ? "> " : "  ") + Game.ITEM_NAME[game.inventory[i]], leftX, 276 + i * 20);
            }
            drawEquipDelta(graphics, game, rightX);
        }

        graphics.setColor(HUD_HINT);
        drawCentered(graphics, "[Up/Down] select    [E] equip    [D] drop    [I/Esc] close",
                Game.VIEW_HEIGHT - 22);
    }

    /**
     * Shows what equipping the highlighted carried item would change: the ATK/DEF swing in green/red,
     * the effect gained or lost, and which item it would replace. Modeled on a "vs equipped" preview.
     */
    private void drawEquipDelta(Graphics graphics, Game game, int x) {
        int carried = game.inventory[game.inventorySelection];
        int slot = game.itemSlot(carried);
        int current = game.equippedCounterpart(carried);

        graphics.setColor(HUD_HINT);
        graphics.drawString("IF EQUIPPED", x, 252);

        int lineY = 252 + 24;
        if (slot == Game.WEAPON || slot == Game.ARMOR) {
            String stat;
            int oldTotal;
            int newTotal;
            if (slot == Game.WEAPON) {
                stat = "ATK";
                oldTotal = game.attackPower();
                newTotal = game.playerAtk + game.itemValue(carried);
            } else {
                stat = "DEF";
                oldTotal = game.defense();
                newTotal = game.itemValue(carried);
            }
            int diff = newTotal - oldTotal;
            graphics.setColor(diff > 0 ? DELTA_UP : diff < 0 ? DELTA_DOWN : HUD_TEXT);
            String sign = diff >= 0 ? "+" : "";
            graphics.drawString(stat + "  " + oldTotal + " -> " + newTotal + "   (" + sign + diff + ")", x, lineY);
            lineY += 24;
        }

        int newEffect = game.itemEffect(carried);
        int oldEffect = current >= 0 ? game.itemEffect(current) : Game.EFF_NONE;
        if (newEffect != Game.EFF_NONE) {
            graphics.setColor(DELTA_UP);
            graphics.drawString("gain " + Game.effectLabel(newEffect), x, lineY);
            lineY += 20;
        }
        if (oldEffect != Game.EFF_NONE && oldEffect != newEffect) {
            graphics.setColor(DELTA_DOWN);
            graphics.drawString("lose " + Game.effectLabel(oldEffect), x, lineY);
            lineY += 20;
        }
        graphics.setColor(HUD_HINT);
        graphics.drawString(current >= 0 ? "replaces " + Game.ITEM_NAME[current] : "fills an empty slot", x, lineY);
    }

    private String slotName(int item) {
        return item >= 0 ? Game.ITEM_NAME[item] : "(none)";
    }

    private void drawMerchant(Graphics graphics, int px, int py) {
        int cx = px + Game.TILE / 2;
        graphics.setColor(MERCHANT_ROBE);
        POLY_X4[0] = px + 8; POLY_X4[1] = px + Game.TILE - 8; POLY_X4[2] = px + Game.TILE - 4; POLY_X4[3] = px + 4;
        POLY_Y4[0] = py + 13; POLY_Y4[1] = py + 13; POLY_Y4[2] = py + Game.TILE - 1; POLY_Y4[3] = py + Game.TILE - 1;
        graphics.fillPolygon(POLY_X4, POLY_Y4, 4);
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
