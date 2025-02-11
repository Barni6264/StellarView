package net.povstalec.stellarview.api.common.space_objects.resourcepack;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.api.common.space_objects.StarLike;
import net.povstalec.stellarview.api.common.space_objects.SupernovaLeftover;
import org.joml.Matrix4f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.util.*;

public class Star extends StarLike
{
	@Nullable
	private SupernovaInfo supernovaInfo;
	
	public static final Codec<Star> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(Star::getParentLocation),
			Codec.either(SpaceCoords.CODEC, StellarCoordinates.Equatorial.CODEC).fieldOf("coords").forGetter(object -> Either.left(object.getCoords())),
			AxisRotation.CODEC.fieldOf("axis_rotation").forGetter(Star::getAxisRotation),
			OrbitInfo.CODEC.optionalFieldOf("orbit_info").forGetter(star -> Optional.ofNullable(star.orbitInfo())),
			TextureLayer.CODEC.listOf().fieldOf("texture_layers").forGetter(Star::getTextureLayers),
			
			FadeOutHandler.CODEC.optionalFieldOf("fade_out_handler", FadeOutHandler.DEFAULT_STAR_HANDLER).forGetter(Star::getFadeOutHandler),
			
			Codec.floatRange(0, Float.MAX_VALUE).optionalFieldOf("min_star_size", MIN_SIZE).forGetter(Star::getMinStarSize),
			Codec.floatRange(0, Color.MAX_FLOAT_VALUE).optionalFieldOf("max_star_alpha", MAX_ALPHA).forGetter(Star::getMaxStarAlpha),
			Codec.floatRange(0, Color.MAX_FLOAT_VALUE).optionalFieldOf("min_star_alpha", MIN_ALPHA).forGetter(Star::getMinStarAlpha),
			
			SupernovaInfo.CODEC.optionalFieldOf("supernova_info").forGetter(star -> Optional.ofNullable(star.supernovaInfo()))
			).apply(instance, Star::new));
	
	public Star() {}
	
	public Star(Optional<ResourceLocation> parent, Either<SpaceCoords, StellarCoordinates.Equatorial> coords, AxisRotation axisRotation,
			Optional<OrbitInfo> orbitInfo, List<TextureLayer> textureLayers, FadeOutHandler fadeOutHandler,
			float minStarSize, float maxStarAlpha, float minStarAlpha,
			Optional<SupernovaInfo> supernovaInfo)
	{
		super(parent, coords, axisRotation, orbitInfo, textureLayers, fadeOutHandler, minStarSize, maxStarAlpha, minStarAlpha);
		
		if(supernovaInfo.isPresent())
			this.supernovaInfo = supernovaInfo.get();
		else
			this.supernovaInfo = null;
	}
	
	public boolean isSupernova()
	{
		return this.supernovaInfo != null;
	}
	
	@Nullable
	public SupernovaInfo supernovaInfo()
	{
		return supernovaInfo;
	}
	
	public float supernovaSize(float size, long ticks, double lyDistance)
	{
		if(supernovaInfo.supernovaEnded(ticks))
			return 0;
		
		if(!supernovaInfo.supernovaStarted(ticks))
			return size;

		long lifetime = supernovaInfo.lifetime(ticks);
		float sizeMultiplier = supernovaInfo.getMaxSizeMultiplier() * (float) Math.sin(Math.PI * lifetime / supernovaInfo.getDurationTicks());
		
		return sizeMultiplier > 1 || (float) lifetime > supernovaInfo.getDurationTicks() / 2 ? sizeMultiplier * size : size;
	}
	
	public float rotation(long ticks)
	{
		if(!isSupernova() || !supernovaInfo.supernovaStarted(ticks))
			return 0;
		
		return (float) (Math.PI * supernovaInfo.lifetime(ticks) / supernovaInfo.getDurationTicks());
	}
	
	
	public Color.FloatRGBA supernovaRGBA(long ticks, double lyDistance)
	{
		Color.FloatRGBA starRGBA = super.starRGBA(lyDistance);
		
		if(!isSupernova() || supernovaInfo.supernovaEnded(ticks) || !supernovaInfo.supernovaStarted(ticks))
			return starRGBA;
		
		float alphaDif = Color.MAX_FLOAT_VALUE - starRGBA.alpha(); // Difference between current star alpha and max alpha
		
		float alpha = starRGBA.alpha() + alphaDif * (float) Math.sin(Math.PI * supernovaInfo.lifetime(ticks) / supernovaInfo.getDurationTicks());
		starRGBA.setAlpha(alpha <= Color.MIN_FLOAT_VALUE ? Color.MIN_FLOAT_VALUE : alpha >= Color.MAX_FLOAT_VALUE ? Color.MAX_FLOAT_VALUE : alpha);
		
		return starRGBA;
	}
	
	//============================================================================================
	//*************************************Saving and Loading*************************************
	//============================================================================================
	
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider)
	{
		CompoundTag tag = super.serializeNBT(provider);
		//TODO Serialize SupernovaInfo
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag)
	{
		super.deserializeNBT(provider, tag);
		//TODO Deserialize SupernovaInfo
		supernovaInfo = null;
	}
	
	
	
	public static class SupernovaInfo
	{
		protected Nebula nebula; //TODO Leave a Nebula where there used to be a Supernova
		protected SupernovaLeftover supernovaLeftover; // Whatever is left after Supernova dies
		
		protected float maxSizeMultiplier;
		protected long startTicks;
		protected long durationTicks;
		
		protected long endTicks;
		
		public static final Codec<SupernovaInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.FLOAT.fieldOf("max_size_multiplier").forGetter(SupernovaInfo::getMaxSizeMultiplier),
				Codec.LONG.fieldOf("start_ticks").forGetter(SupernovaInfo::getStartTicks),
				Codec.LONG.fieldOf("duration_ticks").forGetter(SupernovaInfo::getDurationTicks),
				
				Nebula.CODEC.fieldOf("nebula").forGetter(SupernovaInfo::getNebula),
				SupernovaLeftover.CODEC.fieldOf("supernova_leftover").forGetter(SupernovaInfo::getSupernovaLeftover)
				).apply(instance, SupernovaInfo::new));
		
		public SupernovaInfo(float maxSizeMultiplier, long startTicks, long durationTicks, Nebula nebula, SupernovaLeftover supernovaLeftover)
		{
			this.maxSizeMultiplier = maxSizeMultiplier;
			this.startTicks = startTicks;
			this.durationTicks = durationTicks;
			
			this.endTicks = startTicks + durationTicks;
			
			this.nebula = nebula;
			this.supernovaLeftover = supernovaLeftover;
		}
		
		public Nebula getNebula()
		{
			return nebula;
		}
		
		public SupernovaLeftover getSupernovaLeftover()
		{
			return supernovaLeftover;
		}
		
		public float getMaxSizeMultiplier()
		{
			return maxSizeMultiplier;
		}
		
		public long getStartTicks()
		{
			return startTicks;
		}
		
		public long getDurationTicks()
		{
			return durationTicks;
		}
		
		public long getEndTicks()
		{
			return endTicks;
		}
		
		
		
		public boolean supernovaStarted(long ticks)
		{
			return ticks > getStartTicks();
		}
		
		public boolean supernovaEnded(long ticks)
		{
			return ticks > getEndTicks();
		}
		
		public long lifetime(long ticks)
		{
			return ticks - getStartTicks();
		}
		
		//TODO
		/*public static SupernovaInfo fromTag(CompoundTag tag)
		{
		
		}*/
	}
}
