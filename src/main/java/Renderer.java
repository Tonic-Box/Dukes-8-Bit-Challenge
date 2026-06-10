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
 * Colors live as constants (and a small fog-overlay lookup table) so no Color
 * objects are allocated while rendering.
 */
final class Renderer {

    private static final Color BACKGROUND = new Color(8, 8, 12);
    private static final Color FLOOR_LIT = new Color(64, 60, 78);
    private static final Color WALL_LIT = new Color(112, 106, 142);
    private static final Color STAIRS_LIT = new Color(236, 206, 92);
    private static final Color RED_SOFT = new Color(228, 84, 84);
    private static final Color HUD_TEXT = Color.WHITE;
    private static final Color GRAY_LIGHT = new Color(150, 150, 172);
    private static final Color BAR_BACK = new Color(58, 22, 22);
    private static final Color XP_FILL = new Color(96, 140, 230);
    private static final Color DUKE_NOSE = new Color(232, 44, 44);
    private static final Color RED_MAIN = new Color(214, 70, 64);
    private static final Color PURPLE = new Color(158, 96, 214);
    private static final Color LEAK_COLOR = new Color(92, 192, 112);
    private static final Color FORK_COLOR = new Color(232, 150, 44);
    private static final Color DEADLOCK_COLOR = new Color(120, 122, 136);
    private static final Color DARK = new Color(20, 16, 24);
    private static final Color OVERLAY = new Color(0, 0, 0, 178);
    private static final Color SWORD_COLOR = new Color(224, 226, 240);
    private static final Color SWORD_TRAIL_1 = new Color(224, 226, 240, 150);
    private static final Color SWORD_TRAIL_2 = new Color(224, 226, 240, 70);
    private static final BasicStroke SWORD_STROKE = new BasicStroke(3f);
    private static final Color FLOOR_LIT_ALT = new Color(56, 52, 70);
    private static final Color DUKE_OUTLINE = new Color(22, 28, 70);
    private static final Color DUKE_BELLY = new Color(236, 239, 246);
    private static final Color DUKE_FOOT = new Color(242, 170, 44);
    private static final Color BUG_LEG = new Color(120, 32, 28);
    private static final Color MERCHANT_ROBE = new Color(150, 110, 60);
    private static final Color MERCHANT_SKIN = new Color(232, 196, 158);
    private static final Color PROMPT = new Color(245, 240, 210);
    private static final Color POT_BODY = new Color(178, 90, 54);
    private static final Color POT_RIM = new Color(210, 130, 80);
    private static final Color BROWN = new Color(110, 78, 38);
    private static final Color VASE_BODY = new Color(88, 128, 170);
    private static final Color VASE_NECK = new Color(112, 158, 200);
    private static final Color[] SCENERY_DEBRIS = {
        new Color(190, 110, 60), new Color(180, 140, 75), new Color(100, 140, 185),
    };
    private static final Color CHEST_BODY = new Color(150, 96, 40);
    private static final Color CHEST_LID = new Color(220, 174, 72);
    private static final Color MIMIC_LID = new Color(192, 44, 44);
    private static final Color MIMIC_EYE = new Color(248, 220, 60);
    private static final Color LOOT_GEM = new Color(118, 210, 232);
    private static final Color POISON_TINT = new Color(90, 220, 110, 115);
    private static final Color CRIT_FLASH = new Color(255, 232, 90, 150);
    private static final Color HEAL_FLASH = new Color(110, 240, 130, 130);
    private static final Color DODGE_FLASH = new Color(220, 230, 255, 120);
    private static final Color DELTA_UP = new Color(120, 224, 132);
    private static final Color DOOR_FRAME_LIT = new Color(120, 88, 52);
    private static final Color DOOR_PANEL_LIT = new Color(168, 120, 66);
    private static final Color DOOR_LOCK_LIT = new Color(232, 200, 110);
    private static final Color BOSS_SHADOW = new Color(0, 0, 0, 96);
    private static final Color[] BOSS_BODIES = {
            new Color(58, 36, 74),
            new Color(34, 54, 76),
            new Color(54, 62, 34),
            new Color(70, 44, 36),
    };
    private static final Color BOSS_BODY_ENRAGED = new Color(96, 32, 44);
    private static final Color BOSS_CORE_HOT = new Color(255, 198, 104);
    private static final Color BOSS_EYE = new Color(248, 232, 120);
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
    private static final Color MINIMAP_DOOR = new Color(200, 150, 70);
    private static final Color MINIMAP_PLAYER = new Color(96, 224, 236);
    private static final Color BOSS_BAR_FILL_ENRAGED = new Color(240, 120, 60);

    // Fog-of-war is drawn by laying every tile in its lit form, then overlaying one of these
    // translucent-black steps sized by the tile's light level. At light 0 the overlay's 60% black
    // reproduces the old dim look; at light 1 it is fully transparent. Smoothly interpolating the
    // light level per tile makes the lighting glide with the player instead of snapping.
    private static final int FOG_STEPS = 16;
    private static final Color[] FOG = new Color[FOG_STEPS];
    static {
        for (int i = 0; i < FOG_STEPS; i++) {
            FOG[i] = new Color(0, 0, 0, i * 153 / (FOG_STEPS - 1));
        }
    }

    // Reusable polygon scratch arrays — render loop is single-threaded so sharing is safe.
    private static final int[] POLY_X = new int[5];
    private static final int[] POLY_Y = new int[5];

    /**
     * Draws one frame: the camera-relative, fog-aware dungeon, then loot, entities,
     * Duke, the HUD, and any overlay for the current game state.
     */
    void render(Graphics graphics, Game game) {
        rect(graphics, BACKGROUND, 0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);

        // Round the camera to whole pixels so the scrolling dungeon stays crisp instead of shimmering.
        int cameraX = Math.round(game.cameraX());
        int cameraY = Math.round(game.cameraY());

        graphics.setClip(0, 0, Game.PLAY_WIDTH, Game.PLAY_HEIGHT);
        int firstX = Math.max(0, cameraX / Game.TILE);
        int firstY = Math.max(0, cameraY / Game.TILE);
        int lastX = Math.min(Game.MAP_WIDTH - 1, (cameraX + Game.PLAY_WIDTH) / Game.TILE);
        int lastY = Math.min(Game.MAP_HEIGHT - 1, (cameraY + Game.PLAY_HEIGHT) / Game.TILE);
        // Push the camera into the transform so all world content draws in world pixels with no per-draw
        // offset; the play-area clip set above stays pinned to the screen. Restored before the HUD/minimap.
        graphics.translate(-cameraX, -cameraY);
        // Tiles are drawn per-cell, but the translucent fog overlay is coalesced into horizontal runs of
        // equal darkness and laid down as one fillRect per run — far fewer alpha-composited fills per frame.
        for (int y = firstY; y <= lastY; y++) {
            int rowSy = y * Game.TILE;
            int runStep = 0;
            int runStartSx = 0;
            int runWidth = 0;
            for (int x = firstX; x <= lastX; x++) {
                int idx = Game.index(x, y);
                int screenX = x * Game.TILE;
                int step;
                if (!game.explored[idx]) {
                    step = 0;
                } else {
                    int tile = game.map[idx];
                    drawTile(graphics, screenX, rowSy, tile, ((x + y) & 1) == 1, x, y);
                    if (tile == Game.TRAP && game.visible[idx] && Math.abs(x - game.playerX) + Math.abs(y - game.playerY) <= 2) {
                        drawTrap(graphics, screenX, rowSy);
                    }
                    float light = game.lightLevel[idx];
                    step = light < 0.997f ? Math.round((1f - light) * (FOG_STEPS - 1)) : 0;
                }
                if (step == runStep) {
                    runWidth += Game.TILE;
                } else {
                    fillFog(graphics, runStep, runStartSx, rowSy, runWidth);
                    runStep = step;
                    runStartSx = screenX;
                    runWidth = Game.TILE;
                }
            }
            fillFog(graphics, runStep, runStartSx, rowSy, runWidth);
        }

        for (int i = 0; i < game.breakCount; i++) {
            float life = game.breakTimer[i];
            int breakTileX = game.breakX[i], breakTileY = game.breakY[i];
            int cx = breakTileX * Game.TILE + Game.TILE / 2;
            int cy = breakTileY * Game.TILE + Game.TILE / 2;
            int spread = (int) ((1f - life) * 14) + 1;
            int size = Math.max(1, (int) (life * 4));
            graphics.setColor(SCENERY_DEBRIS[(breakTileX * 7 + breakTileY * 3) % 3]);
            for (int shard = 0; shard < 6; shard++) {
                double angle = shard * Math.PI / 3;
                int fragmentX = cx + (int) (Math.cos(angle) * spread);
                int fragmentY = cy + (int) (Math.sin(angle) * spread);
                graphics.fillRect(fragmentX - size / 2, fragmentY - size / 2, size, size);
            }
        }
        for (int i = 0; i < game.lootCount; i++) {
            if (game.visible[Game.index(game.lootX[i], game.lootY[i])]) {
                int screenX = game.lootX[i] * Game.TILE;
                int screenY = game.lootY[i] * Game.TILE;
                if (game.lootKey[i]) {
                    drawKey(graphics, screenX, screenY);
                } else if (game.lootChest[i]) {
                    drawChest(graphics, screenX, screenY);
                } else {
                    drawGem(graphics, screenX, screenY);
                }
            }
        }

        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                int hop = Math.round(game.enemyHit[i] * 6f);
                drawEnemy(graphics, Math.round(game.enemyRenderPixelX(i)), Math.round(game.enemyRenderPixelY(i)) - hop,
                        game.enemyType[i], game.enemyCrit[i], game.enemyPoison[i] > 0f, game.enemyAttack[i]);
            }
        }

        boolean bossShown = game.bossActive && bossVisible(game);
        if (bossShown) {
            int bossPixelX = Math.round(game.bossRenderPixelX());
            int bossPixelY = Math.round(game.bossRenderPixelY());
            float telegraph = game.bossTelegraph();
            if (telegraph > 0f) {
                drawSlamDanger(graphics, game, telegraph);
            }
            drawBoss(graphics, bossPixelX, bossPixelY, game.bossType, game.bossHit, telegraph, game.bossAnimTime, game.bossEnraged);
            if (game.bossShockwave > 0f) {
                int half = Game.BOSS_SIZE * Game.TILE / 2;
                drawShockwave(graphics, bossPixelX + half, bossPixelY + half, game.bossShockwave);
            }
        }

        if (game.merchantX >= 0 && game.visible[Game.index(game.merchantX, game.merchantY)]) {
            int merchantPixelX = game.merchantX * Game.TILE;
            int merchantPixelY = game.merchantY * Game.TILE;
            drawMerchant(graphics, merchantPixelX, merchantPixelY);
            if (game.adjacentToMerchant()) {
                graphics.setColor(PROMPT);
                drawCenteredAt(graphics, "E", merchantPixelX + Game.TILE / 2, merchantPixelY - 4);
            }
        }
        for (int i = 0; i < game.lootCount; i++) {
            if (game.lootChest[i] && game.visible[Game.index(game.lootX[i], game.lootY[i])]
                    && Math.abs(game.lootX[i] - game.playerX) + Math.abs(game.lootY[i] - game.playerY) <= 1) {
                graphics.setColor(PROMPT);
                drawCenteredAt(graphics, "E", game.lootX[i] * Game.TILE + Game.TILE / 2,
                        game.lootY[i] * Game.TILE - 4);
                break;
            }
        }

        int dukeX = Math.round(game.renderPixelX());
        int dukeY = Math.round(game.renderPixelY());
        // While a step is in flight, hide one foot - alternating each step via the destination tile's parity - for a
        // two-frame walk cycle in step with the footfalls; standing still (moveProgress == 1) shows both feet. The
        // same parity drives Duke's waddle (applied in drawDuke).
        int footHide = game.moveProgress < 1f ? ((game.playerX + game.playerY) & 1) + 1 : 0;
        if (game.falling && game.fallProgress > 0f) {
            float scale = 1f - game.fallProgress;
            if (scale > 0.02f) {
                Graphics2D graphics2d = (Graphics2D) graphics;
                int cx = dukeX + Game.TILE / 2;
                int cy = dukeY + Game.TILE / 2;
                graphics2d.translate(cx, cy);
                graphics2d.scale(scale, scale);
                graphics2d.translate(-cx, -cy);
                drawDuke(graphics, dukeX, dukeY, game.facing, 0f, 0f, footHide);
                graphics2d.translate(cx, cy);
                graphics2d.scale(1.0 / scale, 1.0 / scale);
                graphics2d.translate(-cx, -cy);
            }
        } else {
            drawDuke(graphics, dukeX, dukeY, game.facing, game.playerHeal, game.playerDodge, footHide);
        }
        if (game.attackProgress < 1f) {
            drawSword(graphics, dukeX + Game.TILE / 2, dukeY + Game.TILE / 2, game.attackProgress);
        }

        // Back to screen space for the fixed-position UI layers.
        graphics.translate(cameraX, cameraY);
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

    /** setColor + fillRect folded into one call, for the common single-fill idiom. */
    private static void rect(Graphics graphics, Color color, int x, int y, int w, int h) {
        graphics.setColor(color);
        graphics.fillRect(x, y, w, h);
    }

    /** setColor + fillOval folded into one call, for the common single-fill idiom. */
    private static void oval(Graphics graphics, Color color, int x, int y, int w, int h) {
        graphics.setColor(color);
        graphics.fillOval(x, y, w, h);
    }

    /** Lays one coalesced run of the fog overlay; step 0 is fully lit and draws nothing. */
    private void fillFog(Graphics graphics, int step, int sx, int sy, int width) {
        if (step > 0) {
            rect(graphics, FOG[step], sx, sy, width, Game.TILE);
        }
    }

    /**
     * Draws a tile in its fully-lit form; the caller overlays a fog step afterward to dim it by the
     * tile's current light level, so this method never needs to know how lit the tile is.
     */
    private void drawTile(Graphics graphics, int px, int py, int tile, boolean alt, int tx, int ty) {
        if (tile == Game.WALL) {
            rect(graphics, WALL_LIT, px, py, Game.TILE, Game.TILE);
            rect(graphics, GRAY_LIGHT, px, py, Game.TILE, 3);
            rect(graphics, DARK, px, py + Game.TILE - 3, Game.TILE, 3);
            return;
        }
        if (tile == Game.LOCKED_DOOR) {
            drawLockedDoor(graphics, px, py);
            return;
        }
        if (tile == Game.SCENERY) {
            rect(graphics, alt ? FLOOR_LIT_ALT : FLOOR_LIT, px, py, Game.TILE, Game.TILE);
            drawScenery(graphics, px, py, (tx * 7 + ty * 3) % 3);
            return;
        }
        if (tile == Game.PIT) {
            rect(graphics, DARK, px, py, Game.TILE, Game.TILE);
            oval(graphics, BACKGROUND, px + 3, py + 3, Game.TILE - 6, Game.TILE - 6);
            oval(graphics, Color.BLACK, px + 7, py + 7, Game.TILE - 14, Game.TILE - 14);
            return;
        }
        rect(graphics, alt ? FLOOR_LIT_ALT : FLOOR_LIT, px, py, Game.TILE, Game.TILE);
        if (tile == Game.DOWN_STAIRS) {
            drawStairs(graphics, px, py, STAIRS_LIT);
        } else if (tile == Game.UP_STAIRS) {
            drawStairs(graphics, px, py, RED_SOFT);
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
        graphics.setColor(RED_MAIN);
        POLY_Y[0] = py + Game.TILE - 5; POLY_Y[1] = py + 8; POLY_Y[2] = py + Game.TILE - 5;
        for (int i = 0; i < 3; i++) {
            int spikeX = px + 5 + i * 7;
            POLY_X[0] = spikeX; POLY_X[1] = spikeX + 3; POLY_X[2] = spikeX + 6;
            graphics.fillPolygon(POLY_X, POLY_Y, 3);
        }
    }

    private void drawScenery(Graphics graphics, int px, int py, int type) {
        switch (type) {
            case 0 -> {
                oval(graphics, POT_BODY, px + 5, py + 9, 14, 11);
                graphics.fillRect(px + 8, py + 6, 8, 5);
                rect(graphics, POT_RIM, px + 7, py + 5, 10, 3);
            }
            case 1 -> {
                rect(graphics, DOOR_PANEL_LIT, px + 4, py + 6, 16, 13);
                graphics.setColor(BROWN);
                graphics.drawRect(px + 4, py + 6, 16, 13);
            }
            default -> {
                oval(graphics, VASE_NECK, px + 5, py + 4, 14, 6);
                rect(graphics, VASE_BODY, px + 8, py + 8, 8, 8);
                graphics.fillOval(px + 4, py + 13, 16, 7);
            }
        }
    }

    /** A studded vault door with a brass lock plate and keyhole; the fog overlay dims it out of light. */
    private void drawLockedDoor(Graphics graphics, int px, int py) {
        rect(graphics, DOOR_FRAME_LIT, px, py, Game.TILE, Game.TILE);
        rect(graphics, DOOR_PANEL_LIT, px + 3, py + 2, Game.TILE - 6, Game.TILE - 4);
        int cx = px + Game.TILE / 2;
        rect(graphics, DOOR_LOCK_LIT, cx - 3, py + Game.TILE / 2 - 3, 6, 8);
    }

    /** A small golden key with a ring bow, shaft, and a pair of teeth. */
    private void drawKey(Graphics graphics, int px, int py) {
        int cx = px + Game.TILE / 2;
        int cy = py + Game.TILE / 2;
        oval(graphics, STAIRS_LIT, cx - 7, cy - 5, 7, 7);
        rect(graphics, STAIRS_LIT, cx - 1, cy - 2, 8, 2);
    }

    private void drawChest(Graphics graphics, int px, int py) {
        rect(graphics, CHEST_BODY, px + 5, py + 10, Game.TILE - 10, Game.TILE - 13);
        rect(graphics, CHEST_LID, px + 5, py + 7, Game.TILE - 10, 5);
        rect(graphics, DARK, px + Game.TILE / 2 - 1, py + 11, 2, 4);
    }

    private void drawGem(Graphics graphics, int px, int py) {
        graphics.setColor(LOOT_GEM);
        int cx = px + Game.TILE / 2;
        int cy = py + Game.TILE / 2;
        POLY_X[0] = cx; POLY_X[1] = cx + 5; POLY_X[2] = cx; POLY_X[3] = cx - 5;
        POLY_Y[0] = cy - 6; POLY_Y[1] = cy; POLY_Y[2] = cy + 6; POLY_Y[3] = cy;
        graphics.fillPolygon(POLY_X, POLY_Y, 4);
    }

    /** Draws Duke facing his current travel direction; right reuses the left sprite mirrored. */
    private void drawDuke(Graphics graphics, int px, int py, int facing, float heal, float dodge, int footHide) {
        // Front/back waddle one pixel sideways per step (footHide carries the parity); profiles and standing stay
        // put. A whole-pixel shift keeps the eye-bearing front sprite crisp.
        if (facing != Game.FACE_LEFT && facing != Game.FACE_RIGHT) {
            px += footHide == 1 ? -1 : footHide == 2 ? 1 : 0;
        }
        switch (facing) {
            case Game.FACE_UP -> drawDukeBack(graphics, px, py, footHide);
            case Game.FACE_LEFT -> drawDukeLeft(graphics, px, py);
            case Game.FACE_RIGHT -> drawDukeRight(graphics, px, py);
            default -> drawDukeFront(graphics, px, py, footHide);
        }
        graphics.setColor(DUKE_OUTLINE);
        graphics.drawRoundRect(px + 5, py + 2, 14, 20, 11, 11);
        if (heal > 0f) {
            rect(graphics, HEAL_FLASH, px + 3, py, Game.TILE - 6, Game.TILE);
        }
        if (dodge > 0f) {
            rect(graphics, DODGE_FLASH, px + 3, py, Game.TILE - 6, Game.TILE);
        }
    }

    /**
     * The feet, flippers, and body shell shared by Duke's front and back sprites. {@code footHide} is 0 (both feet),
     * 1 (hide the left foot), or 2 (hide the right) - the walk cycle alternates 1 and 2 each step.
     */
    private void drawDukeBase(Graphics graphics, int px, int py, int footHide) {
        graphics.setColor(DUKE_FOOT);
        if (footHide != 1) {
            graphics.fillRect(px + 7, py + 21, 4, 2);
        }
        if (footHide != 2) {
            graphics.fillRect(px + 13, py + 21, 4, 2);
        }
        graphics.setColor(DARK);
        graphics.fillRoundRect(px + 3, py + 10, 4, 8, 4, 4);
        graphics.fillRoundRect(px + 17, py + 10, 4, 8, 4, 4);
        graphics.fillRoundRect(px + 5, py + 2, 14, 20, 11, 11);
    }

    private void drawDukeFront(Graphics graphics, int px, int py, int footHide) {
        drawDukeBase(graphics, px, py, footHide);
        oval(graphics, DUKE_BELLY, px + 8, py + 10, 8, 11);
        oval(graphics, Color.WHITE, px + 7, py + 6, 5, 5);
        graphics.fillOval(px + 12, py + 6, 5, 5);
        oval(graphics, Color.BLACK, px + 9, py + 8, 2, 2);
        graphics.fillOval(px + 14, py + 8, 2, 2);
        graphics.setColor(DUKE_NOSE);
        POLY_X[0] = px + 10; POLY_X[1] = px + 14; POLY_X[2] = px + 12;
        POLY_Y[0] = py + 12; POLY_Y[1] = py + 12; POLY_Y[2] = py + 15;
        graphics.fillPolygon(POLY_X, POLY_Y, 3);
    }

    /** Back view used while walking away: body and flippers with a nape patch, no face. */
    private void drawDukeBack(Graphics graphics, int px, int py, int footHide) {
        drawDukeBase(graphics, px, py, footHide);
        graphics.fillRoundRect(px + 9, py + 5, 6, 8, 5, 5);
    }

    /** Left-facing profile: belly, eye, and beak turned to the side. */
    private void drawDukeLeft(Graphics graphics, int px, int py) {
        graphics.setColor(DARK);
        graphics.fillRoundRect(px + 5, py + 2, 14, 20, 11, 11);
        oval(graphics, DUKE_BELLY, px + 6, py + 10, 8, 11);
        oval(graphics, Color.WHITE, px + 7, py + 6, 5, 5);
        oval(graphics, Color.BLACK, px + 8, py + 8, 2, 2);
        graphics.setColor(DUKE_NOSE);
        POLY_X[0] = px + 4; POLY_X[1] = px + 9; POLY_X[2] = px + 9;
        POLY_Y[0] = py + 11; POLY_Y[1] = py + 9; POLY_Y[2] = py + 13;
        graphics.fillPolygon(POLY_X, POLY_Y, 3);
        graphics.setColor(DARK);
        graphics.fillRoundRect(px + 7, py + 13, 4, 7, 4, 4);
    }

    /** Right-facing profile: the left sprite mirrored about the tile's vertical center. */
    private void drawDukeRight(Graphics graphics, int px, int py) {
        Graphics2D graphics2d = (Graphics2D) graphics;
        graphics2d.translate(2 * px + Game.TILE, 0);
        graphics2d.scale(-1, 1);
        drawDukeLeft(graphics2d, px, py);
        graphics2d.scale(-1, 1);
        graphics2d.translate(-(2 * px + Game.TILE), 0);
    }

    private void drawSword(Graphics graphics, int centerX, int centerY, float progress) {
        Graphics2D graphics2d = (Graphics2D) graphics;
        Stroke previousStroke = graphics2d.getStroke();
        graphics2d.setStroke(SWORD_STROKE);
        double angle = progress * Math.PI * 2;
        drawBlade(graphics2d, centerX, centerY, angle - 0.7, SWORD_TRAIL_2);
        drawBlade(graphics2d, centerX, centerY, angle - 0.35, SWORD_TRAIL_1);
        drawBlade(graphics2d, centerX, centerY, angle, SWORD_COLOR);
        graphics2d.setStroke(previousStroke);
    }

    private void drawBlade(Graphics2D graphics2d, int centerX, int centerY, double angle, Color color) {
        int hiltX = centerX + (int) (Math.cos(angle) * ((double) Game.TILE / 3));
        int hiltY = centerY + (int) (Math.sin(angle) * ((double) Game.TILE / 3));
        int tipX = centerX + (int) (Math.cos(angle) * Game.TILE);
        int tipY = centerY + (int) (Math.sin(angle) * Game.TILE);
        graphics2d.setColor(color);
        graphics2d.drawLine(hiltX, hiltY, tipX, tipY);
    }

    private void drawEnemy(Graphics graphics, int px, int py, int type, float crit, boolean poisoned, float attack) {
        Graphics2D graphics2d = null;
        float scale = 1f;
        int centerX = 0, centerY = 0;
        if (attack > 0f) {
            scale = 1f + attack * 0.25f;
            centerX = px + Game.TILE / 2;
            centerY = py + Game.TILE / 2;
            graphics2d = (Graphics2D) graphics;
            graphics2d.translate(centerX, centerY);
            graphics2d.scale(scale, scale);
            graphics2d.translate(-centerX, -centerY);
        }
        switch (type) {
            case Game.NULLPTR -> {
                graphics.setColor(PURPLE);
                POLY_X[0] = px + 12; POLY_X[1] = px + 20; POLY_X[2] = px + 12; POLY_X[3] = px + 4;
                POLY_Y[0] = py + 3; POLY_Y[1] = py + 12; POLY_Y[2] = py + 21; POLY_Y[3] = py + 12;
                graphics.fillPolygon(POLY_X, POLY_Y, 4);
            }
            case Game.LEAK -> {
                graphics.setColor(LEAK_COLOR);
                graphics.fillRoundRect(px + 3, py + 5, Game.TILE - 6, Game.TILE - 8, 14, 14);
            }
            case Game.FORKBOMB -> {
                rect(graphics, FORK_COLOR, px + 5, py + 8, Game.TILE - 10, Game.TILE - 12);
                graphics.fillRect(px + 9, py + 4, Game.TILE - 18, 5);
            }
            case Game.DEADLOCK -> rect(graphics, DEADLOCK_COLOR, px + 3, py + 4, Game.TILE - 6, Game.TILE - 7);
            case Game.MIMIC -> {
                rect(graphics, BUG_LEG, px + 3, py + 14, Game.TILE - 6, 9);
                rect(graphics, MIMIC_LID, px + 3, py + 4, Game.TILE - 6, 8);
                oval(graphics, MIMIC_EYE, px + 6, py + 5, 4, 4);
                graphics.fillOval(px + 14, py + 5, 4, 4);
            }
            default -> oval(graphics, RED_MAIN, px + 4, py + 6, Game.TILE - 8, Game.TILE - 10);
        }
        if (type != Game.MIMIC) {
            rect(graphics, DARK, px + 8, py + 10, 3, 3);
            graphics.fillRect(px + Game.TILE - 11, py + 10, 3, 3);
        }
        if (poisoned) {
            rect(graphics, POISON_TINT, px + 2, py + 2, Game.TILE - 4, Game.TILE - 4);
        }
        if (crit > 0f) {
            rect(graphics, CRIT_FLASH, px + 1, py + 1, Game.TILE - 2, Game.TILE - 2);
        }
        if (graphics2d != null) {
            graphics2d.translate(centerX, centerY);
            graphics2d.scale(1.0 / scale, 1.0 / scale);
            graphics2d.translate(-centerX, -centerY);
        }
    }

    private boolean bossVisible(Game game) {
        for (int offsetY = 0; offsetY < Game.BOSS_SIZE; offsetY++) {
            for (int offsetX = 0; offsetX < Game.BOSS_SIZE; offsetX++) {
                int x = game.bossX + offsetX;
                int y = game.bossY + offsetY;
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

        oval(graphics, BOSS_SHADOW, px + 8, py + size - 16, size - 16, 14);

        graphics.setColor(enraged ? BOSS_BODY_ENRAGED : BOSS_BODIES[type & 3]);
        graphics.fillRoundRect(px + 6, py + 10, size - 12, size - 20, 30, 30);

        int pulse = (int) (Math.sin(anim / 160.0) * 3) + 3;
        oval(graphics, RED_SOFT, px + size / 2 - 14 - pulse / 2, py + size / 2 - 10 - pulse / 2, 28 + pulse, 24 + pulse);
        oval(graphics, BOSS_CORE_HOT, px + size / 2 - 7, py + size / 2 - 6, 14, 12);

        graphics.setColor(BOSS_EYE);
        int eyeY = py + 24;
        graphics.fillOval(px + 16, eyeY, 9, 9);
        graphics.fillOval(px + size - 25, eyeY, 9, 9);
        graphics.fillOval(px + size / 2 - 4, py + 17, 8, 8);
        oval(graphics, DARK, px + 19, eyeY + 3, 3, 3);
        graphics.fillOval(px + size - 22, eyeY + 3, 3, 3);
        graphics.fillOval(px + size / 2 - 1, py + 20, 3, 3);

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
    private void drawSlamDanger(Graphics graphics, Game game, float telegraph) {
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
                graphics.fillRect(x * Game.TILE, y * Game.TILE, Game.TILE, Game.TILE);
            }
        }
    }

    /** An expanding ring marking the boss slam's reach, brightest at the moment of impact. */
    private void drawShockwave(Graphics graphics, int centerX, int centerY, float progress) {
        Graphics2D graphics2d = (Graphics2D) graphics;
        Stroke previous = graphics2d.getStroke();
        graphics2d.setStroke(SWORD_STROKE);
        int radius = (int) ((1f - progress) * (Game.BOSS_SIZE / 2f + Game.BOSS_SLAM_RADIUS) * Game.TILE);
        graphics2d.setColor(progress > 0.5f ? SHOCKWAVE_BRIGHT : SHOCKWAVE_FAINT);
        graphics2d.drawOval(centerX - radius, centerY - radius, radius * 2, radius * 2);
        graphics2d.setStroke(previous);
    }

    private void drawBossBar(Graphics graphics, Game game) {
        int barWidth = 360;
        int barX = (Game.PLAY_WIDTH - barWidth) / 2;
        int barY = 16;
        rect(graphics, BAR_BACK, barX, barY, barWidth, 16);
        rect(graphics, game.bossEnraged ? BOSS_BAR_FILL_ENRAGED : RED_MAIN, barX, barY, barWidth * Math.max(0, game.bossHp) / game.bossMaxHp, 16);
        graphics.setColor(HUD_TEXT);
        drawCenteredAt(graphics, bossName(game.bossType) + (game.bossEnraged ? "   — ENRAGED" : ""),
                Game.PLAY_WIDTH / 2, barY - 4);
        graphics.setColor(GRAY_LIGHT);
        drawCenteredAt(graphics, "The stairs below stay sealed until it falls", Game.PLAY_WIDTH / 2, barY + 30);
    }

    private static final String[] BOSS_NAME = {"KERNEL PANIC", "STACK OVERFLOW", "SEGFAULT", "HEAP CORRUPTION"};
    private String bossName(int type) { return BOSS_NAME[type & 3]; }

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

        rect(graphics, MINIMAP_BACK, originX - 3, originY - 3, panelWidth + 6, height + 6);
        graphics.setColor(MINIMAP_BORDER);
        graphics.drawRect(originX - 3, originY - 3, panelWidth + 5, height + 5);

        for (int tileY = 0; tileY < Game.MAP_HEIGHT; tileY++) {
            for (int tileX = 0; tileX < Game.MAP_WIDTH; tileX++) {
                int idx = Game.index(tileX, tileY);
                if (!game.explored[idx]) {
                    continue;
                }
                Color color = minimapColor(game.map[idx], game.visible[idx]);
                if (color == null) {
                    continue;
                }
                graphics.setColor(color);
                graphics.fillRect(gridX + tileX * cellW, originY + tileY * cellH, cellW, cellH);
            }
        }

        graphics.setColor(RED_MAIN);
        for (int i = 0; i < game.enemyCount; i++) {
            if (game.visible[Game.index(game.enemyX[i], game.enemyY[i])]) {
                graphics.fillRect(gridX + game.enemyX[i] * cellW, originY + game.enemyY[i] * cellH, cellW, cellH);
            }
        }

        if (game.bossActive && bossVisible(game)) {
            graphics.setColor(RED_MAIN);
            graphics.fillRect(gridX + game.bossX * cellW, originY + game.bossY * cellH,
                    Game.BOSS_SIZE * cellW, Game.BOSS_SIZE * cellH);
        }

        rect(graphics, MINIMAP_PLAYER, gridX + game.playerX * cellW - 1, originY + game.playerY * cellH - 1, cellW + 2, cellH + 2);
    }

    /** Minimap colour for a tile: null hides unseen walls; floors dim when only remembered, not lit. */
    private Color minimapColor(int tile, boolean lit) {
        return switch (tile) {
            case Game.WALL -> null;
            case Game.DOWN_STAIRS -> STAIRS_LIT;
            case Game.UP_STAIRS -> RED_SOFT;
            case Game.LOCKED_DOOR -> MINIMAP_DOOR;
            case Game.PIT -> DARK;
            case Game.SCENERY -> MINIMAP_BORDER;
            default -> lit ? WALL_LIT : FLOOR_LIT_ALT;
        };
    }

    private void drawHud(Graphics graphics, Game game) {
        int top = Game.PLAY_HEIGHT;
        rect(graphics, DARK, 0, top, Game.VIEW_WIDTH, Game.HUD_HEIGHT);

        graphics.setColor(HUD_TEXT);
        graphics.drawString(
                "DEPTH " + game.floor + "   LVL " + game.playerLevel
                + "   ATK " + game.attackPower() + "   DEF " + game.defense()
                + "   GOLD " + game.gold + "   POT " + game.potions + "   KEY " + game.keys, 12, top + 22
        );

        int barX = 430;
        int barWidth = 200;
        int hpY = top + 10;
        rect(graphics, BAR_BACK, barX, hpY, barWidth, 15);
        rect(graphics, RED_MAIN, barX, hpY, barWidth * Math.max(0, game.playerHp) / game.playerMaxHp, 15);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("HP " + game.playerHp + "/" + game.playerMaxHp, barX + 64, hpY + 12);

        int xpY = top + 34;
        rect(graphics, DARK, barX, xpY, barWidth, 7);
        rect(graphics, XP_FILL, barX, xpY, barWidth * Math.min(game.playerXp, game.xpForNext()) / game.xpForNext(), 7);

        graphics.setColor(GRAY_LIGHT);
        graphics.drawString("WASD move   Space attack   Q potion   Tab inventory   E interact   M mute all   T mute music   Stairs descend",
                12, top + 58);
    }

    private void drawDeath(Graphics graphics, Game game) {
        rect(graphics, OVERLAY, 0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "DUKE HAS FALLEN", Game.PLAY_HEIGHT / 2 - 24);
        drawCentered(graphics, "Reached depth " + game.floor, Game.PLAY_HEIGHT / 2 + 4);
        drawCentered(graphics, "Press Space to descend anew", Game.PLAY_HEIGHT / 2 + 32);
    }

    private void drawShop(Graphics graphics, Game game) {
        rect(graphics, OVERLAY, 0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "DUKE FINDS A MERCHANT", Game.PLAY_HEIGHT / 2 - 52);
        drawCentered(graphics, "Gold: " + game.gold + "      Potions: " + game.potions, Game.PLAY_HEIGHT / 2 - 20);
        drawCentered(graphics, "[E]  Buy a potion  -  " + Game.POTION_COST + " gold", Game.PLAY_HEIGHT / 2 + 12);
        drawCentered(graphics, "[Q]  Leave the merchant", Game.PLAY_HEIGHT / 2 + 40);
    }

    private void drawPause(Graphics graphics, Game game) {
        rect(graphics, OVERLAY, 0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "PAUSED", Game.PLAY_HEIGHT / 2 - 40);
        graphics.setColor(game.pauseSelection == 0 ? PROMPT : GRAY_LIGHT);
        drawCentered(graphics, (game.pauseSelection == 0 ? "> " : "  ") + "Resume", Game.PLAY_HEIGHT / 2);
        graphics.setColor(game.pauseSelection == 1 ? PROMPT : GRAY_LIGHT);
        drawCentered(graphics, (game.pauseSelection == 1 ? "> " : "  ") + "Quit", Game.PLAY_HEIGHT / 2 + 30);
        graphics.setColor(GRAY_LIGHT);
        drawCentered(graphics, "W / S to choose      E to confirm      Esc to resume", Game.PLAY_HEIGHT / 2 + 64);
    }

    private void drawInventory(Graphics graphics, Game game) {
        rect(graphics, OVERLAY, 0, 0, Game.VIEW_WIDTH, Game.VIEW_HEIGHT);
        graphics.setColor(HUD_TEXT);
        drawCentered(graphics, "INVENTORY", 48);

        int leftX = 60;
        int rightX = 356;

        graphics.setColor(GRAY_LIGHT);
        graphics.drawString("CHARACTER", leftX, 84);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("HP         " + game.playerHp + " / " + game.playerMaxHp, leftX, 108);
        graphics.drawString("ATK        " + game.attackPower(), leftX, 128);
        graphics.drawString("DEF        " + game.defense(), leftX, 148);
        graphics.drawString("Crit       " + game.critChance() + "%", leftX, 168);
        graphics.drawString("Lifesteal  " + game.lifestealPercent() + "%", leftX, 188);
        graphics.drawString("Dodge      " + game.dodgeChance() + "%", leftX, 208);

        graphics.setColor(GRAY_LIGHT);
        graphics.drawString("EQUIPPED", rightX, 84);
        graphics.setColor(HUD_TEXT);
        graphics.drawString("Weapon   " + slotName(game, game.equippedWeapon), rightX, 108);
        graphics.drawString("Armor    " + slotName(game, game.equippedArmor), rightX, 128);
        graphics.drawString("Trinket  " + slotName(game, game.equippedTrinket), rightX, 148);

        graphics.setColor(GRAY_LIGHT);
        graphics.drawString("CARRIED", leftX, 252);
        if (game.inventoryCount == 0) {
            graphics.setColor(HUD_TEXT);
            graphics.drawString("  (empty)", leftX, 276);
        } else {
            for (int i = 0; i < game.inventoryCount; i++) {
                boolean selected = i == game.inventorySelection;
                graphics.setColor(selected ? PROMPT : HUD_TEXT);
                graphics.drawString((selected ? "> " : "  ") + game.itemName(game.inventory[i]), leftX, 276 + i * 20);
            }
            drawEquipDelta(graphics, game, rightX);
        }

        graphics.setColor(GRAY_LIGHT);
        drawCentered(graphics, "[Up/Down] select    [E] equip    [D] drop    [Q/Esc] close",
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

        graphics.setColor(GRAY_LIGHT);
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
            graphics.setColor(diff > 0 ? DELTA_UP : diff < 0 ? RED_SOFT : HUD_TEXT);
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
            graphics.setColor(RED_SOFT);
            graphics.drawString("lose " + Game.effectLabel(oldEffect), x, lineY);
            lineY += 20;
        }
        graphics.setColor(GRAY_LIGHT);
        graphics.drawString(current >= 0 ? "replaces " + game.itemName(current) : "fills an empty slot", x, lineY);
    }

    private String slotName(Game game, int packed) {
        return packed >= 0 ? game.itemName(packed) : "(none)";
    }

    private void drawMerchant(Graphics graphics, int px, int py) {
        int cx = px + Game.TILE / 2;
        graphics.setColor(MERCHANT_ROBE);
        POLY_X[0] = px + 8; POLY_X[1] = px + Game.TILE - 8; POLY_X[2] = px + Game.TILE - 4; POLY_X[3] = px + 4;
        POLY_Y[0] = py + 13; POLY_Y[1] = py + 13; POLY_Y[2] = py + Game.TILE - 1; POLY_Y[3] = py + Game.TILE - 1;
        graphics.fillPolygon(POLY_X, POLY_Y, 4);
        oval(graphics, MERCHANT_SKIN, cx - 5, py + 4, 10, 9);
        rect(graphics, BROWN, cx - 6, py + 3, 12, 2);
        graphics.fillRect(cx - 4, py, 8, 3);
        rect(graphics, DARK, cx - 3, py + 8, 2, 2);
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
