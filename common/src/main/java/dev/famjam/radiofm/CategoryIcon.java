package dev.famjam.radiofm;

import net.minecraft.util.FastColor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * RU: иконка категории громкости; чат ждёт массив пикселей 16 на 16, не картинку
 * US: the volume category icon; the chat wants a 16 by 16 pixel array, not an image
 */
public final class CategoryIcon {

    private static final String PATH = "/assets/" + RadioFM.MODID + "/textures/gui/category_icon.png";
    private static final int SIZE = 16;

    private CategoryIcon() {
    }

    /** RU: null - чат нарисует свою | US: null lets the chat draw its own */
    public static int[][] load() {
        try (InputStream stream = CategoryIcon.class.getResourceAsStream(PATH)) {
            if (stream == null) {
                return null;
            }
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                return null;
            }
            if (image.getWidth() != SIZE || image.getHeight() != SIZE) {
                RadioFM.LOGGER.warn("Category icon must be {}x{}, got {}x{}",
                        SIZE, SIZE, image.getWidth(), image.getHeight());
                return null;
            }
            return toPixels(image);
        } catch (Exception e) {
            RadioFM.LOGGER.warn("Could not read the category icon", e);
            return null;
        }
    }

    private static int[][] toPixels(BufferedImage image) {
        int[][] pixels = new int[SIZE][SIZE];
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                // RU: первый индекс - столбец, и порядок каналов ABGR, а не ARGB
                // US: the first index is the column, and channels are ABGR, not ARGB
                pixels[x][y] = FastColor.ABGR32.fromArgb32(image.getRGB(x, y));
            }
        }
        return pixels;
    }
}
