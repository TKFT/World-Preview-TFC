package caeruleustait.world.preview.backend.color;

import java.security.InvalidParameterException;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

public class ColorMap {
   private final ResourceLocation key;
   private final String name;
   private final Color[] colors;

   public ColorMap(ResourceLocation key, String name, Color[] colors) {
      this.key = key;
      this.name = name;
      this.colors = colors;
   }

   public ResourceLocation key() {
      return this.key;
   }

   public String name() {
      return this.name;
   }

   public ColorMap(ResourceLocation key, RawColorMap raw) {
      this.key = key;
      this.name = raw.name;
      if (raw.data.size() < 2) {
         throw new InvalidParameterException(this.name + ": All colormaps MUST have at least 2 entries");
      } else {
         this.colors = new Color[raw.data.size()];

         for (int i = 0; i < raw.data.size(); i++) {
            List<Float> entry = raw.data.get(i);
            if (entry.size() != 3) {
               throw new InvalidParameterException(this.name + ": All entries in a colormap data MUST have exactly 3 elements: [R, G, B]!");
            }

            if (entry.stream().anyMatch(x -> x < 0.0 || x > 1.0)) {
               throw new InvalidParameterException(this.name + ": All values in a colormap data MUST be between 0.0 and 1.0 (inclusive)");
            }

            this.colors[i] = new Color(entry.get(0), entry.get(1), entry.get(2));
         }
      }
   }

   public Color get(float position) {
      if (position < 0.0F) {
         return this.colors[0];
      } else if (position > 1.0F) {
         return this.colors[this.colors.length - 1];
      } else {
         float pos = position * (this.colors.length - 1);
         int floor = (int)pos;
         if (pos == floor) {
            return this.colors[floor];
         } else {
            return pos == floor + 1 ? this.colors[floor + 1] : lerp(this.colors[floor], this.colors[floor + 1], pos - floor);
         }
      }
   }

   public int getARGB(float position) {
      Color c = this.get(position);
      int R = (int)(c.r * 255.0F) & 0xFF;
      int G = (int)(c.g * 255.0F) & 0xFF;
      int B = (int)(c.b * 255.0F) & 0xFF;
      return R | G << 8 | B << 16 | 0xFF000000;
   }

   public int[] bake(int yMin, int yMax, int yVisMin, int yVisMax) {
      int[] res = new int[yMax - yMin];
      float visRange = (float)yVisMax - yVisMin;

      for (int i = yMin; i < yMax; i++) {
         res[i - yMin] = this.getARGB((i - yVisMin) / visRange);
      }

      return res;
   }

   public int[] bake(int numValues) {
      int[] res = new int[numValues];

      for (int i = 0; i < numValues; i++) {
         res[i] = this.getARGB((float)i / numValues);
      }

      return res;
   }

   static float[] RGBToXYZ(float[] out, float r, float g, float b) {
      float R = (r > 0.04045 ? (float)Math.pow((r + 0.055F) / 1.055F, 2.4F) : r / 12.92F) * 100.0F;
      float G = (g > 0.04045 ? (float)Math.pow((g + 0.055F) / 1.055F, 2.4F) : g / 12.92F) * 100.0F;
      float B = (b > 0.04045 ? (float)Math.pow((b + 0.055F) / 1.055F, 2.4F) : b / 12.92F) * 100.0F;
      out[0] = R * 0.4124564F + G * 0.3575761F + B * 0.1804375F;
      out[1] = R * 0.2126729F + G * 0.7151522F + B * 0.072175F;
      out[2] = R * 0.0193339F + G * 0.119192F + B * 0.9503041F;
      return out;
   }

   static float[] XYZToLab(float[] out, float x, float y, float z) {
      float a = x / 95.047F;
      float b = y * 0.01F;
      float c = z / 108.883F;
      float j = 0.13793103F;
      a = a > 0.008856 ? (float)Math.cbrt(a) : 7.787F * a + 0.13793103F;
      b = b > 0.008856 ? (float)Math.cbrt(b) : 7.787F * b + 0.13793103F;
      c = c > 0.008856 ? (float)Math.cbrt(c) : 7.787F * c + 0.13793103F;
      out[0] = 116.0F * b - 16.0F;
      out[1] = 500.0F * (a - b);
      out[2] = 200.0F * (b - c);
      return out;
   }

   static float[] LabToXYZ(float[] out, float L, float a, float b) {
      out[1] = (L + 16.0F) / 116.0F;
      out[0] = a / 500.0F + out[1];
      out[2] = out[1] - b / 200.0F;

      for (int i = 0; i < 3; i++) {
         if (out[i] > 0.20689304F) {
            out[i] *= out[i] * out[i];
         } else {
            out[i] = (out[i] - 0.13793103F) / 7.787F;
         }
      }

      out[0] *= 95.047F;
      out[1] *= 100.0F;
      out[2] *= 108.883F;
      return out;
   }

   static float[] XYZToRGB(float[] out, float x, float y, float z) {
      float X = x / 100.0F;
      float Y = y / 100.0F;
      float Z = z / 100.0F;
      out[0] = X * 3.2404542F + Y * -1.5371385F + Z * -0.4985314F;
      out[1] = X * -0.969266F + Y * 1.8760108F + Z * 0.041556F;
      out[2] = X * 0.0556434F + Y * -0.2040259F + Z * 1.0572252F;

      for (int i = 0; i < 3; i++) {
         if (out[i] > 0.0031308) {
            out[i] = 1.055F * (float)Math.pow(out[i], 0.4166666666666667) - 0.055F;
         } else {
            out[i] = 12.92F * out[i];
         }
      }

      return out;
   }

   public static float[] LabToRGB(float L, float a, float b) {
      float[] out = new float[3];
      return XYZToRGB(LabToXYZ(out, L, a, b), out[0], out[1], out[2]);
   }

   public static float[] RGBToLab(float r, float g, float b) {
      float[] out = new float[3];
      return XYZToLab(RGBToXYZ(out, r, g, b), out[0], out[1], out[2]);
   }

   public static float clamp(float val, float min, float max) {
      return Math.min(Math.max(val, min), max);
   }

   public static float lerp(float low, float high, float amt) {
      amt = clamp(amt, 0.0F, 1.0F);
      return low * amt + high * (1.0F - amt);
   }

   public static Color lerp(Color lower, Color upper, float amount) {
      float[] lowerLab = RGBToLab(lower.r, lower.g, lower.b);
      float[] upperLab = RGBToLab(upper.r, upper.g, upper.b);
      float[] rgb = LabToRGB(lerp(upperLab[0], lowerLab[0], amount), lerp(upperLab[1], lowerLab[1], amount), lerp(upperLab[2], lowerLab[2], amount));
      return new Color(clamp(rgb[0], 0.0F, 1.0F), clamp(rgb[1], 0.0F, 1.0F), clamp(rgb[2], 0.0F, 1.0F));
   }

   public record Color(float r, float g, float b) {
   }

   public record RawColorMap(String name, List<List<Float>> data) {
   }
}
