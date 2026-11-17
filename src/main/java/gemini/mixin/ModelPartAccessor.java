package gemini.mixin;

import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Chams walk a posed model part tree the way {@code ModelPart.render} does, honouring hidden parts. */
@Mixin({ModelPart.class})
public interface ModelPartAccessor {
   @Accessor("cubes")
   List<ModelPart.Cube> geminiCubes();

   @Accessor("children")
   Map<String, ModelPart> geminiChildren();
}
