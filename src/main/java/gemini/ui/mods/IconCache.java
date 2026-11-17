package gemini.ui.mods;

import com.mojang.blaze3d.platform.NativeImage;
import gemini.mods.Images;
import gemini.mods.ModrinthClient;
import gemini.mods.WebpLossless;
import gemini.util.ClientThread;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Project icons for the browser: fetched from Modrinth's CDN on a worker thread, decoded (lossless WebP with
 * {@link WebpLossless}, PNG/JPEG/GIF with stb_image), scaled down to {@link #SIZE} px and uploaded as a dynamic
 * texture on the client thread. Keeps the most recent {@link #MAX_ICONS} and releases older textures.
 */
final class IconCache {
   static final int SIZE = 32;
   private static final int MAX_ICONS = 96;
   private static final int MAX_BYTES = 1 << 20;
   private static final int MAX_DIMENSION = 2048;
   private static int counter;
   private final ModrinthClient client;
   private final Map<String, Entry> entries = new LinkedHashMap<>(32, 0.75F, true);

   private static final class Entry {
      volatile Identifier id;
      volatile boolean failed;
   }

   IconCache(ModrinthClient client) {
      this.client = client;
   }

   /** The texture for {@code url}, or null while it loads (or when it could not be loaded). Client thread only. */
   Identifier get(String url) {
      if (url == null || url.isBlank()) {
         return null;
      }

      Entry entry = this.entries.get(url);
      if (entry == null) {
         entry = new Entry();
         this.entries.put(url, entry);
         this.load(url, entry);
         this.trim();
      }

      return entry.id;
   }

   private void load(String url, Entry entry) {
      this.client.async(() -> {
         byte[] data = this.client.bytesNow(url, MAX_BYTES);
         int[] pixels = decodeToSquare(data);
         if (pixels == null) {
            entry.failed = true;
            return null;
         }

         ClientThread.run(() -> this.upload(entry, pixels));
         return null;
      }).exceptionally(error -> {
         entry.failed = true;
         return null;
      });
   }

   private void upload(Entry entry, int[] pixels) {
      if (!this.entries.containsValue(entry)) {
         return; // evicted while loading
      }

      NativeImage image = new NativeImage(SIZE, SIZE, false);
      for (int y = 0; y < SIZE; y++) {
         for (int x = 0; x < SIZE; x++) {
            image.setPixel(x, y, pixels[y * SIZE + x]);
         }
      }

      Identifier id = Identifier.fromNamespaceAndPath("gemini", "mod_icon/" + counter++);
      Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Gemini mod icon", image));
      entry.id = id;
   }

   private void trim() {
      Iterator<Map.Entry<String, Entry>> it = this.entries.entrySet().iterator();
      while (this.entries.size() > MAX_ICONS && it.hasNext()) {
         Entry old = it.next().getValue();
         it.remove();
         if (old.id != null) {
            Minecraft.getInstance().getTextureManager().release(old.id);
         }
      }
   }

   /** Frees every texture (the browser was closed for good, or the cache is replaced). */
   void clear() {
      for (Entry entry : this.entries.values()) {
         if (entry.id != null) {
            Minecraft.getInstance().getTextureManager().release(entry.id);
         }
      }

      this.entries.clear();
   }

   /** ARGB pixels scaled to SIZE x SIZE, or null when the image cannot be read. Worker thread. */
   private static int[] decodeToSquare(byte[] data) throws IOException {
      if (WebpLossless.isWebp(data)) {
         WebpLossless.Image image = WebpLossless.decode(data);
         return image == null ? null : Images.fitSquare(image.argb(), image.width(), image.height(), SIZE);
      }

      ByteBuffer buffer = MemoryUtil.memAlloc(data.length);
      try (MemoryStack stack = MemoryStack.stackPush()) {
         buffer.put(data).flip();
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer channels = stack.mallocInt(1);
         if (!STBImage.stbi_info_from_memory(buffer, w, h, channels) || w.get(0) > MAX_DIMENSION || h.get(0) > MAX_DIMENSION) {
            return null;
         }

         ByteBuffer rgba = STBImage.stbi_load_from_memory(buffer, w, h, channels, 4);
         if (rgba == null) {
            return null;
         }

         try {
            int width = w.get(0);
            int height = h.get(0);
            int[] argb = new int[width * height];
            for (int i = 0; i < argb.length; i++) {
               int r = rgba.get(i * 4) & 0xFF;
               int g = rgba.get(i * 4 + 1) & 0xFF;
               int b = rgba.get(i * 4 + 2) & 0xFF;
               int a = rgba.get(i * 4 + 3) & 0xFF;
               argb[i] = a << 24 | r << 16 | g << 8 | b;
            }

            return Images.fitSquare(argb, width, height, SIZE);
         } finally {
            STBImage.stbi_image_free(rgba);
         }
      } finally {
         MemoryUtil.memFree(buffer);
      }
   }
}
