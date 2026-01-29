package caeruleustait.world.preview.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.apache.commons.lang3.StringUtils;

public class WorldPreviewClient {
   public static ShaderInstance HSV_SHADER;

   public static void renderTexture(AbstractTexture texture, double xMin, double yMin, double xMax, double yMax) {
      Tesselator tesselator = Tesselator.getInstance();
      BufferBuilder bufferBuilder = tesselator.begin(Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
      RenderSystem.setShader(GameRenderer::getPositionTexShader);
      RenderSystem.setShaderTexture(0, texture.getId());
      RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
      bufferBuilder.addVertex((float)xMin, (float)yMax, 0.0F).setUv(0.0F, 1.0F);
      bufferBuilder.addVertex((float)xMax, (float)yMax, 0.0F).setUv(1.0F, 1.0F);
      bufferBuilder.addVertex((float)xMax, (float)yMin, 0.0F).setUv(1.0F, 0.0F);
      bufferBuilder.addVertex((float)xMin, (float)yMin, 0.0F).setUv(0.0F, 0.0F);
      MeshData data = bufferBuilder.buildOrThrow();

      try {
         BufferUploader.drawWithShader(data);
      } catch (Throwable var15) {
         if (data != null) {
            try {
               data.close();
            } catch (Throwable var14) {
               var15.addSuppressed(var14);
            }
         }

         throw var15;
      }

      if (data != null) {
         data.close();
      }
   }

   public static String toTitleCase(String input) {
      return input != null && !input.isBlank()
         ? Arrays.stream(input.split(" ")).<CharSequence>map(StringUtils::capitalize).collect(Collectors.joining(" "))
         : input;
   }

   @EventBusSubscriber(value = Dist.CLIENT, modid = "world_preview_tfc")
   public static class ModClientEvents {
      @SubscribeEvent
      public static void shaderRegistry(RegisterShadersEvent event) throws IOException {
         event.registerShader(
            new ShaderInstance(event.getResourceProvider(), ResourceLocation.parse("world_preview:hsv"), DefaultVertexFormat.NEW_ENTITY),
            shaderInstance -> WorldPreviewClient.HSV_SHADER = shaderInstance
         );
      }
   }
}
